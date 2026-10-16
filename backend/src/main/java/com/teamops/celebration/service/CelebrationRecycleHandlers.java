package com.teamops.celebration.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.teamops.celebration.entity.Employee;
import com.teamops.celebration.entity.ImportantDay;
import com.teamops.celebration.entity.MarketingEvent;
import com.teamops.celebration.entity.OccasionKind;
import com.teamops.celebration.repository.EmployeeRepository;
import com.teamops.common.exception.ApiException;
import com.teamops.common.storage.FileService;
import com.teamops.common.storage.StoredFile;
import com.teamops.recyclebin.service.RecycleBinHandler;
import com.teamops.user.repository.UserRepository;

/**
 * Celebrations in the recycle bin keep their photo, creatives, attachments and LinkedIn posts; those go only with a
 * permanent delete (they point at the record by kind and id, without a foreign key). An employee comes back only while
 * no live employee has its employee ID, and the restore warns when its work email now belongs to someone else.
 */
@Configuration
class CelebrationRecycleHandlers {

	private static final Set<OccasionKind> EMPLOYEE_KINDS = Set.of(OccasionKind.BIRTHDAY, OccasionKind.WORK_ANNIVERSARY);

	@Bean
	RecycleBinHandler<Employee> employeeRecycleHandler(LinkedInPostService linkedInPosts, FileService fileService,
			EmployeeRepository employees, UserRepository users) {
		return new RecycleBinHandler<>() {

			@Override
			public Class<Employee> type() {
				return Employee.class;
			}

			@Override
			public void checkRestore(Employee employee) {
				if (employees.existsByEmployeeCodeAndIdNot(employee.getEmployeeCode(), employee.getId())) {
					throw ApiException.conflict("RESTORE_CONFLICT", "Another employee now has the employee ID "
							+ employee.getEmployeeCode() + ". Change that employee's ID first.");
				}
			}

			@Override
			public List<String> restoreWarnings(Employee employee) {
				String email = employee.getWorkEmail();
				if (email == null || email.isBlank()) {
					return List.of();
				}
				List<String> warnings = new ArrayList<>();
				employees.findFirstByWorkEmailIgnoreCaseAndIdNot(email, employee.getId())
					.ifPresent(other -> warnings.add("The work email " + email + " now belongs to employee "
							+ other.getEmployeeCode() + " (" + other.getFirstName() + " " + other.getLastName() + ")."));
				Long linkedUserId = employee.getUser() == null ? null : employee.getUser().getId();
				users.findByEmailIgnoreCase(email)
					.filter(user -> !Objects.equals(user.getId(), linkedUserId))
					.ifPresent(user -> warnings.add("The work email " + email + " is the sign-in email of "
							+ user.getFullName() + ", who is not linked to this employee."));
				return warnings;
			}

			@Override
			public void beforePurge(Employee employee) {
				linkedInPosts.deleteFor(EMPLOYEE_KINDS, employee.getId());
			}

			@Override
			public void afterPurge(Employee employee) {
				StoredFile photo = employee.getPhoto();
				if (photo != null) {
					fileService.delete(photo);
				}
			}

		};
	}

	@Bean
	RecycleBinHandler<ImportantDay> importantDayRecycleHandler(OccasionAttachmentService attachments,
			LinkedInPostService linkedInPosts) {
		return new RecycleBinHandler<>() {

			@Override
			public Class<ImportantDay> type() {
				return ImportantDay.class;
			}

			@Override
			public void beforePurge(ImportantDay day) {
				attachments.deleteAll(OccasionKind.IMPORTANT_DAY, day.getId());
				linkedInPosts.deleteFor(Set.of(OccasionKind.IMPORTANT_DAY), day.getId());
			}

		};
	}

	@Bean
	RecycleBinHandler<MarketingEvent> marketingEventRecycleHandler(OccasionAttachmentService attachments,
			LinkedInPostService linkedInPosts) {
		return new RecycleBinHandler<>() {

			@Override
			public Class<MarketingEvent> type() {
				return MarketingEvent.class;
			}

			@Override
			public void beforePurge(MarketingEvent event) {
				attachments.deleteAll(OccasionKind.EVENT, event.getId());
				linkedInPosts.deleteFor(Set.of(OccasionKind.EVENT), event.getId());
			}

		};
	}

}
