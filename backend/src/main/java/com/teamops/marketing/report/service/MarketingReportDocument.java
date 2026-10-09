package com.teamops.marketing.report.service;

import static com.teamops.common.report.Cells.of;

import java.util.ArrayList;
import java.util.List;

import com.teamops.common.report.ReportDocument;
import com.teamops.common.report.ReportDocument.Section;
import com.teamops.marketing.report.dto.MarketingReportDtos.KeywordMove;
import com.teamops.marketing.report.dto.MarketingReportDtos.MonthlyReport;

/** The monthly report as a {@link ReportDocument}: one table per group, the keyword movements and the targets. */
public final class MarketingReportDocument {

	private MarketingReportDocument() {
	}

	public static ReportDocument document(MonthlyReport r) {
		String now = r.period().label();
		String before = r.comparisonPeriod().label();
		String subtitle = now + " against " + before + (r.ownerId() == null ? " · all owners" : " · one owner")
				+ (r.frozen() ? " · frozen " + r.generatedAt() : "");
		List<Section> sections = new ArrayList<>();
		for (var group : r.groups()) {
			sections.add(new Section(group.title(), List.of("Measure", now, before, "Change", "Change %"),
					group.lines().stream().map(l -> List.of(l.label() + (l.unit().name().equals("PERCENT") ? " (%)" : ""),
							of(l.current()), of(l.previous()), of(l.change()), of(l.changePct()))).toList()));
		}
		if (r.keywordMovements() != null) {
			sections.add(moves("Keywords that improved most", r.keywordMovements().improved(), now, before));
			sections.add(moves("Keywords that declined most", r.keywordMovements().declined(), now, before));
		}
		if (r.targets() != null) {
			sections.add(new Section("Target achievement",
					List.of("Target type", "Unit", "Target", "Actual", "Achievement %", "Remaining", "Status",
							before + " target", before + " actual", before + " achievement %"),
					r.targets().stream().map(t -> List.of(t.type(), t.unit(), of(t.targetValue()), of(t.actual()),
							of(t.achievementPct()), of(t.remaining()), of(t.status()), of(t.previousTargetValue()),
							of(t.previousActual()), of(t.previousAchievementPct()))).toList()));
		}
		String file = "marketing-report-%d-%02d".formatted(r.period().year(), r.period().month());
		return new ReportDocument("Digital Marketing monthly report", subtitle, file, sections);
	}

	private static Section moves(String title, List<KeywordMove> moves, String now, String before) {
		return new Section(title, List.of("Keyword", "Page", before + " position", now + " position", "Change"),
				moves.stream().map(m -> List.of(m.keyword(), of(m.page()), position(m.previousPosition()),
						position(m.position()), of(m.change()))).toList());
	}

	/** "NR" for Not Ranked, as the SEO pages show it. */
	private static String position(Integer position) {
		return position == null ? "NR" : position.toString();
	}

}
