package com.teamops.report.service;

import static com.teamops.common.report.Cells.of;

import java.time.LocalDate;
import java.util.List;

import com.teamops.common.report.ReportDocument;
import com.teamops.common.report.ReportDocument.Section;
import com.teamops.report.dto.ReportDtos.ProjectReport;
import com.teamops.report.dto.ReportDtos.Range;
import com.teamops.report.dto.ReportDtos.TaskReport;
import com.teamops.report.dto.ReportDtos.TaskSummary;
import com.teamops.report.dto.ReportDtos.TicketReport;
import com.teamops.report.dto.ReportDtos.TicketSummary;
import com.teamops.report.dto.ReportDtos.WorkloadReport;

/** The management reports as {@link ReportDocument}s, ready for any {@link com.teamops.common.report.ReportExporter}. */
public final class ReportDocuments {

	private ReportDocuments() {
	}

	public static ReportDocument tasks(TaskReport r) {
		TaskSummary s = r.summary();
		return new ReportDocument("Task report", subtitle(r.range()), "task-report-" + r.range().from() + "-to-" + r.range().to(),
				List.of(new Section("Summary", List.of("Measure", "Value"),
						List.of(List.of("Tasks created", of(s.created())), List.of("Tasks completed", of(s.completed())),
								List.of("Completed with a due date", of(s.completedWithDueDate())),
								List.of("Completed on time", of(s.completedOnTime())), List.of("On time %", of(s.onTimePct())),
								List.of("Open now", of(s.open())), List.of("Overdue now", of(s.overdue())),
								List.of("Overdue %", of(s.overduePct())), List.of("Hours logged on completed tasks", of(s.hoursLogged())))),
						new Section("Open tasks by status", List.of("Status", "Tasks"),
								r.openByStatus().stream().map(c -> List.of(of(c.status()), of(c.tasks()))).toList()),
						new Section("Department performance",
								List.of("Department", "Created", "Completed", "On time %", "Open", "Overdue", "Overdue %"),
								r.departments().stream().map(d -> List.of(d.department().name(), of(d.created()), of(d.completed()),
										of(d.onTimePct()), of(d.open()), of(d.overdue()), of(d.overduePct()))).toList()),
						new Section("Employee productivity",
								List.of("Employee", "Department", "Completed", "On time %", "Open", "Overdue", "Hours logged"),
								r.employees().stream().map(e -> List.of(e.user().fullName(), e.department().name(), of(e.completed()),
										of(e.onTimePct()), of(e.open()), of(e.overdue()), of(e.hoursLogged()))).toList()),
						new Section("Weekly trend", List.of("Week starting", "Created", "Completed"),
								r.trend().stream().map(w -> List.of(of(w.weekStart()), of(w.created()), of(w.completed()))).toList())));
	}

	public static ReportDocument workload(WorkloadReport r, LocalDate today) {
		return new ReportDocument("Workload report",
				"Window: " + r.employees().windowDays() + " days from " + today + " · unestimated tasks count as "
						+ of(r.employees().defaultTaskHours()) + " h",
				"workload-report-" + today,
				List.of(new Section("Department workload",
						List.of("Department", "People", "Active tasks", "Overdue", "Remaining hours", "Capacity hours", "Workload %", "Level"),
						r.departments().stream().map(d -> List.of(d.department().name(), of(d.people()), of(d.activeTasks()),
								of(d.overdue()), of(d.remainingHours()), of(d.capacityHours()), of(d.workloadPercent()), of(d.level()))).toList()),
						new Section("Employee workload",
								List.of("Employee", "Department", "Active tasks", "Overdue", "Due today", "Remaining hours", "Capacity hours", "Workload %", "Level"),
								r.employees().rows().stream().map(e -> List.of(e.user().fullName(), e.department().name(), of(e.activeTasks()),
										of(e.overdue()), of(e.dueToday()), of(e.remainingHours()), of(e.capacityHours()),
										of(e.workloadPercent()), of(e.level()))).toList())));
	}

	public static ReportDocument tickets(TicketReport r) {
		TicketSummary s = r.summary();
		return new ReportDocument("Ticket report", subtitle(r.range()), "ticket-report-" + r.range().from() + "-to-" + r.range().to(),
				List.of(new Section("Summary", List.of("Measure", "Value"),
						List.of(List.of("Tickets created", of(s.created())), List.of("Tickets resolved", of(s.resolved())),
								List.of("Open now", of(s.open())), List.of("Open with a breached SLA", of(s.openBreached())),
								List.of("First response compliance %", of(s.firstResponseCompliance())),
								List.of("Resolution compliance %", of(s.resolutionCompliance())),
								List.of("Average resolution hours", of(s.averageResolutionHours())))),
						new Section("By priority",
								List.of("Priority", "Created", "Resolved", "Open", "First response compliance %", "Resolution compliance %"),
								r.priorities().stream().map(p -> List.of(of(p.priority()), of(p.created()), of(p.resolved()), of(p.open()),
										of(p.firstResponseCompliance()), of(p.resolutionCompliance()))).toList()),
						new Section("By department",
								List.of("Department", "Created", "Resolved", "Open", "Open breached", "Resolution compliance %"),
								r.departments().stream().map(d -> List.of(d.department().name(), of(d.created()), of(d.resolved()),
										of(d.open()), of(d.openBreached()), of(d.resolutionCompliance()))).toList()),
						new Section("Open ticket ageing", List.of("Age", "Tickets"),
								r.ageing().stream().map(a -> List.of(a.label(), of(a.tickets()))).toList()),
						new Section("Open tickets by status", List.of("Status", "Tickets"),
								r.openByStatus().stream().map(c -> List.of(of(c.status()), of(c.tickets()))).toList())));
	}

	public static ReportDocument projects(ProjectReport r) {
		return new ReportDocument("Project progress report", "As of " + r.today(), "project-report-" + r.today(),
				List.of(new Section("Projects by status", List.of("Status", "Projects"),
						r.byStatus().stream().map(c -> List.of(of(c.status()), of(c.projects()))).toList()),
						new Section("Projects",
								List.of("Code", "Project", "Status", "Department", "Owner", "Start", "End", "Progress %", "Tasks",
										"Tasks completed", "Tasks overdue", "Milestones", "Milestones completed", "Milestones overdue",
										"Open risks", "Past end date"),
								r.projects().stream().map(p -> List.of(p.code(), p.name(), of(p.status()),
										p.department() == null ? "" : p.department().name(), p.owner() == null ? "" : p.owner().fullName(),
										of(p.startDate()), of(p.endDate()), of(p.progress()), of(p.tasks()), of(p.tasksCompleted()),
										of(p.tasksOverdue()), of(p.milestones()), of(p.milestonesCompleted()), of(p.milestonesOverdue()),
										of(p.openRisks()), of(p.pastEndDate()))).toList())));
	}

	private static String subtitle(Range range) {
		return range.from() + " to " + range.to();
	}

}
