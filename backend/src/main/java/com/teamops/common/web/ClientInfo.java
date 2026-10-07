package com.teamops.common.web;

import jakarta.servlet.http.HttpServletRequest;

/** Request origin details recorded with security-relevant events. */
public record ClientInfo(String ipAddress, String userAgent) {

	private static final int MAX_USER_AGENT_LENGTH = 512;

	public static ClientInfo from(HttpServletRequest request) {
		String userAgent = request.getHeader("User-Agent");
		if (userAgent != null && userAgent.length() > MAX_USER_AGENT_LENGTH) {
			userAgent = userAgent.substring(0, MAX_USER_AGENT_LENGTH);
		}
		return new ClientInfo(request.getRemoteAddr(), userAgent);
	}

	public static ClientInfo unknown() {
		return new ClientInfo(null, null);
	}

}
