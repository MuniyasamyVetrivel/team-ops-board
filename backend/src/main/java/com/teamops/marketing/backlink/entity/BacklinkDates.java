package com.teamops.marketing.backlink.entity;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** The date each stage was reached; null for a stage the backlink has not reached. */
public record BacklinkDates(LocalDate submitted, LocalDate approved, LocalDate live, LocalDate rejected,
		LocalDate lost) {

	public static final BacklinkDates NONE = new BacklinkDates(null, null, null, null, null);

	/** The dates by API field name, in stage order (nulls included). */
	public Map<String, LocalDate> byField() {
		Map<String, LocalDate> fields = new LinkedHashMap<>();
		fields.put("submittedDate", submitted);
		fields.put("approvedDate", approved);
		fields.put("liveDate", live);
		fields.put("rejectedDate", rejected);
		fields.put("lostDate", lost);
		return fields;
	}

}
