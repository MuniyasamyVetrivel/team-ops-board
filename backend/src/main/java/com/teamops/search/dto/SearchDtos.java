package com.teamops.search.dto;

import java.util.List;

public final class SearchDtos {

	private SearchDtos() {
	}

	/** What a result is; the UI links each kind to its page. */
	public enum ResultType {

		TASK, TICKET, PROJECT, EMPLOYEE, ARTICLE, MARKETING_PAGE, KEYWORD, EMAIL_CAMPAIGN, PAID_CAMPAIGN, LEAD

	}

	/** The groups in the order they are shown. */
	public enum Group {

		TASKS("Tasks"), TICKETS("Tickets"), PROJECTS("Projects"), EMPLOYEES("Employees"),
		KNOWLEDGE_BASE("Knowledge Base"), MARKETING_PAGES("Marketing Pages"), KEYWORDS("Keywords"),
		CAMPAIGNS("Campaigns"), LEADS("Leads");

		private final String label;

		Group(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}

	}

	/**
	 * One match. {@code ref} is the key the result's page uses when it is not the id (an article's slug).
	 * {@code status} is the record's status code, shown next to its label.
	 */
	public record Hit(ResultType type, Long id, String code, String title, String subtitle, String status,
			String ref) {

	}

	/** {@code total} counts every match the viewer may see; {@code hits} holds the first few. */
	public record GroupResult(Group group, String label, long total, List<Hit> hits) {

	}

	/** Only the groups the viewer may search, and only those with matches. */
	public record SearchResults(String query, List<GroupResult> groups) {

	}

}
