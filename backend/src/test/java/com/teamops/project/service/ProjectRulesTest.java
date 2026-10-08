package com.teamops.project.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.teamops.common.security.AccessScope;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.department.entity.Department;
import com.teamops.project.dto.ProjectDtos;
import com.teamops.project.dto.ProjectDtos.MilestoneResponse;
import com.teamops.project.dto.ProjectDtos.RiskResponse;
import com.teamops.project.entity.MilestoneStatus;
import com.teamops.project.entity.Project;
import com.teamops.project.entity.ProjectMilestone;
import com.teamops.project.entity.ProjectRisk;
import com.teamops.project.entity.RiskLevel;
import com.teamops.project.repository.ProjectQuery.TaskCounts;
import com.teamops.support.SliceAuth;
import com.teamops.support.TestFixtures;
import com.teamops.user.entity.User;

/** Project progress, milestone and risk rules, and project access. */
class ProjectRulesTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 8);

	private final Department webDev = TestFixtures.department(5L, "WEBDEV", "Web Development");

	private final Department marketing = TestFixtures.department(7L, "DM", "Digital Marketing");

	@Test
	void progressComesFromTasksUnlessOverridden() {
		assertThat(ProjectDtos.progress(null, new TaskCounts(8, 3, 5, 1))).as("3 of 8").isEqualTo(38);
		assertThat(ProjectDtos.progress(null, new TaskCounts(4, 4, 0, 0))).isEqualTo(100);
		assertThat(ProjectDtos.progress(null, TaskCounts.EMPTY)).as("no tasks: —").isNull();
		assertThat(ProjectDtos.progress(60, new TaskCounts(8, 3, 5, 1))).as("override wins").isEqualTo(60);
		assertThat(ProjectDtos.progress(0, TaskCounts.EMPTY)).isZero();
	}

	@Test
	void milestonesAreOverdueOnlyWhenOpenAndPastDue() {
		assertThat(MilestoneResponse.of(milestone(TODAY.minusDays(1), MilestoneStatus.IN_PROGRESS), TODAY).overdue())
			.isTrue();
		assertThat(MilestoneResponse.of(milestone(TODAY.minusDays(1), MilestoneStatus.COMPLETED), TODAY).overdue())
			.isFalse();
		assertThat(MilestoneResponse.of(milestone(TODAY, MilestoneStatus.PLANNED), TODAY).overdue()).isFalse();
		assertThat(MilestoneResponse.of(milestone(null, MilestoneStatus.PLANNED), TODAY).overdue()).isFalse();
	}

	@Test
	void riskSeverityIsProbabilityTimesImpact() {
		assertThat(RiskResponse.of(risk(RiskLevel.HIGH, RiskLevel.HIGH)).severity()).isEqualTo(9);
		assertThat(RiskResponse.of(risk(RiskLevel.LOW, RiskLevel.HIGH)).severity()).isEqualTo(3);
		assertThat(RiskResponse.of(risk(RiskLevel.LOW, RiskLevel.LOW)).severity()).isEqualTo(1);
	}

	@Test
	void employeesSeeTheirDepartmentsProjectsAndProjectsTheyBelongTo() {
		ProjectAccess employee = new ProjectAccess(SliceAuth.EMPLOYEE, AccessScope.own(4L));
		Project ours = project(webDev, null);
		Project theirs = project(marketing, null);

		assertThat(employee.canView(ours)).isTrue();
		assertThat(employee.canView(theirs)).isFalse();
		theirs.getMembers().add(user(4L));
		assertThat(employee.canView(theirs)).as("member").isTrue();
		assertThat(employee.canEdit(ours)).as("no PROJECT_EDIT").isFalse();
		assertThat(employee.canCreateIn(5L)).isFalse();
	}

	@Test
	void managersAndOwnersEditButManagersOnlyCreateInTheirDepartments() {
		AuthenticatedUser manager = new AuthenticatedUser(3L, "sanjay@x", "Sanjay", 5L, Set.of("DEPARTMENT_MANAGER"),
				Set.of("PROJECT_VIEW", "PROJECT_EDIT"));
		ProjectAccess access = new ProjectAccess(manager, AccessScope.departments(3L, Set.of(5L)));

		assertThat(access.canEdit(project(webDev, null))).isTrue();
		assertThat(access.canEdit(project(marketing, null))).isFalse();
		assertThat(access.canEdit(project(marketing, user(3L)))).as("owner").isTrue();
		assertThat(access.canCreateIn(5L)).isTrue();
		assertThat(access.canCreateIn(7L)).isFalse();
	}

	private static ProjectMilestone milestone(LocalDate due, MilestoneStatus status) {
		ProjectMilestone milestone = new ProjectMilestone();
		milestone.setName("Launch");
		milestone.setDueDate(due);
		milestone.setStatus(status);
		return milestone;
	}

	private static ProjectRisk risk(RiskLevel probability, RiskLevel impact) {
		ProjectRisk risk = new ProjectRisk();
		risk.setTitle("Vendor delay");
		risk.setProbability(probability);
		risk.setImpact(impact);
		return risk;
	}

	private static Project project(Department department, User owner) {
		Project project = new Project();
		project.setName("Website revamp");
		project.setDepartment(department);
		project.setOwner(owner);
		return project;
	}

	private static User user(long id) {
		return TestFixtures.user(id, "user" + id + "@teamops.local", TestFixtures.role(3L, "EMPLOYEE"));
	}

}
