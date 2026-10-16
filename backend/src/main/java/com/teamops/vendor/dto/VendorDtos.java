package com.teamops.vendor.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.teamops.user.dto.UserSummary;
import com.teamops.vendor.entity.Vendor;
import com.teamops.vendor.entity.VendorCategory;
import com.teamops.vendor.entity.VendorContact;
import com.teamops.vendor.entity.VendorContractDocument;
import com.teamops.vendor.entity.VendorStatus;
import com.teamops.vendor.service.ContractRules.ContractStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** API records for Vendor Management: vendors, contacts, contracts and contract documents. */
public final class VendorDtos {

	public static final int MAX_CONTACTS = 20;

	private VendorDtos() {
	}

	// --- vendors ----------------------------------------------------------------------------------------------

	/** A vendor as other records name it (contracts now; licences and expenses in Phase X8). */
	public record VendorRef(Long id, String name, VendorStatus status) {

		public static VendorRef of(Vendor vendor) {
			return vendor == null ? null : new VendorRef(vendor.getId(), vendor.getName(), vendor.getStatus());
		}

	}

	public record ContactDto(Long id, String name, String role, String email, String phone) {

		public static ContactDto of(VendorContact contact) {
			return new ContactDto(contact.getId(), contact.getName(), contact.getRole(), contact.getEmail(),
					contact.getPhone());
		}

	}

	/**
	 * @param contractCount live contracts with this vendor
	 * @param nextRenewal the earliest date one of its contracts not yet expired comes up for renewal; never stored
	 */
	public record VendorDto(Long id, String name, String taxId, VendorCategory category, String country,
			String address, String website, String paymentTerms, VendorStatus status, Integer rating, String notes,
			List<ContactDto> contacts, long contractCount, LocalDate nextRenewal, Integer version, Instant createdAt,
			Instant updatedAt) {

	}

	/** @param id an existing contact of this vendor to change; null for a new one */
	public record SaveContact(Long id, @NotBlank @Size(max = 150) String name, @Size(max = 100) String role,
			@Email @Size(max = 254) String email, @Size(max = 50) String phone) {

	}

	/**
	 * Contacts replace the vendor's list (order kept; the first is the main contact). {@code version} is required for
	 * an update (409 STALE_UPDATE when someone else saved first).
	 */
	public record SaveVendor(@NotBlank @Size(max = 200) String name, @Size(max = 50) String taxId,
			VendorCategory category, @Size(max = 100) String country, @Size(max = 500) String address,
			@Size(max = 300) String website, @Size(max = 100) String paymentTerms, VendorStatus status,
			@Min(1) @Max(5) Integer rating, @Size(max = 5000) String notes,
			@Size(max = MAX_CONTACTS) List<@NotNull @Valid SaveContact> contacts, Integer version) {

	}

	// --- contracts --------------------------------------------------------------------------------------------

	public record DocumentDto(Long fileId, String fileName, String contentType, long sizeBytes, UserSummary addedBy,
			Instant addedAt) {

		public static DocumentDto of(VendorContractDocument document) {
			return new DocumentDto(document.getFile().getId(), document.getFile().getOriginalName(),
					document.getFile().getContentType(), document.getFile().getSizeBytes(),
					UserSummary.of(document.getAddedBy()), document.getAddedAt());
		}

	}

	/**
	 * Computed on today's business date, never stored:
	 * @param status upcoming, active or expired, from the dates
	 * @param renewsOn the renewal date, or else the end date
	 * @param daysUntilRenewal days to {@code renewsOn} while that is today or later and the contract has not expired
	 * @param noticeBy the last day to give notice: {@code renewsOn} less the notice period
	 * @param valueHidden the viewer may not see finance amounts (FINANCE_VIEW), so {@code value} is null
	 */
	public record ContractDto(Long id, VendorRef vendor, String title, LocalDate startDate, LocalDate endDate,
			BigDecimal value, boolean valueHidden, String currency, LocalDate renewalDate, boolean autoRenew, Integer noticePeriodDays,
			String notes, ContractStatus status, LocalDate renewsOn, Long daysUntilRenewal, LocalDate noticeBy,
			List<DocumentDto> documents, Integer version, Instant createdAt, Instant updatedAt) {

	}

	/**
	 * {@code currency} defaults to INR; {@code version} is required for an update. {@code value} is ignored on an
	 * update by someone without FINANCE_VIEW (the stored value stays).
	 */
	public record SaveContract(@NotNull Long vendorId, @NotBlank @Size(max = 200) String title,
			@NotNull LocalDate startDate, LocalDate endDate,
			@DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal value,
			@Pattern(regexp = "[A-Za-z]{3}", message = "must be a three-letter currency code") String currency,
			LocalDate renewalDate, Boolean autoRenew, @Min(0) @Max(730) Integer noticePeriodDays,
			@Size(max = 5000) String notes, Integer version) {

	}

}
