package com.teamops.marketing.activity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.teamops.common.audit.AuditService;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.sequence.CodeGenerator;
import com.teamops.marketing.activity.entity.ActivityChecklistItem;
import com.teamops.marketing.activity.entity.ActivityOccurrence;
import com.teamops.marketing.activity.entity.Frequency;
import com.teamops.marketing.activity.entity.MarketingActivity;
import com.teamops.marketing.activity.entity.OccurrenceStatus;
import com.teamops.marketing.activity.repository.ActivityOccurrenceRepository;
import com.teamops.marketing.activity.repository.MarketingActivityRepository;
import com.teamops.notification.repository.NotificationRepository;
import com.teamops.notification.service.NotificationService;
import com.teamops.support.TestFixtures;
import com.teamops.task.entity.Task;
import com.teamops.task.entity.TaskChecklistItem;
import com.teamops.task.entity.TaskSource;
import com.teamops.task.entity.TaskStatus;
import com.teamops.task.event.TaskStatusChangedEvent;
import com.teamops.task.repository.TaskChecklistItemRepository;
import com.teamops.task.repository.TaskHistoryRepository;
import com.teamops.task.repository.TaskRepository;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;

/**
 * Completing a recurring activity's occurrence creates the next one and keeps the old one as history (brief sections
 * 35–36), without a database. {@code ActivityFlowIT} covers the same flow end to end.
 */
class OccurrenceEngineTest {

	private static final Instant NOW = Instant.parse("2026-10-10T06:00:00Z");

	private static final LocalDate OCT_1 = LocalDate.of(2026, 10, 1);

	private static final LocalDate NOV_1 = LocalDate.of(2026, 11, 1);

	private final ActivityOccurrenceRepository occurrenceRepository = mock(ActivityOccurrenceRepository.class);

	private final TaskRepository taskRepository = mock(TaskRepository.class);

	private final TaskChecklistItemRepository checklistRepository = mock(TaskChecklistItemRepository.class);

	private final UserRepository userRepository = mock(UserRepository.class);

	private final CodeGenerator codeGenerator = mock(CodeGenerator.class);

	private final NotificationService notificationService = mock(NotificationService.class);

	private final User seoExecutive = TestFixtures.user(5L, "seo@teamops.test", TestFixtures.role(3L, "EMPLOYEE"));

	private OccurrenceEngine engine;

	private MarketingActivity activity;

	@BeforeEach
	void setUp() {
		engine = new OccurrenceEngine(mock(MarketingActivityRepository.class), occurrenceRepository, taskRepository,
				checklistRepository, mock(TaskHistoryRepository.class), userRepository, codeGenerator,
				notificationService, mock(NotificationRepository.class), mock(AuditService.class),
				new BusinessCalendar(Clock.fixed(NOW, ZoneOffset.UTC), "Asia/Kolkata"));
		when(occurrenceRepository.save(any())).thenAnswer(call -> call.getArgument(0));
		when(taskRepository.save(any())).thenAnswer(call -> call.getArgument(0));
		when(occurrenceRepository.findByActivityIdAndPeriodStart(anyLong(), any())).thenReturn(Optional.empty());
		when(codeGenerator.next(CodeGenerator.TASK)).thenReturn("TSK-000042");
		when(userRepository.getReferenceById(5L)).thenReturn(seoExecutive);

		// The brief's example: "Monthly SEO Ranking Update", due on the 5th, assigned to the SEO executive.
		activity = new MarketingActivity();
		ReflectionTestUtils.setField(activity, "id", 11L);
		activity.setName("Monthly SEO Ranking Update");
		activity.setDepartment(TestFixtures.department(7L, "DM", "Digital Marketing"));
		activity.setFrequency(Frequency.MONTHLY);
		activity.setStartDate(LocalDate.of(2026, 1, 1));
		activity.setDueOffsetDays(4);
		activity.setTaskTitleTemplate("Update {month} keyword rankings");
		activity.setDefaultAssignee(seoExecutive);
		ActivityChecklistItem step = new ActivityChecklistItem();
		step.setContent("Export positions");
		activity.getChecklist().add(step);
	}

	private ActivityOccurrence october() {
		ActivityOccurrence occurrence = new ActivityOccurrence();
		ReflectionTestUtils.setField(occurrence, "id", 100L);
		occurrence.setActivity(activity);
		occurrence.setPeriodStart(OCT_1);
		occurrence.setPeriodEnd(LocalDate.of(2026, 10, 31));
		occurrence.setDueDate(LocalDate.of(2026, 10, 5));
		return occurrence;
	}

	private ActivityOccurrence savedOccurrence() {
		ArgumentCaptor<ActivityOccurrence> saved = ArgumentCaptor.forClass(ActivityOccurrence.class);
		verify(occurrenceRepository).save(saved.capture());
		return saved.getValue();
	}

