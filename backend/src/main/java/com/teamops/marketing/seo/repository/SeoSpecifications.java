package com.teamops.marketing.seo.repository;

import java.util.Locale;
import java.util.Set;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import com.teamops.marketing.seo.entity.Device;
import com.teamops.marketing.seo.entity.KeywordStatus;
import com.teamops.marketing.seo.entity.PageStatus;
import com.teamops.marketing.seo.entity.PageType;
import com.teamops.marketing.seo.entity.SeoKeyword;
import com.teamops.marketing.seo.entity.SeoPage;

/** Composable filters for SEO pages and keywords. */
public final class SeoSpecifications {

	private SeoSpecifications() {
	}

	// --- pages ----------------------------------------------------------------------------------------------

	public static Specification<SeoPage> pageMatches(String search) {
		if (!StringUtils.hasText(search)) {
			return all();
		}
		String pattern = likePattern(search);
		return (root, query, cb) -> cb.or(cb.like(cb.lower(root.get("title")), pattern, '\\'),
				cb.like(cb.lower(root.get("url")), pattern, '\\'),
				cb.like(cb.lower(root.get("primaryKeyword")), pattern, '\\'));
	}

	public static Specification<SeoPage> pageTypeIn(Set<PageType> types) {
		return types == null || types.isEmpty() ? all() : (root, query, cb) -> root.get("pageType").in(types);
	}

	public static Specification<SeoPage> pageStatusIn(Set<PageStatus> statuses) {
		return statuses == null || statuses.isEmpty() ? all() : (root, query, cb) -> root.get("status").in(statuses);
	}

	public static Specification<SeoPage> pageOwner(Long ownerId) {
		return ownerId == null ? all() : (root, query, cb) -> cb.equal(root.get("owner").get("id"), ownerId);
	}

	public static Specification<SeoPage> pageDepartment(Long departmentId) {
		return departmentId == null ? all()
				: (root, query, cb) -> cb.equal(root.get("department").get("id"), departmentId);
	}

	// --- keywords -------------------------------------------------------------------------------------------

	public static Specification<SeoKeyword> keywordMatches(String search) {
		if (!StringUtils.hasText(search)) {
			return all();
		}
		String pattern = likePattern(search);
		return (root, query, cb) -> cb.like(cb.lower(root.get("keyword")), pattern, '\\');
	}

	public static Specification<SeoKeyword> keywordPage(Long pageId) {
		return pageId == null ? all() : (root, query, cb) -> cb.equal(root.get("page").get("id"), pageId);
	}

	public static Specification<SeoKeyword> keywordOwner(Long ownerId) {
		return ownerId == null ? all() : (root, query, cb) -> cb.equal(root.get("owner").get("id"), ownerId);
	}

	public static Specification<SeoKeyword> keywordStatusIn(Set<KeywordStatus> statuses) {
		return statuses == null || statuses.isEmpty() ? all() : (root, query, cb) -> root.get("status").in(statuses);
	}

	public static Specification<SeoKeyword> keywordDevice(Device device) {
		return device == null ? all() : (root, query, cb) -> cb.equal(root.get("device"), device);
	}

	private static <T> Specification<T> all() {
		return (root, query, cb) -> cb.conjunction();
	}

	private static String likePattern(String search) {
		return "%" + search.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
				+ "%";
	}

}
