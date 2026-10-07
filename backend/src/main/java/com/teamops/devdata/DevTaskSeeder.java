package com.teamops.devdata;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.sequence.CodeGenerator;
import com.teamops.department.entity.Department;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.project.entity.Project;
import com.teamops.project.entity.ProjectStatus;
import com.teamops.project.repository.ProjectRepository;
import com.teamops.tag.TagService;
import com.teamops.task.entity.Task;
import com.teamops.task.entity.TaskChecklistItem;
import com.teamops.task.entity.TaskComment;
import com.teamops.task.entity.TaskPriority;
import com.teamops.task.entity.TaskStatus;
import com.teamops.task.repository.TaskChecklistItemRepository;
import com.teamops.task.repository.TaskCommentRepository;
import com.teamops.task.repository.TaskRepository;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Development projects and tasks. Runs only when the tasks table is empty. Dates are relative to "today" so the
 * overdue / due today / upcoming views and the workload page always have realistic content, and workloads are
 * deliberately varied (some people overloaded, some light).
 */
@Component
@ConditionalOnBooleanProperty(name = "app.dev-seed.enabled")
@RequiredArgsConstructor
class DevTaskSeeder {

	/** email -> {active tasks, estimated hours per task}. Everyone else gets 3 tasks of 6 h (LOW). */
	static final Map<String, int[]> LOAD = Map.of("karthik.raj@teamops.local", new int[] { 8, 12 },
			"priya.menon@teamops.local", new int[] { 7, 13 }, "deepak.nair@teamops.local", new int[] { 9, 10 },
			"arun.kumar@teamops.local", new int[] { 6, 11 }, "sanjay.varma@teamops.local", new int[] { 6, 10 },
			"vignesh.raman@teamops.local", new int[] { 5, 9 }, "kavya.suresh@teamops.local", new int[] { 4, 10 },
			"manoj.thomas@teamops.local", new int[] { 1, 4 });

	/** Due-date offsets from today, cycled per task: overdue, today, this week, later, and no date. */
	private static final Integer[] DUE_OFFSETS = { -4, 0, 2, -1, 5, 9, 1, null, 12, 3 };

	private static final TaskStatus[] STATUSES = { TaskStatus.IN_PROGRESS, TaskStatus.TODO, TaskStatus.TODO,
			TaskStatus.BLOCKED, TaskStatus.IN_REVIEW, TaskStatus.IN_PROGRESS, TaskStatus.TODO, TaskStatus.TODO,
			TaskStatus.IN_PROGRESS, TaskStatus.TODO };

	private static final TaskPriority[] PRIORITIES = { TaskPriority.HIGH, TaskPriority.MEDIUM, TaskPriority.URGENT,
			TaskPriority.LOW, TaskPriority.MEDIUM, TaskPriority.HIGH, TaskPriority.MEDIUM, TaskPriority.LOW };

