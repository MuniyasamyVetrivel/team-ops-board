package com.teamops.user.service;

import java.util.Set;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.teamops.user.entity.RoleCodes;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Production bootstrap: when the users table is empty and BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD are set,
 * creates the first Super Admin. Does nothing once any user exists.
 */
@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class BootstrapAdminInitializer implements ApplicationRunner {

	static final int MIN_ADMIN_PASSWORD_LENGTH = 12;

	private final BootstrapAdminProperties properties;

	private final UserRepository userRepository;

	private final UserProvisioningService userProvisioningService;

	@Override
	public void run(ApplicationArguments args) {
		if (!StringUtils.hasText(properties.email()) || !StringUtils.hasText(properties.password())) {
			return;
		}
		if (userRepository.count() > 0) {
			log.info("Bootstrap admin skipped: users already exist. You can remove BOOTSTRAP_ADMIN_* settings.");
			return;
		}
		if (properties.password().length() < MIN_ADMIN_PASSWORD_LENGTH) {
			throw new IllegalStateException(
					"BOOTSTRAP_ADMIN_PASSWORD must be at least " + MIN_ADMIN_PASSWORD_LENGTH + " characters");
		}
		String firstName = StringUtils.hasText(properties.firstName()) ? properties.firstName() : "Admin";
		String departmentCode = StringUtils.hasText(properties.departmentCode()) ? properties.departmentCode() : "IT";
		userProvisioningService.createUser(new NewUser(properties.email(), properties.password(), firstName, "",
				"Reporting Manager", departmentCode, Set.of(RoleCodes.SUPER_ADMIN), Set.of()), null);
		log.info("Bootstrap Super Admin created: {}", properties.email());
	}

}
