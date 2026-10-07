package com.teamops.devdata;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.teamops.user.entity.RoleCodes;
import com.teamops.user.repository.UserRepository;
import com.teamops.user.service.NewUser;
import com.teamops.user.service.UserProvisioningService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Development seed data, enabled with DEV_SEED_ENABLED=true. Idempotent: existing users are left untouched. All seeded
 * users share the password in DEV_SEED_PASSWORD. Later phases extend this with tasks, tickets, marketing data, etc.
 */
@Slf4j
@Component
@Order(20)
@ConditionalOnBooleanProperty(name = "app.dev-seed.enabled")
@RequiredArgsConstructor
public class DevDataSeeder implements ApplicationRunner {

	static final Set<String> ALL_MARKETING_PERMISSIONS = Set.of("MARKETING_VIEW", "MARKETING_EDIT", "SEO_VIEW",
			"SEO_EDIT", "CAMPAIGN_VIEW", "CAMPAIGN_EDIT", "TARGET_VIEW", "TARGET_EDIT", "LEAD_VIEW", "LEAD_EDIT",
			"BACKLINK_VIEW", "BACKLINK_EDIT", "CONTENT_VIEW", "CONTENT_EDIT");

	private static final String RAKESH = "rakesh@teamops.local";

	private static final String DM_MANAGER = "priya.menon@teamops.local";

	private static final String SEO_EXECUTIVE = "arun.kumar@teamops.local";

	private static final String WEB_DEVELOPER = "karthik.raj@teamops.local";

	private final DevSeedProperties properties;

	private final UserRepository userRepository;

	private final UserProvisioningService userProvisioningService;

	@Override
	public void run(ApplicationArguments args) {
		if (properties.password() == null || properties.password().length() < 8) {
			log.warn("DEV_SEED_ENABLED=true but DEV_SEED_PASSWORD is missing or shorter than 8 characters; "
					+ "skipping development seed data.");
			return;
		}
		log.warn("Seeding DEVELOPMENT data (DEV_SEED_ENABLED=true). Never enable this in production.");

		List<NewUser> users = List.of(
				new NewUser(RAKESH, properties.password(), "Rakesh", "", "Reporting Manager", "IT",
						Set.of(RoleCodes.SUPER_ADMIN), Set.of()),
				new NewUser(DM_MANAGER, properties.password(), "Priya", "Menon", "Digital Marketing Manager", "DM",
						Set.of(RoleCodes.DEPARTMENT_MANAGER), ALL_MARKETING_PERMISSIONS),
				new NewUser(SEO_EXECUTIVE, properties.password(), "Arun", "Kumar", "SEO Executive", "DM",
						Set.of(RoleCodes.EMPLOYEE), Set.of("MARKETING_VIEW", "SEO_VIEW", "SEO_EDIT", "TARGET_VIEW",
								"BACKLINK_VIEW", "BACKLINK_EDIT", "CONTENT_VIEW")),
				new NewUser(WEB_DEVELOPER, properties.password(), "Karthik", "Raj", "Frontend Developer", "WEBDEV",
						Set.of(RoleCodes.EMPLOYEE), Set.of()));

		Map<String, Long> created = new HashMap<>();
		for (NewUser user : users) {
			if (!userRepository.existsByEmailIgnoreCase(user.email())) {
				created.put(user.email(), userProvisioningService.createUser(user, null).getId());
				log.info("Seeded user {}", user.email());
			}
		}
		if (created.containsKey(DM_MANAGER)) {
			userProvisioningService.assignDepartmentManager("DM", created.get(DM_MANAGER));
		}
		linkReportsTo(created, DM_MANAGER, RAKESH);
		linkReportsTo(created, WEB_DEVELOPER, RAKESH);
		linkReportsTo(created, SEO_EXECUTIVE, DM_MANAGER);
	}

	private void linkReportsTo(Map<String, Long> created, String email, String managerEmail) {
		if (!created.containsKey(email)) {
			return;
		}
		userRepository.findByEmailIgnoreCase(managerEmail)
			.ifPresent(manager -> userProvisioningService.setReportsTo(created.get(email), manager.getId()));
	}

}
