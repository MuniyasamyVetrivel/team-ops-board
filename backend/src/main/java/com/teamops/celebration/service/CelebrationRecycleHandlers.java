package com.teamops.celebration.service;

import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.teamops.celebration.entity.Employee;
import com.teamops.celebration.entity.ImportantDay;
import com.teamops.celebration.entity.MarketingEvent;
import com.teamops.celebration.entity.OccasionKind;
import com.teamops.common.storage.FileService;
import com.teamops.common.storage.StoredFile;
import com.teamops.recyclebin.service.RecycleBinHandler;

/**
 * Celebrations in the recycle bin keep their photo, creatives, attachments and LinkedIn posts; those go only with a
 * permanent delete (they point at the record by kind and id, without a foreign key).
 */
@Configuration
class CelebrationRecycleHandlers {

	private static final Set<OccasionKind> EMPLOYEE_KINDS = Set.of(OccasionKind.BIRTHDAY, OccasionKind.WORK_ANNIVERSARY);

	@Bean
	RecycleBinHandler<Employee> employeeRecycleHandler(LinkedInPostService linkedInPosts, FileService fileService) {
		return new RecycleBinHandler<>() {

			@Override
			public Class<Employee> type() {
				return Employee.class;
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
