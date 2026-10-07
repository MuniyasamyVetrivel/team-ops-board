package com.teamops.sla.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.teamops.sla.service.SlaCalculator.Clock;
import com.teamops.sla.service.SlaCalculator.Status;

/** Brief section 12: due times, warning, breach, pausing and compliance. */
class SlaCalculatorTest {

	private static final Instant START = Instant.parse("2026-10-08T04:00:00Z");

	/** URGENT resolution: 4 hours, warning at 75%. */
	private static Clock urgentResolution(long pausedMinutes, Instant pausedAt, Instant completedAt) {
		return new Clock(START, START.plus(Duration.ofHours(4)), 75, pausedMinutes, pausedAt, completedAt);
	}

	private static Instant at(int minutes) {
		return START.plus(Duration.ofMinutes(minutes));
	}

	@Test
	void onTrackEarlyWithRemainingTime() {
		Status status = SlaCalculator.evaluate(urgentResolution(0, null, null), at(60));

		assertThat(status.state()).isEqualTo(SlaState.ON_TRACK);
		assertThat(status.remainingMinutes()).isEqualTo(180);
		assertThat(status.elapsedPercent()).isEqualTo(25);
		assertThat(status.dueAt()).isEqualTo(at(240));
		assertThat(status.met()).as("still open").isNull();
		assertThat(status.paused()).isFalse();
	}

	@Test
	void warningFromTheThresholdUntilTheDueTime() {
		assertThat(SlaCalculator.evaluate(urgentResolution(0, null, null), at(179)).state())
			.isEqualTo(SlaState.ON_TRACK);
		assertThat(SlaCalculator.evaluate(urgentResolution(0, null, null), at(180)).state())
			.as("exactly 75%").isEqualTo(SlaState.WARNING);
		assertThat(SlaCalculator.evaluate(urgentResolution(0, null, null), at(240)).state())
			.as("due now is not yet late").isEqualTo(SlaState.WARNING);
	}

	@Test
	void breachedOnceTheDueTimeHasPassed() {
		Status status = SlaCalculator.evaluate(urgentResolution(0, null, null), at(241));

		assertThat(status.state()).isEqualTo(SlaState.BREACHED);
		assertThat(status.remainingMinutes()).isEqualTo(-1);
		assertThat(status.decided()).as("a breach is final for compliance").isTrue();
		assertThat(status.compliant()).isFalse();
	}

	@Test
	void briefDefaultsGiveTheExpectedDueTimes() {
		// First response / resolution: URGENT 1h/4h, HIGH 2h/8h, MEDIUM 4h/24h, LOW 8h/48h.
		int[][] targets = { { 60, 240 }, { 120, 480 }, { 240, 1440 }, { 480, 2880 } };
		for (int[] target : targets) {
			Clock first = new Clock(START, at(target[0]), 75, 0, null, null);
			Clock resolution = new Clock(START, at(target[1]), 75, 0, null, null);
			assertThat(SlaCalculator.evaluate(first, START).dueAt()).isEqualTo(at(target[0]));
			assertThat(SlaCalculator.evaluate(resolution, START).dueAt()).isEqualTo(at(target[1]));
			assertThat(SlaCalculator.evaluate(first, at(target[0] + 1)).state()).isEqualTo(SlaState.BREACHED);
		}
	}

	@Test
	void pausedTimeDoesNotCountAndPushesTheDueTimeBack() {
		// Waiting for the requester since minute 120; now minute 300 (would be breached without the pause).
		Status paused = SlaCalculator.evaluate(urgentResolution(0, at(120), null), at(300));

		assertThat(paused.paused()).isTrue();
		assertThat(paused.state()).isEqualTo(SlaState.ON_TRACK);
		assertThat(paused.elapsedPercent()).as("only 120 of 240 minutes used").isEqualTo(50);
		assertThat(paused.dueAt()).isEqualTo(at(420));
		assertThat(paused.remainingMinutes()).isEqualTo(120);
	}

	@Test
	void accumulatedPausesKeepCountingAfterTheClockResumes() {
		// 90 minutes were paused earlier; the clock runs again. At minute 300, 210 minutes have counted.
		Status status = SlaCalculator.evaluate(urgentResolution(90, null, null), at(300));

		assertThat(status.paused()).isFalse();
		assertThat(status.dueAt()).isEqualTo(at(330));
		assertThat(status.remainingMinutes()).isEqualTo(30);
		assertThat(status.state()).as("210/240 = 87%").isEqualTo(SlaState.WARNING);
	}

	@Test
	void completedDeadlinesAreJudgedAtCompletionTime() {
		Status inTime = SlaCalculator.evaluate(urgentResolution(0, null, at(200)), at(10_000));
		assertThat(inTime.met()).isTrue();
		assertThat(inTime.state()).as("no warning once met").isEqualTo(SlaState.ON_TRACK);
		assertThat(inTime.remainingMinutes()).isEqualTo(40);

		Status late = SlaCalculator.evaluate(urgentResolution(0, null, at(250)), at(10_000));
		assertThat(late.met()).isFalse();
		assertThat(late.state()).isEqualTo(SlaState.BREACHED);
	}

	@Test
	void pauseThatStartedAtResolutionDoesNotCountTowardsIt() {
		// Waiting from 100 to 160 (60 min), then resolved at 250 with the clock paused again from 250.
		Status status = SlaCalculator.evaluate(urgentResolution(60, at(250), at(250)), at(5_000));

		assertThat(status.met()).as("190 minutes counted, within 240").isTrue();
		assertThat(status.paused()).as("completed deadlines are never reported as paused").isFalse();
	}

	@Test
	void complianceIsMetOverDecidedAndNullWhenNothingIsDecided() {
		Status met = SlaCalculator.evaluate(urgentResolution(0, null, at(100)), at(1000));
		Status late = SlaCalculator.evaluate(urgentResolution(0, null, at(300)), at(1000));
		Status breachedOpen = SlaCalculator.evaluate(urgentResolution(0, null, null), at(300));
		Status openOnTrack = SlaCalculator.evaluate(urgentResolution(0, null, null), at(10));

		assertThat(SlaCalculator.compliancePercent(List.of(met, late, breachedOpen, openOnTrack)))
			.as("1 met of 3 decided").isEqualTo(33);
		assertThat(SlaCalculator.compliancePercent(List.of(met, met, late))).isEqualTo(67);
		assertThat(SlaCalculator.compliancePercent(List.of(openOnTrack))).isNull();
		assertThat(SlaCalculator.compliancePercent(List.of())).isNull();
	}

	@Test
	void worstStateWins() {
		assertThat(SlaState.worst(SlaState.ON_TRACK, SlaState.WARNING)).isEqualTo(SlaState.WARNING);
		assertThat(SlaState.worst(SlaState.BREACHED, SlaState.WARNING)).isEqualTo(SlaState.BREACHED);
		assertThat(SlaState.worst(SlaState.ON_TRACK, SlaState.ON_TRACK)).isEqualTo(SlaState.ON_TRACK);
	}

	@Test
	void minutesBetweenRoundsDownAndNeverGoesNegative() {
		assertThat(SlaCalculator.minutesBetween(at(0), START.plusSeconds(119))).isEqualTo(1);
		assertThat(SlaCalculator.minutesBetween(at(10), at(0))).isZero();
	}

}
