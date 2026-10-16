package com.teamops.module.web;

import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.UrlPathHelper;

import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.module.service.ModuleAccessService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Enforces the module switch and permissions on every endpoint of every department tool: a request under
 * {@code /api/modules/{key}/} reaches its controller only when the user may use that module, with the view permission
 * for reads (GET, HEAD) and the edit permission as well for everything else. A module's controllers still put
 * {@code @PreAuthorize} on their endpoints; this check is what a forgotten annotation cannot undo.
 * <p>
 * Fails closed: without a {@link ModuleAccessService} (a test slice that does not provide one) every module request
 * is refused.
 */
public class ModuleAccessInterceptor implements HandlerInterceptor {

	public static final String PATH_PREFIX = "/api/modules/";

	private static final Set<String> READ_METHODS = Set.of("GET", "HEAD");

	private final ObjectProvider<ModuleAccessService> moduleAccess;

	public ModuleAccessInterceptor(ObjectProvider<ModuleAccessService> moduleAccess) {
		this.moduleAccess = moduleAccess;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (CorsUtils.isPreFlightRequest(request)) {
			return true;
		}
		AuthenticatedUser user = currentUser();
		String key = moduleKey(UrlPathHelper.defaultInstance.getPathWithinApplication(request));
		boolean edit = !READ_METHODS.contains(request.getMethod());
		moduleAccess.getObject().requireAccess(user, key, edit);
		return true;
	}

	/** "/api/modules/bench/allocations/7" → "bench"; "" when the path names no module. */
	static String moduleKey(String path) {
		if (path == null || !path.startsWith(PATH_PREFIX)) {
			return "";
		}
		String rest = path.substring(PATH_PREFIX.length());
		int slash = rest.indexOf('/');
		return slash < 0 ? rest : rest.substring(0, slash);
	}

	private static AuthenticatedUser currentUser() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
			return user;
		}
		throw ApiException.unauthorized("UNAUTHORIZED", "Sign in to continue");
	}

}
