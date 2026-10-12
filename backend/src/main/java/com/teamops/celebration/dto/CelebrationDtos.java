package com.teamops.celebration.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.teamops.celebration.entity.DayRecurrence;
import com.teamops.celebration.entity.EmployeeStatus;
import com.teamops.celebration.entity.ImportantDayCategory;
import com.teamops.celebration.entity.LinkedInPostStatus;
import com.teamops.celebration.entity.MarketingEventStatus;
import com.teamops.celebration.entity.MarketingEventType;
import com.teamops.celebration.entity.OccasionAttachment;
import com.teamops.celebration.entity.OccasionCategory;
import com.teamops.celebration.entity.OccasionKind;
import com.teamops.celebration.entity.Region;
import com.teamops.department.dto.DepartmentSummary;
import com.teamops.marketing.email.entity.EmailCampaignStatus;
import com.teamops.user.dto.UserSummary;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** API records for employees, important days, marketing events, LinkedIn posts and calendar occasions. */
public final class CelebrationDtos {

	private CelebrationDtos() {
	}

	/**
	 * LinkedIn post tracking of one occurrence. {@code saved} is false while nobody has decided yet: {@code needed} is
	 * then the default for the kind (yes for important days and events, no for birthdays and anniversaries).
	 */
	public record LinkedInPostDto(boolean needed, boolean saved, LinkedInPostStatus status, UserSummary owner,
			LocalDate postDate, String postLink, Integer version) {

	}

	/**
	 * What the calendar shows about a birthday, anniversary, important day, event or scheduled email campaign.
	 * @param subjectType null for an email campaign (it has no LinkedIn tracking)
	 * @param detail e.g. "5 years at Tritusa" for an anniversary, the designation for a birthday, the location of an
	 * event
	 * @param years completed years for a work anniversary, otherwise null (ages are never shown)
	 */
	public record OccasionInfo(OccasionCategory category, OccasionKind subjectType, Long subjectId, String detail,
			Integer years, ImportantDayCategory dayCategory, Region region, MarketingEventType eventType,
			MarketingEventStatus eventStatus, String location, LinkedInPostDto linkedIn) {

	}

	public record Attachment(Long fileId, String fileName, String contentType, long sizeBytes, UserSummary addedBy,
			Instant addedAt) {

		public static Attachment of(OccasionAttachment attachment) {
			return new Attachment(attachment.getFile().getId(), attachment.getFile().getOriginalName(),
					attachment.getFile().getContentType(), attachment.getFile().getSizeBytes(),
					UserSummary.of(attachment.getAddedBy()), attachment.getAddedAt());
		}

	}

	// --- employees --------------------------------------------------------------------------------------------

	/**
	 * Everyone sees the birthday's day and month; {@code dateOfBirth} (with the year) is only filled for Super Admins
	 * and CELEBRATIONS_EDIT holders.
	 * @param nextAnniversaryYears the years completed on {@code nextAnniversary}
	 */
	public record EmployeeDto(Long id, String employeeCode, String firstName, String lastName, String fullName,
			String designation, String reportingManager, DepartmentSummary department, String workEmail,
			String officeLocation, boolean hasPhoto, UserSummary user, EmployeeStatus status, int birthMonth,
			int birthDay, LocalDate dateOfBirth, LocalDate dateOfJoining, LocalDate nextBirthday,
			LocalDate nextAnniversary, Integer nextAnniversaryYears, Integer version, boolean canEdit) {

	}

	/**
	 * Create or (with {@code version}) update. With {@code userId}, blank names, designation, reporting manager,
	 * department and work email are filled from that app user; after that, names, designation and reporting manager
	 * are required.
	 */
	public record SaveEmployee(@NotBlank @Size(max = 40) String employeeCode, @Size(max = 100) String firstName,
			@Size(max = 100) String lastName, @NotNull LocalDate dateOfBirth, @Size(max = 150) String designation,
			@Size(max = 150) String reportingManager, LocalDate dateOfJoining, Long departmentId,
			@Email @Size(max = 254) String workEmail, @Size(max = 150) String officeLocation, Long userId,
			EmployeeStatus status, Integer version) {

	}

	// --- important days ---------------------------------------------------------------------------------------

	/**
	 * @param nextOccurrence the next date on or after today for a yearly day; the date itself for a one-off day
	 * @param linkedIn post tracking of {@code nextOccurrence}
	 */
	public record ImportantDayDto(Long id, String name, LocalDate date, ImportantDayCategory category, Region region,
			DayRecurrence recurrence, String description, String marketingNotes, LocalDate nextOccurrence,
			long attachmentCount, LinkedInPostDto linkedIn, Integer version, boolean canEdit) {

	}

	/** For a yearly day, {@code date} is its first occurrence; it repeats on that month and day every year after. */
	public record SaveImportantDay(@NotBlank @Size(max = 150) String name, @NotNull LocalDate date,
			@NotNull ImportantDayCategory category, @NotNull Region region, @NotNull DayRecurrence recurrence,
			@Size(max = 2000) String description, @Size(max = 5000) String marketingNotes, Integer version) {

	}

	// --- marketing events -------------------------------------------------------------------------------------

	public record LinkedCampaign(Long id, String name, EmailCampaignStatus status, LocalDate campaignDate) {

	}

	/**
	 * @param startDate the start's business-zone date (the occurrence LinkedIn tracking is keyed on)
	 * @param emailCampaign the linked campaign's details for CAMPAIGN_VIEW holders, otherwise null ({@code emailCampaignId}
	 * is always set when linked)
	 */
	public record MarketingEventDto(Long id, String title, MarketingEventType eventType, OccasionCategory category,
			MarketingEventStatus status, Instant startAt, Instant endAt, LocalDate startDate, String location,
			String onlineLink, String registrationLink, String description, UserSummary owner, Long emailCampaignId,
			LinkedCampaign emailCampaign, long attachmentCount, LinkedInPostDto linkedIn, Integer version,
			boolean canEdit) {

	}

	public record SaveMarketingEvent(@NotBlank @Size(max = 200) String title, @NotNull MarketingEventType eventType,
			MarketingEventStatus status, @NotNull Instant startAt, Instant endAt, @Size(max = 255) String location,
			@Size(max = 500) String onlineLink, @Size(max = 500) String registrationLink,
			@Size(max = 5000) String description, @NotNull Long ownerId, Long emailCampaignId, Integer version) {

	}

	// --- LinkedIn posts ---------------------------------------------------------------------------------------

	/** One upcoming occurrence on the LinkedIn posts list. */
	public record LinkedInPostRow(String key, OccasionKind subjectType, Long subjectId, OccasionCategory category,
			String title, LocalDate occurrenceDate, Region region, LinkedInPostDto linkedIn) {

	}

	/**
	 * Sets the post tracking of one occurrence. {@code version} is required once the occurrence has a saved post (from
	 * {@link LinkedInPostDto#version()}) and must be null before.
	 */
	public record SaveLinkedInPost(@NotNull OccasionKind subjectType, @NotNull Long subjectId,
			@NotNull LocalDate occurrenceDate, @NotNull Boolean postNeeded, Long ownerId,
			@NotNull LinkedInPostStatus status, LocalDate postDate, @Size(max = 500) String postLink, Integer version) {

	}

}
