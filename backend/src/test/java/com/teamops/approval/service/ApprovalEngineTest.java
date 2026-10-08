package com.teamops.approval.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import com.teamops.approval.entity.Approval;
import com.teamops.approval.entity.ApprovalStatus;
import com.teamops.approval.entity.ApprovalStep;
import com.teamops.approval.entity.ApprovalTypeStep;
import com.teamops.approval.entity.ApproverKind;
import com.teamops.approval.entity.StepStatus;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.support.SliceAuth;
import com.teamops.support.TestFixtures;
import com.teamops.user.entity.Role;
import com.teamops.user.entity.User;

/** Brief section 14: sequential steps, no self-approval, escalation, rejection and cancellation. */
class ApprovalEngineTest {

	private static final Instant NOW = Instant.parse("2026-10-08T10:00:00Z");

	private final Role superAdmin = TestFixtures.role(1L, "SUPER_ADMIN");

	private final Role financeRole = TestFixtures.role(7L, "FINANCE");

	private final User karthik = user(4L);

	private final User sanjay = user(3L);

	private final User rakesh = user(1L);

	private static final AuthenticatedUser SANJAY = principal(3L, "DEPARTMENT_MANAGER");

	private static final AuthenticatedUser KARTHIK = principal(4L, "EMPLOYEE");

