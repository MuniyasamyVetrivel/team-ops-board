package com.teamops.common.audit;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;

/**
 * Who is acting on the current thread, for audit stamps and record history: the signed-in user of the current
 * request, or nobody (null) for the system — background jobs, startup tasks and migrations.
 */
public final class AuditActor {

	private AuditActor() {
	}

	public static Long currentUserId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
			return user.id();
		}
		return null;
	}

	/** The current request's origin, or null outside a request. */
	public static ClientInfo currentClient() {
		RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
		if (attributes instanceof ServletRequestAttributes servlet) {
			return ClientInfo.from(servlet.getRequest());
		}
		return null;
	}

}
