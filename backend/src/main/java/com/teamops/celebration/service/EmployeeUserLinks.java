package com.teamops.celebration.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.celebration.repository.EmployeeRepository;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;
import com.teamops.user.service.EmployeeIds;

import lombok.RequiredArgsConstructor;

/**
 * Links app users to Employee records (celebrations) by employee ID (Expansion Phase X4): a user and the Employee
 * whose employee code equals the user's employee ID are the same person, so the details live once. Kept in step from
 * both sides: when a user's employee ID is set or changed, and when an Employee is saved.
 */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class EmployeeUserLinks {

	private final EmployeeRepository employees;

	private final UserRepository users;

	/**
	 * After a user's employee ID was set or changed: unlinks an Employee with another code from them, and links the
	 * Employee whose code is their employee ID (moving it from anyone it was linked to by hand before).
	 */
	public void linkUser(User user) {
		employees.findByUserId(user.getId())
			.filter(linked -> !EmployeeIds.same(linked.getEmployeeCode(), user.getEmployeeId()))
			.ifPresent(linked -> {
				linked.setUser(null);
				// The user_id key is unique: free it before another Employee takes it.
				employees.flush();
			});
		if (user.getEmployeeId() == null) {
			return;
		}
		employees.findByEmployeeCode(user.getEmployeeId())
			.filter(employee -> employee.getUser() == null || !employee.getUser().getId().equals(user.getId()))
			.ifPresent(employee -> employee.setUser(user));
	}

	/** The app user whose employee ID is this employee code, if any. */
	public User userFor(String employeeCode) {
		return users.findByEmployeeIdIgnoreCase(employeeCode).orElse(null);
	}

	/** Whether an Employee with this code and the user may be linked: the user has no employee ID yet, or this one. */
	public static boolean matches(String employeeCode, User user) {
		return user.getEmployeeId() == null || EmployeeIds.same(user.getEmployeeId(), employeeCode);
	}

}
