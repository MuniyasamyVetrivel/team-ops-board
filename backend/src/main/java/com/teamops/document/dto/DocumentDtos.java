package com.teamops.document.dto;

import java.time.Instant;
import java.util.List;

import com.teamops.department.dto.DepartmentSummary;
import com.teamops.document.entity.DocumentVersion;
import com.teamops.project.dto.ProjectRef;
import com.teamops.user.dto.UserSummary;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Document API records. */
public final class DocumentDtos {

	private DocumentDtos() {
	}

	public record VersionResponse(int versionNo, String fileName, String contentType, long sizeBytes,
			String changeNote, UserSummary uploadedBy, Instant uploadedAt) {

		public static VersionResponse of(DocumentVersion version) {
			return version == null ? null
					: new VersionResponse(version.getVersionNo(), version.getFile().getOriginalName(),
							version.getFile().getContentType(), version.getFile().getSizeBytes(),
							version.getChangeNote(), UserSummary.of(version.getUploadedBy()), version.getCreatedAt());
		}

	}

	/** {@code department = null} is company-wide. */
	public record DocumentItem(Long id, String name, String description, DepartmentSummary department,
			ProjectRef project, UserSummary uploadedBy, VersionResponse current, int versionCount, Instant createdAt,
			Instant updatedAt, boolean canEdit) {

	}

	public record DocumentDetail(Long id, String name, String description, DepartmentSummary department,
			ProjectRef project, UserSummary uploadedBy, List<VersionResponse> versions, Instant createdAt,
			Instant updatedAt, Integer version, boolean canEdit) {

	}

	public record UpdateDocument(@NotNull(message = "Version is required") Integer version,
			@NotBlank(message = "Name is required") @Size(max = 200) String name,
			@Size(max = 5000) String description) {

	}

}
