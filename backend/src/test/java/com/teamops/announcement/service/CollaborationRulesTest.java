package com.teamops.announcement.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.teamops.announcement.entity.Announcement;
import com.teamops.announcement.entity.AnnouncementState;
import com.teamops.common.security.AccessScope;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.department.entity.Department;
import com.teamops.document.entity.Document;
import com.teamops.document.service.DocumentService;
import com.teamops.support.SliceAuth;
import com.teamops.support.TestFixtures;
import com.teamops.user.entity.User;

/** Announcement state and audience, and document visibility. */
class CollaborationRulesTest {

	private static final Instant NOW = Instant.parse("2026-10-08T10:00:00Z");

	private final Department webDev = TestFixtures.department(5L, "WEBDEV", "Web Development");

	private final Department marketing = TestFixtures.department(7L, "DM", "Digital Marketing");

	private static final AuthenticatedUser MANAGER = new AuthenticatedUser(3L, "sanjay@x", "Sanjay", 5L,
			Set.of("DEPARTMENT_MANAGER"), Set.of("DASHBOARD_VIEW", "ANNOUNCEMENT_MANAGE", "DOCUMENT_VIEW",
					"DOCUMENT_EDIT"));

	@Test
	void announcementStateFollowsPublishAndExpiryTimes() {
		assertThat(announcement(null, NOW.plusSeconds(60), null).stateAt(NOW)).isEqualTo(AnnouncementState.SCHEDULED);
		assertThat(announcement(null, NOW, null).stateAt(NOW)).isEqualTo(AnnouncementState.ACTIVE);
		assertThat(announcement(null, NOW.minusSeconds(60), NOW).stateAt(NOW)).isEqualTo(AnnouncementState.EXPIRED);
		assertThat(announcement(null, NOW.minusSeconds(60), NOW.plusSeconds(1)).stateAt(NOW))
			.isEqualTo(AnnouncementState.ACTIVE);
	}

	@Test
	void audienceIsOwnAndManagedDepartmentsOrEveryoneForSuperAdmin() {
		assertThat(AnnouncementService.audienceOf(SliceAuth.EMPLOYEE, AccessScope.own(4L))).containsExactly(5L);
		assertThat(AnnouncementService.audienceOf(MANAGER, AccessScope.departments(3L, Set.of(5L, 9L))))
			.containsExactlyInAnyOrder(5L, 9L);
		assertThat(AnnouncementService.audienceOf(SliceAuth.SUPER_ADMIN, AccessScope.all(1L))).isNull();
	}

	@Test
	void managersManageTheirDepartmentsAnnouncementsAndTheirOwn() {
		AccessScope scope = AccessScope.departments(3L, Set.of(5L));
		assertThat(AnnouncementService.canManage(announcement(webDev, NOW, null), MANAGER, scope)).isTrue();
		assertThat(AnnouncementService.canManage(announcement(marketing, NOW, null), MANAGER, scope)).isFalse();
		assertThat(AnnouncementService.canManage(announcement(null, NOW, null), MANAGER, scope))
			.as("company-wide").isFalse();
		Announcement own = announcement(marketing, NOW, null);
		own.setCreatedBy(user(3L));
		assertThat(AnnouncementService.canManage(own, MANAGER, scope)).isTrue();
		assertThat(AnnouncementService.canManage(announcement(webDev, NOW, null), SliceAuth.EMPLOYEE,
				AccessScope.own(4L))).isFalse();
	}

	@Test
	void documentsAreVisibleCompanyWideInOwnDepartmentsAndToTheUploader() {
		AccessScope own = AccessScope.own(4L);
		assertThat(DocumentService.canView(document(null, null), SliceAuth.EMPLOYEE, own)).as("company-wide").isTrue();
		assertThat(DocumentService.canView(document(webDev, null), SliceAuth.EMPLOYEE, own)).isTrue();
		assertThat(DocumentService.canView(document(marketing, null), SliceAuth.EMPLOYEE, own)).isFalse();
		assertThat(DocumentService.canView(document(marketing, user(4L)), SliceAuth.EMPLOYEE, own)).isTrue();
	}

	@Test
	void documentEditsNeedDocumentEditAndManagementOrOwnership() {
		AccessScope scope = AccessScope.departments(3L, Set.of(5L));
		assertThat(DocumentService.canEdit(document(webDev, null), MANAGER, scope)).isTrue();
		assertThat(DocumentService.canEdit(document(marketing, null), MANAGER, scope)).isFalse();
		assertThat(DocumentService.canEdit(document(null, null), MANAGER, scope)).as("company-wide").isFalse();
		assertThat(DocumentService.canEdit(document(webDev, user(4L)), SliceAuth.EMPLOYEE, AccessScope.own(4L)))
			.as("no DOCUMENT_EDIT").isFalse();
		assertThat(DocumentService.canEdit(document(null, null), SliceAuth.SUPER_ADMIN, AccessScope.all(1L))).isTrue();
	}

	private static Announcement announcement(Department target, Instant publishAt, Instant expiresAt) {
		Announcement announcement = new Announcement();
		ReflectionTestUtils.setField(announcement, "id", 1L);
		announcement.setTitle("Office closed Friday");
		announcement.setBody("Diwali");
		announcement.setTargetDepartment(target);
		announcement.setPublishAt(publishAt);
		announcement.setExpiresAt(expiresAt);
		return announcement;
	}

	private static Document document(Department department, User uploader) {
		Document document = new Document();
		document.setName("Leave policy");
		document.setDepartment(department);
		document.setUploadedBy(uploader);
		return document;
	}

	private static User user(long id) {
		return TestFixtures.user(id, "user" + id + "@teamops.local", TestFixtures.role(3L, "EMPLOYEE"));
	}

}
