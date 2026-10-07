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

import com.teamops.common.web.ClientInfo;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.department.service.DepartmentService;
import com.teamops.user.dto.CreateUserRequest;
import com.teamops.user.entity.RoleCodes;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;
import com.teamops.user.service.UserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Development seed data, enabled with DEV_SEED_ENABLED=true. Idempotent: existing users are left untouched, and a
 * department's manager is only set if it has none. All seeded users share the password in DEV_SEED_PASSWORD. Later
 * phases extend this with tasks, tickets, marketing data, etc.
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

	private static final String MANAGER = RoleCodes.DEPARTMENT_MANAGER;

	private static final String EMPLOYEE = RoleCodes.EMPLOYEE;

	/**
	 * Ordered so that every reports-to target is created before the people reporting to them. {@code leadsDepartment}
	 * marks the user set as the department's manager.
	 */
	static final List<SeedUser> USERS = List.of(
			new SeedUser(RAKESH, "Rakesh", "", "Reporting Manager", "IT", RoleCodes.SUPER_ADMIN, Set.of(), null, false),
			// Department managers report to Rakesh.
			new SeedUser("suresh.babu@teamops.local", "Suresh", "Babu", "IT Manager", "IT", MANAGER, Set.of(), RAKESH, true),
			new SeedUser("anitha.raj@teamops.local", "Anitha", "Raj", "Security Lead", "CYBERSEC", MANAGER, Set.of(), RAKESH, true),
			new SeedUser("lakshmi.priya@teamops.local", "Lakshmi", "Priya", "HR Manager", "HR", MANAGER, Set.of(), RAKESH, true),
			new SeedUser("ramesh.kannan@teamops.local", "Ramesh", "Kannan", "Talent Acquisition Lead", "TA", MANAGER, Set.of(), RAKESH, true),
			new SeedUser("sanjay.varma@teamops.local", "Sanjay", "Varma", "Web Development Lead", "WEBDEV", MANAGER, Set.of(), RAKESH, true),
			new SeedUser("harish.prabhu@teamops.local", "Harish", "Prabhu", "Mobile Development Lead", "APPDEV", MANAGER, Set.of(), RAKESH, true),
			new SeedUser("priya.menon@teamops.local", "Priya", "Menon", "Digital Marketing Manager", "DM", MANAGER, ALL_MARKETING_PERMISSIONS, RAKESH, true),
			new SeedUser("gokul.ravi@teamops.local", "Gokul", "Ravi", "Pre-Sales Head", "PRESALES", MANAGER, Set.of(), RAKESH, true),
			new SeedUser("ajay.dev@teamops.local", "Ajay", "Dev", "Creative Lead", "GRAPHICS", MANAGER, Set.of(), RAKESH, true),
			new SeedUser("revathi.sundar@teamops.local", "Revathi", "Sundar", "Payroll Manager", "PAYROLL", MANAGER, Set.of(), RAKESH, true),
			// Employees report to their department manager.
			new SeedUser("vignesh.raman@teamops.local", "Vignesh", "Raman", "System Administrator", "IT", EMPLOYEE, Set.of(), "suresh.babu@teamops.local", false),
			new SeedUser("deepak.nair@teamops.local", "Deepak", "Nair", "SOC Analyst", "CYBERSEC", EMPLOYEE, Set.of(), "anitha.raj@teamops.local", false),
			new SeedUser("meena.sekar@teamops.local", "Meena", "Sekar", "HR Executive", "HR", EMPLOYEE, Set.of(), "lakshmi.priya@teamops.local", false),
			new SeedUser("divya.mohan@teamops.local", "Divya", "Mohan", "Recruiter", "TA", EMPLOYEE, Set.of(), "ramesh.kannan@teamops.local", false),
			new SeedUser("karthik.raj@teamops.local", "Karthik", "Raj", "Frontend Developer", "WEBDEV", EMPLOYEE, Set.of(), "sanjay.varma@teamops.local", false),
			new SeedUser("nithya.ganesh@teamops.local", "Nithya", "Ganesh", "Flutter Developer", "APPDEV", EMPLOYEE, Set.of(), "harish.prabhu@teamops.local", false),
			new SeedUser("arun.kumar@teamops.local", "Arun", "Kumar", "SEO Executive", "DM", EMPLOYEE,
					Set.of("MARKETING_VIEW", "SEO_VIEW", "SEO_EDIT", "TARGET_VIEW", "BACKLINK_VIEW", "BACKLINK_EDIT", "CONTENT_VIEW"),
					"priya.menon@teamops.local", false),
			new SeedUser("kavya.suresh@teamops.local", "Kavya", "Suresh", "Content Writer", "DM", EMPLOYEE,
					Set.of("MARKETING_VIEW", "CONTENT_VIEW", "CONTENT_EDIT", "SEO_VIEW", "TARGET_VIEW"),
					"priya.menon@teamops.local", false),
			new SeedUser("swetha.bala@teamops.local", "Swetha", "Bala", "Solutions Consultant", "PRESALES", EMPLOYEE, Set.of(), "gokul.ravi@teamops.local", false),
			new SeedUser("pooja.lakshman@teamops.local", "Pooja", "Lakshman", "Graphic Designer", "GRAPHICS", EMPLOYEE, Set.of(), "ajay.dev@teamops.local", false),
			new SeedUser("manoj.thomas@teamops.local", "Manoj", "Thomas", "Payroll Executive", "PAYROLL", EMPLOYEE, Set.of(), "revathi.sundar@teamops.local", false));

	private final DevSeedProperties properties;

	private final UserRepository userRepository;

	private final DepartmentRepository departmentRepository;

	private final UserService userService;

	private final DepartmentService departmentService;

	@Override
	public void run(ApplicationArguments args) {
		if (properties.password() == null || properties.password().length() < 8) {
			log.warn("DEV_SEED_ENABLED=true but DEV_SEED_PASSWORD is missing or shorter than 8 characters; "
					+ "skipping development seed data.");
			return;
		}
		log.warn("Seeding DEVELOPMENT data (DEV_SEED_ENABLED=true). Never enable this in production.");

		Map<String, Long> departmentIds = new HashMap<>();
		departmentRepository.findAll().forEach(d -> departmentIds.put(d.getCode(), d.getId()));

		int created = 0;
		for (SeedUser seed : USERS) {
			Long departmentId = departmentIds.get(seed.departmentCode());
			Long userId = userRepository.findByEmailIgnoreCase(seed.email()).map(User::getId).orElse(null);
			if (userId == null) {
				Long reportsToId = seed.reportsToEmail() == null ? null
						: userRepository.findByEmailIgnoreCase(seed.reportsToEmail()).map(User::getId).orElse(null);
				userId = userService
					.create(new CreateUserRequest(seed.email(), properties.password(), seed.firstName(),
							seed.lastName(), seed.jobTitle(), null, "Chennai", "09:30 - 18:30 IST", departmentId,
							reportsToId, null, Set.of(seed.role()), seed.permissions()), null, ClientInfo.unknown())
					.id();
				created++;
			}
			if (seed.leadsDepartment()) {
				departmentService.assignManagerIfUnset(departmentId, userId);
			}
		}
		log.info("Development seed: {} users created, {} already present", created, USERS.size() - created);
	}

	record SeedUser(String email, String firstName, String lastName, String jobTitle, String departmentCode,
			String role, Set<String> permissions, String reportsToEmail, boolean leadsDepartment) {
	}

	/** Exposed for tests: every seeded department code must exist in V2 reference data. */
	static Set<String> departmentCodes() {
		return Set.copyOf(USERS.stream().map(SeedUser::departmentCode).toList());
	}

}
