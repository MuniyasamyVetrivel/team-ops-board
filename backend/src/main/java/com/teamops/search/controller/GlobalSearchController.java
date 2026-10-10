package com.teamops.search.controller;

import java.util.Set;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.teamops.common.security.AuthenticatedUser;
import com.teamops.search.dto.SearchDtos.Group;
import com.teamops.search.dto.SearchDtos.SearchResults;
import com.teamops.search.service.GlobalSearchService;

import lombok.RequiredArgsConstructor;

/**
 * Global search for every signed-in user. No single permission guards it: each group is searched only with that
 * module's permissions (see {@link GlobalSearchService}).
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class GlobalSearchController {

	private final GlobalSearchService globalSearchService;

	/**
	 * @param q at least two characters; shorter queries return no groups
	 * @param group limit the search to these groups (all permitted groups when absent)
	 * @param limit results per group, 1–20
	 */
	@GetMapping
	public SearchResults search(@RequestParam(defaultValue = "") String q,
			@RequestParam(required = false) Set<Group> group,
			@RequestParam(defaultValue = "" + GlobalSearchService.DEFAULT_LIMIT) int limit,
			@AuthenticationPrincipal AuthenticatedUser viewer) {
		return globalSearchService.search(q, group, limit, viewer);
	}

}
