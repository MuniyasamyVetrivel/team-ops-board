package com.teamops;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.teamops.department.repository.DepartmentRepository;
import com.teamops.user.repository.PermissionRepository;
import com.teamops.user.repository.RoleRepository;

/**
 * Requires MySQL (credentials from backend/.env). Run with: mvnw verify -Pit. Proves Flyway migrates cleanly and
 * that every JPA entity matches the schema (ddl-auto=validate).
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-seed.enabled=false")
class TeamOpsApplicationIT {

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private PermissionRepository permissionRepository;

	@Autowired
	private DepartmentRepository departmentRepository;

	@Test
	void contextLoadsAndReferenceDataIsPresent() {
		assertThat(roleRepository.count()).isEqualTo(3);
		assertThat(permissionRepository.findAllCodes()).contains("TASK_VIEW", "MARKETING_VIEW", "AUDIT_VIEW")
			.hasSize(46);
		assertThat(departmentRepository.count()).isGreaterThanOrEqualTo(10);
		assertThat(departmentRepository.findByCode("DM")).isPresent();
	}

}