	@Test
	void departmentManagerStepGoesToTheRequestersManager() {
		Approval approval = submit(karthik, sanjay, step(ApproverKind.DEPARTMENT_MANAGER, null, null));

		assertThat(approval.getSteps()).singleElement().satisfies(step -> {
			assertThat(step.getApproverKind()).isEqualTo(ApproverKind.DEPARTMENT_MANAGER);
			assertThat(step.getApprover()).isSameAs(sanjay);
			assertThat(step.getStatus()).isEqualTo(StepStatus.PENDING);
		});
		assertThat(approval.getCurrentStep()).isEqualTo(1);
		assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.PENDING);
	}

	@Test
	void managersOwnRequestsAndDepartmentsWithoutAManagerEscalateToSuperAdmin() {
		Approval own = submit(sanjay, sanjay, step(ApproverKind.DEPARTMENT_MANAGER, null, null));
		Approval noManager = submit(karthik, null, step(ApproverKind.DEPARTMENT_MANAGER, null, null));

		for (Approval approval : List.of(own, noManager)) {
			ApprovalStep step = approval.getSteps().get(0);
			assertThat(step.getApproverKind()).isEqualTo(ApproverKind.ROLE);
			assertThat(step.getApproverRole()).isSameAs(superAdmin);
			assertThat(step.getApprover()).isNull();
		}
	}

	@Test
	void selfApprovalStepsAreSkippedAndRepeatedRolesCollapse() {
		Approval approval = submit(sanjay, sanjay, step(ApproverKind.DEPARTMENT_MANAGER, null, null),
				step(ApproverKind.ROLE, superAdmin, null), step(ApproverKind.USER, null, sanjay),
				step(ApproverKind.ROLE, financeRole, null));

		assertThat(approval.getSteps()).extracting(ApprovalStep::getStatus)
			.containsExactly(StepStatus.PENDING, StepStatus.SKIPPED, StepStatus.SKIPPED, StepStatus.WAITING);
	}

	@Test
	void approvingEveryStepApprovesTheRequest() {
		Approval approval = submit(karthik, sanjay, step(ApproverKind.DEPARTMENT_MANAGER, null, null),
				step(ApproverKind.ROLE, superAdmin, null));

		assertThat(ApprovalEngine.decide(approval, SANJAY, sanjay, true, null, NOW)).hasValueSatisfying(next -> {
			assertThat(next.getStepOrder()).isEqualTo(2);
			assertThat(next.getStatus()).isEqualTo(StepStatus.PENDING);
		});
		assertThat(approval.getCurrentStep()).isEqualTo(2);
		assertThat(approval.getSteps().get(0).getDecidedBy()).isSameAs(sanjay);

		assertThat(ApprovalEngine.decide(approval, SliceAuth.SUPER_ADMIN, rakesh, true, "OK", NOW)).isEmpty();
		assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
		assertThat(approval.getDecidedAt()).isEqualTo(NOW);
		assertThat(approval.getCurrentStep()).isNull();
	}

	@Test
	void rejectingNeedsAReasonAndSkipsTheRemainingSteps() {
		Approval approval = submit(karthik, sanjay, step(ApproverKind.DEPARTMENT_MANAGER, null, null),
				step(ApproverKind.ROLE, superAdmin, null));

		assertError(() -> ApprovalEngine.decide(approval, SANJAY, sanjay, false, " ", NOW), HttpStatus.BAD_REQUEST,
				"REASON_REQUIRED");
		ApprovalEngine.decide(approval, SANJAY, sanjay, false, "Not in budget", NOW);

		assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
		assertThat(approval.getSteps()).extracting(ApprovalStep::getStatus)
			.containsExactly(StepStatus.REJECTED, StepStatus.SKIPPED);
		assertThat(approval.getSteps().get(0).getComment()).isEqualTo("Not in budget");
	}

	@Test
	void onlyTheCurrentApproverMayDecideAndNeverTheRequester() {
		Approval approval = submit(karthik, sanjay, step(ApproverKind.DEPARTMENT_MANAGER, null, null),
				step(ApproverKind.ROLE, superAdmin, null));

		assertError(() -> ApprovalEngine.decide(approval, SliceAuth.SUPER_ADMIN, rakesh, true, null, NOW),
				HttpStatus.FORBIDDEN, "NOT_YOUR_DECISION");
		assertError(() -> ApprovalEngine.decide(approval, KARTHIK, karthik, true, null, NOW), HttpStatus.FORBIDDEN,
				"NOT_YOUR_DECISION");

		AuthenticatedUser managerWithoutDecide = new AuthenticatedUser(3L, "s@x", "Sanjay", 5L,
				Set.of("DEPARTMENT_MANAGER"), Set.of("APPROVAL_VIEW"));
		assertThat(ApprovalEngine.canDecide(approval, approval.getSteps().get(0), managerWithoutDecide)).isFalse();
		assertThat(ApprovalEngine.canDecide(approval, approval.getSteps().get(0), SANJAY)).isTrue();
	}

	@Test
	void anyHolderOfTheRoleDecidesARoleStep() {
		Approval approval = submit(karthik, null, step(ApproverKind.ROLE, superAdmin, null));
		AuthenticatedUser otherAdmin = new AuthenticatedUser(2L, "admin2@x", "Admin Two", 1L, Set.of("SUPER_ADMIN"),
				SliceAuth.ALL_PERMISSIONS);

		assertThat(ApprovalEngine.canDecide(approval, approval.getSteps().get(0), SliceAuth.SUPER_ADMIN)).isTrue();
		assertThat(ApprovalEngine.canDecide(approval, approval.getSteps().get(0), otherAdmin)).isTrue();
		assertThat(ApprovalEngine.canDecide(approval, approval.getSteps().get(0), SANJAY)).isFalse();
	}

	@Test
	void aWorkflowWithNothingLeftToDecideIsApprovedImmediately() {
		Approval approval = submit(sanjay, sanjay, step(ApproverKind.USER, null, sanjay));

		assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
		assertThat(approval.getDecidedAt()).isEqualTo(NOW);
	}

	@Test
	void cancellingSkipsOpenStepsAndOnlyWorksWhilePending() {
		Approval approval = submit(karthik, sanjay, step(ApproverKind.DEPARTMENT_MANAGER, null, null),
				step(ApproverKind.ROLE, superAdmin, null));

		ApprovalEngine.cancel(approval, NOW);

		assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.CANCELLED);
		assertThat(approval.getSteps()).extracting(ApprovalStep::getStatus)
			.containsOnly(StepStatus.SKIPPED);
		assertError(() -> ApprovalEngine.cancel(approval, NOW), HttpStatus.CONFLICT, "NOT_PENDING");
		assertError(() -> ApprovalEngine.decide(approval, SANJAY, sanjay, true, null, NOW), HttpStatus.CONFLICT,
				"NOT_PENDING");
	}

	// --- fixtures -------------------------------------------------------------------------------------------

	private Approval submit(User requester, User manager, ApprovalTypeStep... template) {
		Approval approval = new Approval();
		ReflectionTestUtils.setField(approval, "id", 9L);
		approval.setRequester(requester);
		approval.getSteps().addAll(ApprovalEngine.materialize(approval, List.of(template), manager, superAdmin));
		ApprovalEngine.advance(approval, NOW);
		return approval;
	}

	private static ApprovalTypeStep step(ApproverKind kind, Role role, User user) {
		ApprovalTypeStep step = new ApprovalTypeStep();
		step.setApproverKind(kind);
		step.setApproverRole(role);
		step.setApproverUser(user);
		return step;
	}

	private static User user(long id) {
		return TestFixtures.user(id, "user" + id + "@teamops.local", TestFixtures.role(3L, "EMPLOYEE"));
	}

	private static AuthenticatedUser principal(long id, String role) {
		return new AuthenticatedUser(id, "user" + id + "@teamops.local", "User " + id, 5L, Set.of(role),
				Set.of("APPROVAL_VIEW", "APPROVAL_DECIDE"));
	}

	private static void assertError(ThrowingCallable call, HttpStatus status, String code) {
		assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, ex -> {
			assertThat(ex.getStatus()).isEqualTo(status);
			assertThat(ex.getCode()).isEqualTo(code);
		});
	}

}
