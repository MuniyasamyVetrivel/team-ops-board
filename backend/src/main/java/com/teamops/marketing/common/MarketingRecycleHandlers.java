package com.teamops.marketing.common;

import java.time.Month;
import java.time.format.TextStyle;
import java.util.Locale;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.teamops.common.exception.ApiException;
import com.teamops.common.storage.FileService;
import com.teamops.marketing.backlink.entity.Backlink;
import com.teamops.marketing.backlink.repository.BacklinkRepository;
import com.teamops.marketing.content.entity.ContentAttachment;
import com.teamops.marketing.content.entity.ContentItem;
import com.teamops.marketing.content.repository.ContentAttachmentRepository;
import com.teamops.marketing.content.repository.ContentItemRepository;
import com.teamops.marketing.seo.entity.SeoKeyword;
import com.teamops.marketing.seo.entity.SeoPage;
import com.teamops.marketing.target.entity.MarketingTarget;
import com.teamops.marketing.target.entity.TargetType;
import com.teamops.recyclebin.service.RecycleBinHandler;
import com.teamops.recyclebin.service.RecycleBinService;

/**
 * Digital Marketing records in the recycle bin:
 * <ul>
 * <li>content keeps its attachments and files until the permanent delete;</li>
 * <li>a backlink or content item comes back only if no live record took its link or URL meanwhile;</li>
 * <li>a keyword or monthly target comes back only after the page or target type it belongs to.</li>
 * </ul>
 */
@Configuration
class MarketingRecycleHandlers {

	@Bean
	RecycleBinHandler<ContentItem> contentRecycleHandler(ContentItemRepository contentRepository,
			ContentAttachmentRepository attachmentRepository, FileService fileService) {
		return new RecycleBinHandler<>() {

			@Override
			public Class<ContentItem> type() {
				return ContentItem.class;
			}

			@Override
			public void checkRestore(ContentItem item) {
				if (item.getUrl() != null && contentRepository.existsUrl(item.getUrl(), item.getId())) {
					throw ApiException.conflict("RESTORE_CONFLICT",
							"Another content item now has the URL " + item.getUrl() + ". Change that item first.");
				}
			}

			@Override
			public void beforePurge(ContentItem item) {
				for (ContentAttachment attachment : attachmentRepository.findByContentItemIdOrderByAddedAtAsc(item.getId())) {
					attachmentRepository.delete(attachment);
					fileService.delete(attachment.getFile());
				}
			}

		};
	}

	@Bean
	RecycleBinHandler<Backlink> backlinkRecycleHandler(BacklinkRepository backlinkRepository) {
		return new RecycleBinHandler<>() {

			@Override
			public Class<Backlink> type() {
				return Backlink.class;
			}

			@Override
			public void checkRestore(Backlink backlink) {
				if (backlink.getLinkUrl() != null
						&& backlinkRepository.existsDuplicate(backlink.getLinkUrl(), backlink.getTargetUrl(), backlink.getId())) {
					throw ApiException.conflict("RESTORE_CONFLICT",
							"Another backlink now records this link to " + backlink.getTargetUrl() + ".");
				}
			}

		};
	}

	@Bean
	RecycleBinHandler<SeoKeyword> keywordRecycleHandler(RecycleBinService recycleBin) {
		return new RecycleBinHandler<>() {

			@Override
			public Class<SeoKeyword> type() {
				return SeoKeyword.class;
			}

			/** A keyword belongs to its page's department. */
			@Override
			public Long departmentOf(SeoKeyword keyword) {
				return keyword.getPage().getDepartment() == null ? null : keyword.getPage().getDepartment().getId();
			}

			@Override
			public void checkRestore(SeoKeyword keyword) {
				if (recycleBin.isInBin(SeoPage.class, keyword.getPage().getId())) {
					throw ApiException.conflict("PARENT_IN_RECYCLE_BIN",
							"This keyword's page is in the recycle bin. Restore the page first.");
				}
			}

		};
	}

	@Bean
	RecycleBinHandler<MarketingTarget> targetRecycleHandler(RecycleBinService recycleBin) {
		return new RecycleBinHandler<>() {

			@Override
			public Class<MarketingTarget> type() {
				return MarketingTarget.class;
			}

			/** "Leads target, October 2026": a target has no name of its own. */
			@Override
			public String label(MarketingTarget target) {
				String month = Month.of(target.getMonth()).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
				return target.getType().getName() + " target, " + month + " " + target.getYear();
			}

			@Override
			public void checkRestore(MarketingTarget target) {
				if (recycleBin.isInBin(TargetType.class, target.getType().getId())) {
					throw ApiException.conflict("PARENT_IN_RECYCLE_BIN",
							"This target's type is in the recycle bin. Restore the type first.");
				}
			}

		};
	}

}
