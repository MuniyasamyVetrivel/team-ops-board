package com.teamops.sla.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;

/**
 * SLA arithmetic for one deadline (brief section 12). Pure functions, no I/O.
 * <ul>
 * <li>The budget is {@code dueAt − start}, fixed when the ticket was created.</li>
 * <li>Paused time (waiting for the requester, or resolved before a reopen) does not count: it moves the effective due
 * time later by the same amount.</li>
 * <li>A deadline is evaluated at its completion time (first response / resolution) or, while still open, now.</li>
 * <li>BREACHED when the effective due time has passed; WARNING when at least {@code warningPct}% of the budget is used
 * and the deadline is still open; otherwise ON_TRACK.</li>
 * </ul>
 */
public final class SlaCalculator {

	private SlaCalculator() {
	}

	/**
	 * @param pausedMinutes paused time already accumulated
	 * @param pausedAt start of the current pause, or {@code null} when the clock is running
	 * @param completedAt when the deadline was met (responded / resolved), or {@code null}
	 */
	public record Clock(Instant start, Instant dueAt, int warningPct, long pausedMinutes, Instant pausedAt,
			Instant completedAt) {

	}

	/**
	 * @param dueAt effective due time, including paused time so far
	 * @param remainingMinutes negative when overdue (floor of the remaining time)
	 * @param elapsedPercent share of the budget used, never below 0
	 * @param met {@code null} while open; otherwise whether it was completed in time
	 * @param paused whether the clock is currently paused (open deadlines only)
	 */
	public record Status(SlaState state, Instant dueAt, long remainingMinutes, int elapsedPercent, Boolean met,
			boolean paused) {

		public boolean completed() {
			return met != null;
		}

		/** Decided for compliance: completed, or already breached while open. */
		public boolean decided() {
			return met != null || state == SlaState.BREACHED;
		}

		public boolean compliant() {
			return Boolean.TRUE.equals(met);
		}

	}

	public static Status evaluate(Clock clock, Instant now) {
		Instant at = clock.completedAt() != null ? clock.completedAt() : now;
		Duration paused = Duration.ofMinutes(clock.pausedMinutes());
		if (clock.pausedAt() != null && clock.pausedAt().isBefore(at)) {
			paused = paused.plus(Duration.between(clock.pausedAt(), at));
		}
		Instant effectiveDue = clock.dueAt().plus(paused);
		long budgetSeconds = Math.max(1, Duration.between(clock.start(), clock.dueAt()).toSeconds());
		long elapsedSeconds = Math.max(0, Duration.between(clock.start(), at).minus(paused).toSeconds());
		long remainingSeconds = Duration.between(at, effectiveDue).toSeconds();
		int percent = (int) Math.min(Integer.MAX_VALUE, elapsedSeconds * 100 / budgetSeconds);

		boolean completed = clock.completedAt() != null;
		SlaState state;
		if (remainingSeconds < 0) {
			state = SlaState.BREACHED;
		}
		else if (!completed && percent >= clock.warningPct()) {
			state = SlaState.WARNING;
		}
		else {
			state = SlaState.ON_TRACK;
		}
		return new Status(state, effectiveDue, Math.floorDiv(remainingSeconds, 60), percent,
				completed ? remainingSeconds >= 0 : null, !completed && clock.pausedAt() != null);
	}

	/** Minutes between two instants, rounded down (used when a pause ends). */
	public static long minutesBetween(Instant from, Instant to) {
		return Math.max(0, Duration.between(from, to).toMinutes());
	}

	/** Compliance % = met ÷ decided × 100, rounded half up; {@code null} when nothing is decided yet. */
	public static Integer compliancePercent(Collection<Status> statuses) {
		long decided = 0;
		long met = 0;
		for (Status status : statuses) {
			if (status.decided()) {
				decided++;
				if (status.compliant()) {
					met++;
				}
			}
		}
		return decided == 0 ? null : (int) Math.round(met * 100.0 / decided);
	}

}
