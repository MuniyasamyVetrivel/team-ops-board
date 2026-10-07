package com.teamops.sla.dto;

import java.time.Instant;
import java.util.List;

import com.teamops.sla.entity.SlaPolicy;
import com.teamops.ticket.dto.TicketDtos.TicketListItem;
import com.teamops.ticket.entity.TicketPriority;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** SLA API records. Compliance values are {@code null} when nothing in the window is decided yet ("—"). */
public final class SlaDtos {

	private SlaDtos() {
	}

	/** At most 30 days per target. */
	public static final int MAX_MINUTES = 43_200;

	public record PolicyResponse(Long id, String name, TicketPriority priority, int firstResponseMinutes,
			int resolutionMinutes, Integer version) {

		public static PolicyResponse of(SlaPolicy policy) {
			return new PolicyResponse(policy.getId(), policy.getName(), policy.getPriority(),
					policy.getFirstResponseMinutes(), policy.getResolutionMinutes(), policy.getVersion());
		}

	}

	/** New targets apply to tickets created afterwards; existing tickets keep their snapshotted due times. */
	public record UpdatePolicy(@NotNull(message = "Version is required") Integer version,
			@NotNull(message = "First response time is required") @Min(value = 1, message = "At least 1 minute") @Max(value = MAX_MINUTES, message = "At most 30 days") Integer firstResponseMinutes,
			@NotNull(message = "Resolution time is required") @Min(value = 1, message = "At least 1 minute") @Max(value = MAX_MINUTES, message = "At most 30 days") Integer resolutionMinutes) {

	}

	public record PriorityRow(TicketPriority priority, int firstResponseMinutes, int resolutionMinutes,
			long created, Integer firstResponseCompliance, Integer resolutionCompliance, long openBreached) {

	}

	/**
	 * @param created tickets created in the window (compliance is measured on these)
	 * @param open tickets open now, split by their worse SLA state; {@code paused} of them wait for the requester
	 * @param atRisk open tickets in WARNING or BREACHED, soonest effective due time first
	 */
	public record Summary(int windowDays, Instant generatedAt, int warningThresholdPct, long created,
			Integer firstResponseCompliance, Integer resolutionCompliance, long open, long onTrack, long warning,
			long breached, long paused, List<PriorityRow> priorities, List<TicketListItem> atRisk) {

	}

	/** Dashboard figures: open tickets and open tickets with a breached SLA, within the viewer's scope. */
	public record TicketKpis(long open, long breached) {

	}

}
