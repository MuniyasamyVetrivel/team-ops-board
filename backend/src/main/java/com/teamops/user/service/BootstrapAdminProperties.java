package com.teamops.user.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code app.bootstrap-admin.*}: creates the first Super Admin on an empty database. */
@ConfigurationProperties(prefix = "app.bootstrap-admin")
public record BootstrapAdminProperties(String email, String password, String firstName, String departmentCode) {

	@Override
	public String toString() {
		return "BootstrapAdminProperties[email=" + email + ", departmentCode=" + departmentCode + "]";
	}

}
