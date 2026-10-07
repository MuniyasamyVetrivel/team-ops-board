package com.teamops.support;

import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.security.JwtTokenService;
import com.teamops.common.security.UserPrincipalService;

/** Test principals and bearer tokens for {@link SecuritySliceTest}s. */
public final class SliceAuth {

	/** Every permission seeded by V2, as the backend resolves them for SUPER_ADMIN. */
	public static final Set<String> ALL_PERMISSIONS = Set.of("DASHBOARD_VIEW", "TASK_VIEW", "TASK_CREATE",
			"TASK_EDIT", "TASK_ASSIGN", "TASK_DELETE", "WORKLOAD_VIEW", "PROJECT_VIEW", "PROJECT_EDIT", "TICKET_VIEW",
			"TICKET_CREATE", "TICKET_EDIT", "TICKET_ASSIGN", "SLA_MANAGE", "APPROVAL_VIEW", "APPROVAL_DECIDE",
			"APPROVAL_CONFIGURE", "ANNOUNCEMENT_MANAGE", "KB_VIEW", "KB_EDIT", "DOCUMENT_VIEW", "DOCUMENT_EDIT",
			"TEAM_VIEW", "CALENDAR_VIEW", "CALENDAR_EDIT", "REPORT_VIEW", "REPORT_EXPORT", "USER_MANAGE",
			"DEPARTMENT_MANAGE", "SETTINGS_MANAGE", "PERMISSION_MANAGE", "AUDIT_VIEW", "MARKETING_VIEW",
			"MARKETING_EDIT", "SEO_VIEW", "SEO_EDIT", "CAMPAIGN_VIEW", "CAMPAIGN_EDIT", "TARGET_VIEW", "TARGET_EDIT",
			"LEAD_VIEW", "LEAD_EDIT", "BACKLINK_VIEW", "BACKLINK_EDIT", "CONTENT_VIEW", "CONTENT_EDIT");

	/** EMPLOYEE role permissions from V2. */
	public static final Set<String> EMPLOYEE_PERMISSIONS = Set.of("DASHBOARD_VIEW", "TASK_VIEW", "TASK_CREATE",
			"TASK_EDIT", "TICKET_VIEW", "TICKET_CREATE", "PROJECT_VIEW", "APPROVAL_VIEW", "KB_VIEW", "DOCUMENT_VIEW",
			"TEAM_VIEW", "CALENDAR_VIEW");

	public static final AuthenticatedUser SUPER_ADMIN = new AuthenticatedUser(1L, "rakesh@teamops.local", "Rakesh",
			1L, Set.of("SUPER_ADMIN"), ALL_PERMISSIONS);

	public static final AuthenticatedUser EMPLOYEE = new AuthenticatedUser(4L, "karthik.raj@teamops.local",
			"Karthik Raj", 5L, Set.of("EMPLOYEE"), EMPLOYEE_PERMISSIONS);

	/** Can manage users but not permissions. */
	public static final AuthenticatedUser USER_ADMIN = new AuthenticatedUser(6L, "hr.admin@teamops.local",
			"HR Admin", 3L, Set.of("EMPLOYEE"), with(EMPLOYEE_PERMISSIONS, "USER_MANAGE"));

	/** No permissions at all. */
	public static final AuthenticatedUser NOBODY = new AuthenticatedUser(9L, "nobody@teamops.local", "Nobody", 5L,
			Set.of("EMPLOYEE"), Set.of());

	private SliceAuth() {
	}

	/** Stubs the principal lookup and returns an {@code Authorization} header value. */
	public static String bearer(JwtTokenService jwtTokenService, UserPrincipalService principalService,
			AuthenticatedUser user) {
		when(principalService.findActiveUser(user.id())).thenReturn(Optional.of(user));
		return "Bearer " + jwtTokenService.issue(user).value();
	}

	public static Set<String> with(Set<String> base, String... extra) {
		return Stream.concat(base.stream(), Stream.of(extra)).collect(Collectors.toUnmodifiableSet());
	}

}
