package com.teamops.task.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.teamops.task.entity.TaskStatus;

class DueStateTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);

	@ParameterizedTest(name = "due {0} days from today, {1} -> {2}")
	@CsvSource({ "-1, TODO, OVERDUE", "-30, BLOCKED, OVERDUE", "0, IN_PROGRESS, DUE_TODAY", "1, TODO, DUE_SOON",
			"3, IN_REVIEW, DUE_SOON", "4, TODO, SCHEDULED", "-5, COMPLETED, NONE", "0, CANCELLED, NONE" })
	void classifiesActiveTasksByDueDate(int offset, TaskStatus status, DueState expected) {
		assertThat(DueState.of(TODAY.plusDays(offset), status, TODAY)).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource({ "TODO", "COMPLETED" })
	void noDueDateIsNone(TaskStatus status) {
		assertThat(DueState.of(null, status, TODAY)).isEqualTo(DueState.NONE);
	}

}