	@Test
	void completingOctoberCreatesNovemberWithItsTaskAndKeepsOctoberAsHistory() {
		ActivityOccurrence october = october();

		engine.close(october, OccurrenceStatus.COMPLETED, 5L);

		assertThat(october.getStatus()).isEqualTo(OccurrenceStatus.COMPLETED);
		assertThat(october.getCompletedAt()).isEqualTo(NOW);
		assertThat(october.getCompletedBy()).isSameAs(seoExecutive);
		assertThat(october.getPeriodStart()).as("the closed occurrence keeps its period").isEqualTo(OCT_1);
		verify(occurrenceRepository, never()).delete(any(ActivityOccurrence.class));

		ActivityOccurrence november = savedOccurrence();
		assertThat(november).isNotSameAs(october);
		assertThat(november.getPeriodStart()).isEqualTo(NOV_1);
		assertThat(november.getPeriodEnd()).isEqualTo(LocalDate.of(2026, 11, 30));
		assertThat(november.getDueDate()).isEqualTo(LocalDate.of(2026, 11, 5));
		assertThat(november.getStatus()).isEqualTo(OccurrenceStatus.PENDING);

		Task task = november.getTask();
		assertThat(task.getCode()).isEqualTo("TSK-000042");
		assertThat(task.getTitle()).isEqualTo("Update November keyword rankings");
		assertThat(task.getAssignee()).isSameAs(seoExecutive);
		assertThat(task.getDueDate()).isEqualTo(LocalDate.of(2026, 11, 5));
		assertThat(task.getSource()).isEqualTo(TaskSource.MARKETING_ACTIVITY);
		ArgumentCaptor<TaskChecklistItem> checklist = ArgumentCaptor.forClass(TaskChecklistItem.class);
		verify(checklistRepository).save(checklist.capture());
		assertThat(checklist.getValue().getContent()).isEqualTo("Export positions");
	}

	@Test
	void anExistingNextPeriodIsNeverCreatedTwice() {
		ActivityOccurrence existing = new ActivityOccurrence();
		when(occurrenceRepository.findByActivityIdAndPeriodStart(11L, NOV_1)).thenReturn(Optional.of(existing));

		engine.close(october(), OccurrenceStatus.COMPLETED, 5L);

		verify(occurrenceRepository, never()).save(any());
		verify(taskRepository, never()).save(any());
	}

	@Test
	void noNextOccurrenceAfterTheActivityEndsOrWhenItIsInactive() {
		activity.setEndDate(LocalDate.of(2026, 10, 31));
		engine.close(october(), OccurrenceStatus.COMPLETED, 5L);

		activity.setEndDate(null);
		activity.setActive(false);
		engine.close(october(), OccurrenceStatus.COMPLETED, 5L);

		verify(occurrenceRepository, never()).save(any());
	}

	@Test
	void tasklessActivitiesCreateTheNextOccurrenceWithoutATask() {
		activity.setTaskTitleTemplate(null);

		engine.close(october(), OccurrenceStatus.COMPLETED, 5L);

		assertThat(savedOccurrence().getTask()).isNull();
		verify(taskRepository, never()).save(any());
	}

	@Test
	void completingTheGeneratedTaskCompletesTheOccurrenceAndCreatesTheNext() {
		ActivityOccurrence october = october();
		when(occurrenceRepository.findByTaskId(42L)).thenReturn(Optional.of(october));

		engine.onTaskStatusChanged(new TaskStatusChangedEvent(42L, TaskStatus.IN_REVIEW, TaskStatus.COMPLETED, 5L));

		assertThat(october.getStatus()).isEqualTo(OccurrenceStatus.COMPLETED);
		assertThat(savedOccurrence().getPeriodStart()).isEqualTo(NOV_1);
		verify(notificationService).notify(eq(5L), any(), any(), any(), any(), any(), any());
	}

	@Test
	void cancellingTheTaskSkipsTheOccurrenceAndStillCreatesTheNext() {
		ActivityOccurrence october = october();
		when(occurrenceRepository.findByTaskId(42L)).thenReturn(Optional.of(october));

		engine.onTaskStatusChanged(new TaskStatusChangedEvent(42L, TaskStatus.TODO, TaskStatus.CANCELLED, 5L));

		assertThat(october.getStatus()).isEqualTo(OccurrenceStatus.SKIPPED);
		assertThat(october.getCompletedAt()).isNull();
		assertThat(savedOccurrence().getPeriodStart()).isEqualTo(NOV_1);
	}

	@Test
	void reopeningTheTaskReopensTheOccurrenceAndKeepsTheNextOne() {
		ActivityOccurrence october = october();
		october.setStatus(OccurrenceStatus.COMPLETED);
		october.setCompletedAt(NOW);
		when(occurrenceRepository.findByTaskId(42L)).thenReturn(Optional.of(october));

		engine.onTaskStatusChanged(new TaskStatusChangedEvent(42L, TaskStatus.COMPLETED, TaskStatus.IN_PROGRESS, 5L));

		assertThat(october.getStatus()).isEqualTo(OccurrenceStatus.IN_PROGRESS);
		assertThat(october.getCompletedAt()).isNull();
		verify(occurrenceRepository, never()).save(any());
		verify(occurrenceRepository, never()).delete(any(ActivityOccurrence.class));
	}

}
