package com.teamops.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.teamops.common.web.ClientInfo;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.user.dto.CreateUserRequest;
import com.teamops.user.repository.UserRepository;
import com.teamops.user.service.UserService;

/**
 * Creates throwaway users with unique emails for integration tests and deletes them afterwards. Integration tests
 * are not {@code @Transactional} because audit entries are written in their own transactions.
 */
public class IntegrationUsers {

	public static final String PASSWORD = "Integration@Test1";

	private final UserService userService;

	private final UserRepository userRepository;

	private final DepartmentRepository departmentRepository;

	private final List<Long> created = new ArrayList<>();

	private final Map<Long, String> emails = new HashMap<>();

	public IntegrationUsers(UserService userService, UserRepository userRepository,
			DepartmentRepository departmentRepository) {
		this.userService = userService;
		this.userRepository = userRepository;
		this.departmentRepository = departmentRepository;
	}

	public Long create(String departmentCode, String role, String... permissions) {
		Long departmentId = departmentRepository.findByCode(departmentCode).orElseThrow().getId();
		return create(departmentId, role, permissions);
	}

	public Long create(Long departmentId, String role, String... permissions) {
		String email = "it-" + UUID.randomUUID() + "@teamops.local";
		Long id = userService.create(new CreateUserRequest(email, PASSWORD, "Integration", role, "QA", null, null,
				null, departmentId, null, null, Set.of(role), Set.of(permissions)), null, ClientInfo.unknown()).id();
		created.add(id);
		emails.put(id, email);
		return id;
	}

	/** Registers a user created through the API so it is cleaned up too. */
	public void track(Long id, String email) {
		created.add(id);
		emails.put(id, email);
	}

	public String email(Long id) {
		return emails.get(id);
	}

	public void deleteAll() {
		List<Long> ids = new ArrayList<>(created);
		Collections.reverse(ids);
		ids.forEach(id -> userRepository.findById(id).ifPresent(userRepository::delete));
		created.clear();
		emails.clear();
	}

}
