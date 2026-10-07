package com.teamops.common.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

import com.teamops.common.exception.ApiException;

/**
 * Builds a {@link Pageable} from {@code page}, {@code size} and {@code sort=field,asc|desc} query parameters. Sort
 * fields are whitelisted per endpoint and mapped to entity paths, so clients cannot sort by arbitrary columns.
 */
public final class PageRequests {

	public static final int DEFAULT_SIZE = 20;

	public static final int MAX_SIZE = 100;

	private PageRequests() {
	}

	/**
	 * @param sortFields public sort key to one or more entity property paths
	 * @param defaultSort used when {@code sort} is blank, in the same {@code field,direction} format
	 */
	public static Pageable of(int page, int size, String sort, Map<String, List<String>> sortFields,
			String defaultSort) {
		int safePage = Math.max(page, 0);
		int safeSize = size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
		return PageRequest.of(safePage, safeSize, toSort(StringUtils.hasText(sort) ? sort : defaultSort, sortFields));
	}

	static Sort toSort(String sort, Map<String, List<String>> sortFields) {
		String[] parts = sort.split(",");
		String key = parts[0].trim();
		List<String> paths = sortFields.get(key);
		if (paths == null) {
			throw ApiException.badRequest("INVALID_SORT",
					"Unsupported sort field '" + key + "'. Allowed: " + sortFields.keySet());
		}
		Sort.Direction direction = Sort.Direction.ASC;
		if (parts.length > 1) {
			String value = parts[1].trim().toUpperCase(Locale.ROOT);
			if (!value.equals("ASC") && !value.equals("DESC")) {
				throw ApiException.badRequest("INVALID_SORT", "Sort direction must be asc or desc");
			}
			direction = Sort.Direction.valueOf(value);
		}
		List<Sort.Order> orders = new ArrayList<>();
		for (String path : paths) {
			orders.add(new Sort.Order(direction, path));
		}
		return Sort.by(orders);
	}

}