	static final Map<String, List<String>> TITLES = Map.of(
			"IT", List.of("Patch Windows servers", "Renew SSL certificates", "Set up laptops for new joiners",
					"Migrate file share to SharePoint", "Audit VPN accounts", "Replace office Wi-Fi access points",
					"Document backup restore procedure", "Upgrade firewall firmware", "Review M365 licences",
					"Clean up stale AD accounts", "Inventory network switches", "Printer fleet maintenance"),
			"CYBERSEC", List.of("Quarterly vulnerability scan", "Phishing simulation for Q4",
					"Review firewall rule changes", "ISO 27001 risk register update",
					"Incident response tabletop exercise", "Endpoint protection rollout",
					"Access review for finance systems", "Security awareness training deck",
					"Pen test remediation follow-up", "SIEM alert tuning", "Vendor security questionnaire",
					"MFA enforcement for admins"),
			"HR", List.of("Onboarding plan for November joiners", "Update leave policy", "Performance review calendar",
					"Employee engagement survey", "Exit interview summary", "Health insurance renewal",
					"Policy handbook refresh", "Training needs analysis", "Holiday calendar 2027",
					"Payroll inputs for October", "Wellness week planning", "Manager feedback workshop"),
			"TA", List.of("Screen QA engineer applicants", "Schedule interviews for React developer",
					"Post SEO executive job", "Campus hiring plan", "Offer letter for data analyst",
					"Refresh careers page content", "Referral bonus communication",
					"Background verification follow-ups", "Interview panel training", "Hiring pipeline report",
					"Job description for DevOps engineer", "Agency contract renewal"),
			"WEBDEV", List.of("Homepage hero redesign", "Fix contact form validation",
					"Improve Core Web Vitals on services pages", "Build case study template",
					"Migrate blog to new CMS", "Accessibility audit fixes", "Add schema markup to service pages",
					"Set up staging environment", "Optimise image delivery", "Footer navigation update",
					"Cookie consent banner", "404 page redesign"),
			"APPDEV", List.of("Login flow for mobile app", "Push notification service", "Fix crash on Android 14",
					"Offline sync for timesheets", "App store screenshots", "Upgrade Flutter SDK", "Biometric login",
					"API pagination for app lists", "Dark mode support", "Beta release checklist",
					"Crash reporting setup", "Deep links for notifications"),
			"DM", List.of("Update October keyword rankings", "Write SAP testing blog post",
					"LinkedIn campaign creatives brief", "Monthly backlink outreach", "Email newsletter for November",
					"Competitor analysis report", "Landing page for S/4HANA testing",
					"Google Search Console review", "Content calendar for Q4", "Lead report for October",
					"Refresh meta descriptions", "Webinar promotion plan"),
			"PRESALES", List.of("RFP response for retail client", "Demo environment refresh",
					"Solution deck for SAP testing", "Effort estimate for mobile app bid",
					"Client workshop preparation", "Case study for banking client", "Proposal template update",
					"Pricing model review", "Competitive battlecard", "Discovery call notes",
					"Reference call scheduling", "Bid/no-bid review"),
			"GRAPHICS", List.of("LinkedIn ad creatives", "Brochure for testing services", "Event banner design",
					"Infographic for blog", "Website illustration set", "Social media templates",
					"Video editing for product demo", "Icon set refresh", "Email header graphics",
					"Presentation template refresh", "Trade show booth artwork", "Animated logo"),
			"PAYROLL", List.of("October payroll processing", "TDS computation review", "Reimbursement batch",
					"Salary revision letters", "PF and ESI filings", "Payslip template update",
					"Year-end tax declarations reminder", "Full and final settlements", "Payroll variance report",
					"Bonus payout calculation", "Gratuity provision review", "Attendance data reconciliation"));

	private final TaskRepository taskRepository;

	private final TaskChecklistItemRepository checklistRepository;

	private final TaskCommentRepository commentRepository;

	private final ProjectRepository projectRepository;

	private final DepartmentRepository departmentRepository;

	private final UserRepository userRepository;

	private final TagService tagService;

	private final CodeGenerator codeGenerator;

	private final BusinessCalendar calendar;

	/** Returns the number of tasks created (0 when tasks already exist). */
	@Transactional
	public int seedIfEmpty() {
		if (taskRepository.count() > 0) {
			return 0;
		}
		LocalDate today = calendar.today();
		Instant now = calendar.now();
		Map<String, User> users = new HashMap<>();
		DevDataSeeder.USERS.forEach(seed -> userRepository.findByEmailIgnoreCase(seed.email())
			.ifPresent(user -> users.put(seed.email(), user)));
		Map<String, Department> departments = new HashMap<>();
		departmentRepository.findAll().forEach(d -> departments.put(d.getCode(), d));

		Map<String, Project> projects = Map.of(
				"WEBDEV", project("Website Revamp", departments.get("WEBDEV"),
						users.get("sanjay.varma@teamops.local"), today.minusDays(30), today.plusDays(45)),
				"DM", project("SAP Testing Services Campaign", departments.get("DM"),
						users.get("priya.menon@teamops.local"), today.minusDays(20), today.plusDays(60)),
				"CYBERSEC", project("ISO 27001 Readiness", departments.get("CYBERSEC"),
						users.get("anitha.raj@teamops.local"), today.minusDays(45), today.plusDays(90)));

		Map<String, Integer> titleCursor = new HashMap<>();
		int created = 0;
		int sequence = 0;
		for (DevDataSeeder.SeedUser seed : DevDataSeeder.USERS) {
			User user = users.get(seed.email());
			if (user == null || seed.reportsToEmail() == null) {
				continue; // Rakesh has no task load of his own
			}
			User manager = users.getOrDefault(seed.reportsToEmail(), user);
			Department department = departments.get(seed.departmentCode());
			Project project = projects.get(seed.departmentCode());
			int[] load = LOAD.getOrDefault(seed.email(), new int[] { 3, 6 });

			for (int i = 0; i < load[0]; i++, sequence++) {
				Integer offset = DUE_OFFSETS[sequence % DUE_OFFSETS.length];
				TaskStatus status = STATUSES[sequence % STATUSES.length];
				Task task = task(nextTitle(seed.departmentCode(), titleCursor), department, user, manager,
						PRIORITIES[sequence % PRIORITIES.length], status, offset == null ? null : today.plusDays(offset),
						BigDecimal.valueOf(load[1]), i % 2 == 0 ? project : null);
				if (status == TaskStatus.IN_PROGRESS) {
					task.setActualHours(BigDecimal.valueOf(2));
				}
				task.setTags(new HashSet<>(tagService.resolve(tagsFor(seed.departmentCode(), i))));
				if (task.getPriority() == TaskPriority.URGENT && manager != user) {
					task.getWatchers().add(manager);
				}
				taskRepository.save(task);
				if (i == 0) {
					checklist(task, user, List.of("Agree scope", "Draft", "Review with manager", "Publish"), 2, now);
				}
				if (offset != null && offset < 0) {
					comment(task, manager, "This is overdue now. Can you share an update and a new date?");
					comment(task, user, "Waiting on input from another team. I will update today.");
				}
				created++;
			}
			// Completed work over the last five weeks (feeds "completed" counts and the weekly chart in Phase 6).
			int completed = 2 + (sequence % 4);
			for (int i = 0; i < completed; i++, sequence++) {
				Instant completedAt = now.minus(Duration.ofDays(1 + (i * 6L) + (sequence % 4)));
				Task task = task(nextTitle(seed.departmentCode(), titleCursor), department, user, manager,
						PRIORITIES[sequence % PRIORITIES.length], TaskStatus.COMPLETED,
						LocalDate.ofInstant(completedAt, calendar.zone()).plusDays(1), BigDecimal.valueOf(5), null);
				task.setActualHours(BigDecimal.valueOf(4 + (sequence % 3)));
				task.setCompletedAt(completedAt);
				taskRepository.save(task);
				created++;
			}
		}

		// Unassigned backlog, so managers have something to hand out.
		for (String code : List.of("WEBDEV", "DM", "IT")) {
			User manager = departments.get(code).getManager();
			Task task = task(nextTitle(code, titleCursor), departments.get(code), null, manager, TaskPriority.MEDIUM,
					TaskStatus.TODO, today.plusDays(6), BigDecimal.valueOf(4), projects.get(code));
			taskRepository.save(task);
			created++;
		}
		return created;
	}

