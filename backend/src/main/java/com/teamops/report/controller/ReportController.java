package com.teamops.report.controller;

import java.time.LocalDate;
import java.util.Set;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.teamops.common.report.ReportExporters;
import com.teamops.common.report.ReportFormat;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.project.entity.ProjectStatus;
import com.teamops.report.dto.ReportDtos.ProjectReport;
import com.teamops.report.dto.ReportDtos.TaskReport;
import com.teamops.report.dto.ReportDtos.TicketReport;
import com.teamops.report.dto.ReportDtos.WorkloadReport;
import com.teamops.report.service.ReportDocuments;
import com.teamops.report.service.ReportService;
import com.teamops.task.entity.TaskStatus;

import lombok.RequiredArgsConstructor;

/**
 * Management reports: REPORT_VIEW reads, REPORT_EXPORT downloads ({@code /export?format=CSV}; PDF is not available
 * yet). Each report also needs the view permission of the module it summarises, and stays within the viewer's
 * scope. Dates are business days ({@code from}/{@code to}, default the last 30 days).
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

	private final ReportService reportService;

	private final ReportExporters exporters;

	@GetMapping("/tasks")
	@PreAuthorize("hasAuthority('REPORT_VIEW') and hasAuthority('TASK_VIEW')")
	public TaskReport tasks(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Long departmentId, @RequestParam(required = false) Long userId,
			@RequestParam(required = false) Long projectId, @RequestParam(required = false) Set<TaskStatus> status,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return reportService.tasks(reportService.range(from, to), departmentId, userId, projectId, status, actor);
	}

	@GetMapping("/tasks/export")
	@PreAuthorize("hasAuthority('REPORT_EXPORT') and hasAuthority('TASK_VIEW')")
	public ResponseEntity<byte[]> exportTasks(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Long departmentId, @RequestParam(required = false) Long userId,
			@RequestParam(required = false) Long projectId, @RequestParam(required = false) Set<TaskStatus> status,
			@RequestParam(defaultValue = "CSV") ReportFormat format, @AuthenticationPrincipal AuthenticatedUser actor) {
		return exporters.download(ReportDocuments.tasks(tasks(from, to, departmentId, userId, projectId, status, actor)), format);
	}

	@GetMapping("/workload")
	@PreAuthorize("hasAuthority('REPORT_VIEW') and hasAuthority('WORKLOAD_VIEW')")
	public WorkloadReport workload(@RequestParam(required = false) Long departmentId,
			@RequestParam(required = false) Long userId, @AuthenticationPrincipal AuthenticatedUser actor) {
		return reportService.workload(departmentId, userId, actor);
	}

	@GetMapping("/workload/export")
	@PreAuthorize("hasAuthority('REPORT_EXPORT') and hasAuthority('WORKLOAD_VIEW')")
	public ResponseEntity<byte[]> exportWorkload(@RequestParam(required = false) Long departmentId,
			@RequestParam(required = false) Long userId, @RequestParam(defaultValue = "CSV") ReportFormat format,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		WorkloadReport report = workload(departmentId, userId, actor);
		return exporters.download(ReportDocuments.workload(report, report.employees().today()), format);
	}

	@GetMapping("/tickets")
	@PreAuthorize("hasAuthority('REPORT_VIEW') and hasAuthority('TICKET_VIEW')")
	public TicketReport tickets(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Long departmentId, @RequestParam(required = false) Long assigneeId,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return reportService.tickets(reportService.range(from, to), departmentId, assigneeId, actor);
	}

	@GetMapping("/tickets/export")
	@PreAuthorize("hasAuthority('REPORT_EXPORT') and hasAuthority('TICKET_VIEW')")
	public ResponseEntity<byte[]> exportTickets(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Long departmentId, @RequestParam(required = false) Long assigneeId,
			@RequestParam(defaultValue = "CSV") ReportFormat format, @AuthenticationPrincipal AuthenticatedUser actor) {
		return exporters.download(ReportDocuments.tickets(tickets(from, to, departmentId, assigneeId, actor)), format);
	}

	@GetMapping("/projects")
	@PreAuthorize("hasAuthority('REPORT_VIEW') and hasAuthority('PROJECT_VIEW')")
	public ProjectReport projects(@RequestParam(required = false) Long departmentId,
			@RequestParam(required = false) Set<ProjectStatus> status, @AuthenticationPrincipal AuthenticatedUser actor) {
		return reportService.projects(departmentId, status, actor);
	}

	@GetMapping("/projects/export")
	@PreAuthorize("hasAuthority('REPORT_EXPORT') and hasAuthority('PROJECT_VIEW')")
	public ResponseEntity<byte[]> exportProjects(@RequestParam(required = false) Long departmentId,
			@RequestParam(required = false) Set<ProjectStatus> status, @RequestParam(defaultValue = "CSV") ReportFormat format,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return exporters.download(ReportDocuments.projects(projects(departmentId, status, actor)), format);
	}

}
