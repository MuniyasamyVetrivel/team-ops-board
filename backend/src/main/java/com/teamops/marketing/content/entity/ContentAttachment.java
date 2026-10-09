package com.teamops.marketing.content.entity;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

import org.hibernate.annotations.CreationTimestamp;

import com.teamops.common.storage.StoredFile;
import com.teamops.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A file attached to a content item (brief section 64). */
@Getter
@Setter
@Entity
@Table(name = "content_item_attachments")
public class ContentAttachment {

	@EmbeddedId
	private Id id;

	@MapsId("contentItemId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "content_item_id")
	private ContentItem contentItem;

	@MapsId("fileId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "file_id")
	private StoredFile file;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "added_by")
	private User addedBy;

	@CreationTimestamp
	@Column(name = "added_at", nullable = false, updatable = false)
	private Instant addedAt;

	public static ContentAttachment of(ContentItem item, StoredFile file, User addedBy) {
		ContentAttachment attachment = new ContentAttachment();
		attachment.setId(new Id(item.getId(), file.getId()));
		attachment.setContentItem(item);
		attachment.setFile(file);
		attachment.setAddedBy(addedBy);
		return attachment;
	}

	@Getter
	@Embeddable
	@NoArgsConstructor
	public static class Id implements Serializable {

		@Column(name = "content_item_id")
		private Long contentItemId;

		@Column(name = "file_id")
		private Long fileId;

		public Id(Long contentItemId, Long fileId) {
			this.contentItemId = contentItemId;
			this.fileId = fileId;
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof Id that && Objects.equals(contentItemId, that.contentItemId)
					&& Objects.equals(fileId, that.fileId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(contentItemId, fileId);
		}

	}

}