	private Project project(String name, Department department, User owner, LocalDate start, LocalDate end) {
		Project project = new Project();
		project.setCode(codeGenerator.next(CodeGenerator.PROJECT));
		project.setName(name);
		project.setDepartment(department);
		project.setOwner(owner);
		project.setStartDate(start);
		project.setEndDate(end);
		project.setStatus(ProjectStatus.ACTIVE);
		return projectRepository.save(project);
	}

	private Task task(String title, Department department, User assignee, User createdBy, TaskPriority priority,
			TaskStatus status, LocalDate dueDate, BigDecimal estimate, Project project) {
		Task task = new Task();
		task.setCode(codeGenerator.next(CodeGenerator.TASK));
		task.setTitle(title);
		task.setDescription("Development seed task for " + department.getName() + ".");
		task.setDepartment(department);
		task.setAssignee(assignee);
		task.setCreatedBy(createdBy);
		task.setPriority(priority);
		task.setStatus(status);
		task.setStartDate(dueDate == null ? null : dueDate.minusDays(7));
		task.setDueDate(dueDate);
		task.setEstimatedHours(estimate);
		task.setProject(project);
		return task;
	}

	private void checklist(Task task, User user, List<String> items, int done, Instant now) {
		for (int i = 0; i < items.size(); i++) {
			TaskChecklistItem item = new TaskChecklistItem();
			item.setTask(task);
			item.setContent(items.get(i));
			item.setPosition(i);
			if (i < done) {
				item.setDone(true);
				item.setDoneBy(user);
				item.setDoneAt(now.minus(Duration.ofDays(done - i)));
			}
			checklistRepository.save(item);
		}
	}

	private void comment(Task task, User author, String body) {
		TaskComment comment = new TaskComment();
		comment.setTask(task);
		comment.setAuthor(author);
		comment.setBody(body);
		commentRepository.save(comment);
	}

	private static String nextTitle(String departmentCode, Map<String, Integer> cursor) {
		List<String> titles = TITLES.get(departmentCode);
		int index = cursor.merge(departmentCode, 1, Integer::sum) - 1;
		String title = titles.get(index % titles.size());
		return index < titles.size() ? title : title + " (" + (index / titles.size() + 1) + ")";
	}

	private static List<String> tagsFor(String departmentCode, int index) {
		Set<String> tags = new HashSet<>();
		tags.add(departmentCode.toLowerCase(Locale.ROOT));
		if (index % 3 == 0) {
			tags.add("client");
		}
		if (index % 4 == 1) {
			tags.add("documentation");
		}
		return List.copyOf(tags);
	}

}
