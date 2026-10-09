package com.teamops.marketing.report.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.teamops.common.report.ReportExporters;
import com.teamops.common.report.ReportFormat;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.marketing.report.dto.MarketingReportDtos.FrozenMonth;
import com.teamops.marketing.report.dto.MarketingReportDtos.MonthlyReport;
import com.teamops.marketing.report.service.MarketingReportDocument;
import com.teamops.marketing.report.service.MarketingReportService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * The Digital Marketing monthly report: MARKETING_VIEW reads and exports it (each part needs its module's view
 * permission), MARKETING_EDIT freezes an ended month. Month/year default to the current business month; {@code live}
 * recomputes a frozen month.
 */
@RestController
@RequestMapping("/api/marketing/reports")
@RequiredArgsConstructor
public class MarketingReportController {

	private final MarketingReportService reportService;

	private final ReportExporters exporters;

	@GetMapping("/monthly")
	@PreAuthorize("hasAuthority('MARKETING_VIEW')")
	public MonthlyReport monthly(@RequestParam(required = false) Integer month, @RequestParam(required = false) Integer year,
			@RequestParam(required = false) Long ownerId, @RequestParam(defaultValue = "false") boolean live,
			@AuthenticationPrincipal AuthenticatedUser viewer) {
		return reportService.report(reportService.period(month, year), ownerId, live, viewer);
	}

	@GetMapping("/monthly/export")
	@PreAuthorize("hasAuthority('MARKETING_VIEW')")
	public ResponseEntity<byte[]> export(@RequestParam(required = false) Integer month,
			@RequestParam(required = false) Integer year, @RequestParam(required = false) Long ownerId,
			@RequestParam(defaultValue = "false") boolean live, @RequestParam(defaultValue = "CSV") ReportFormat format,
			@AuthenticationPrincipal AuthenticatedUser viewer) {
		return exporters.download(MarketingReportDocument.document(monthly(month, year, ownerId, live, viewer)), format);
	}

	@GetMapping("/frozen")
	@PreAuthorize("hasAuthority('MARKETING_VIEW')")
	public List<FrozenMonth> frozen() {
		return reportService.frozenMonths();
	}

	@PostMapping("/monthly/{year}/{month}/freeze")
	@PreAuthorize("hasAuthority('MARKETING_EDIT')")
	public MonthlyReport freeze(@PathVariable int year, @PathVariable int month,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return reportService.freeze(reportService.period(month, year), actor, ClientInfo.from(http));
	}

}
