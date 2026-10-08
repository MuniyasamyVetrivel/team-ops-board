package com.teamops.devdata;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.announcement.entity.Announcement;
import com.teamops.announcement.entity.AnnouncementPriority;
import com.teamops.announcement.repository.AnnouncementRepository;
import com.teamops.approval.entity.Approval;
import com.teamops.approval.entity.ApprovalType;
import com.teamops.approval.repository.ApprovalRepository;
import com.teamops.approval.repository.ApprovalTypeRepository;
import com.teamops.approval.service.ApprovalEngine;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.sequence.CodeGenerator;
import com.teamops.common.storage.FileStorageService;
import com.teamops.common.storage.StoredFile;
import com.teamops.common.storage.StoredFileRepository;
import com.teamops.department.entity.Department;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.document.entity.Document;
import com.teamops.document.entity.DocumentVersion;
import com.teamops.document.repository.DocumentRepository;
import com.teamops.document.repository.DocumentVersionRepository;
import com.teamops.knowledge.entity.ArticleStatus;
import com.teamops.knowledge.entity.KnowledgeArticle;
import com.teamops.knowledge.entity.KnowledgeCategory;
import com.teamops.knowledge.repository.KnowledgeArticleRepository;
import com.teamops.knowledge.repository.KnowledgeCategoryRepository;
import com.teamops.knowledge.service.KnowledgeText;
import com.teamops.project.entity.MilestoneStatus;
import com.teamops.project.entity.Project;
import com.teamops.project.entity.ProjectMilestone;
import com.teamops.project.entity.ProjectRisk;
import com.teamops.project.entity.RiskLevel;
import com.teamops.project.repository.ProjectMilestoneRepository;
import com.teamops.project.repository.ProjectRepository;
import com.teamops.project.repository.ProjectRiskRepository;
import com.teamops.tag.TagService;
import com.teamops.user.entity.Role;
import com.teamops.user.entity.RoleCodes;
import com.teamops.user.entity.User;
import com.teamops.user.repository.RoleRepository;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Development data for Phase 8: project milestones, risks and members; approvals in every state; announcements;
 * knowledge base articles; and versioned documents. Each part runs only while its table is empty, and dates are
 * relative to today.
 */
@Component
@ConditionalOnBooleanProperty(name = "app.dev-seed.enabled")
@RequiredArgsConstructor
class DevCollaborationSeeder {

	private final ProjectRepository projectRepository;

	private final ProjectMilestoneRepository milestoneRepository;

	private final ProjectRiskRepository riskRepository;

	private final ApprovalRepository approvalRepository;

	private final ApprovalTypeRepository typeRepository;

	private final AnnouncementRepository announcementRepository;

	private final KnowledgeArticleRepository articleRepository;

	private final KnowledgeCategoryRepository categoryRepository;

	private final DocumentRepository documentRepository;

	private final DocumentVersionRepository versionRepository;

	private final StoredFileRepository fileRepository;

	private final FileStorageService storage;

	private final DepartmentRepository departmentRepository;

	private final UserRepository userRepository;

	private final RoleRepository roleRepository;

	private final TagService tagService;

	private final CodeGenerator codeGenerator;

	private final BusinessCalendar calendar;

	private final JdbcTemplate jdbc;

	@Transactional
	public String seed() {
		Map<String, User> users = userRepository.findAll()
			.stream()
			.collect(Collectors.toMap(u -> u.getEmail().toLowerCase(), Function.identity()));
		Map<String, Department> departments = departmentRepository.findAll()
			.stream()
			.collect(Collectors.toMap(Department::getCode, Function.identity()));
		int projects = seedProjects(users);
		linkCompletedTasks();
		int approvals = seedApprovals(users);
		int announcements = seedAnnouncements(users, departments);
		int articles = seedArticles(users);
		int documents = seedDocuments(users, departments);
		return "%d project details, %d approvals, %d announcements, %d articles, %d documents".formatted(projects,
				approvals, announcements, articles, documents);
	}

	private int seedProjects(Map<String, User> users) {
		if (milestoneRepository.count() > 0) {
			return 0;
		}
		LocalDate today = calendar.today();
		int count = 0;
		for (Project project : projectRepository.findAll()) {
			milestone(project, "Discovery and plan", today.minusDays(14), MilestoneStatus.COMPLETED, 1);
			milestone(project, "First release", today.plusDays(4), MilestoneStatus.IN_PROGRESS, 2);
			milestone(project, "Go-live", today.plusDays(30), MilestoneStatus.PLANNED, 3);
			risk(project, "Key people unavailable during festive season", RiskLevel.MEDIUM, RiskLevel.HIGH,
					"Plan cover and front-load reviews");
			risk(project, "Scope creep from stakeholder requests", RiskLevel.HIGH, RiskLevel.MEDIUM,
					"Weekly change review with the owner");
			userRepository.findByDepartmentIdOrderByFirstNameAscLastNameAsc(project.getDepartment().getId())
				.stream()
				.filter(User::isActive)
				.forEach(user -> project.getMembers().add(user));
			count++;
		}
		return count;
	}

	/**
	 * Gives each seeded project some finished work, so its task-based progress is realistic: links up to four of the
	 * department's completed tasks when the project has none. Idempotent.
	 */
	private void linkCompletedTasks() {
		for (Project project : projectRepository.findAll()) {
			Long done = jdbc.queryForObject(
					"select count(*) from tasks where project_id = ? and status = 'COMPLETED'", Long.class,
					project.getId());
			if (done != null && done == 0) {
				jdbc.update("""
						update tasks set project_id = ? where id in (select id from (
						  select id from tasks where department_id = ? and status = 'COMPLETED' and project_id is null
						  order by completed_at desc limit 4) picked)
						""", project.getId(), project.getDepartment().getId());
			}
		}
	}

	private void milestone(Project project, String name, LocalDate due, MilestoneStatus status, int position) {
		ProjectMilestone milestone = new ProjectMilestone();
		milestone.setProject(project);
		milestone.setName(name);
		milestone.setDueDate(due);
		milestone.setStatus(status);
		milestone.setPosition(position);
		if (status == MilestoneStatus.COMPLETED) {
			milestone.setCompletedAt(calendar.startOf(due));
		}
		milestoneRepository.save(milestone);
	}

	private void risk(Project project, String title, RiskLevel probability, RiskLevel impact, String mitigation) {
		ProjectRisk risk = new ProjectRisk();
		risk.setProject(project);
		risk.setTitle(title);
		risk.setProbability(probability);
		risk.setImpact(impact);
		risk.setMitigation(mitigation);
		risk.setOwner(project.getOwner());
		riskRepository.save(risk);
	}

	private int seedApprovals(Map<String, User> users) {
		if (approvalRepository.count() > 0) {
			return 0;
		}
		Map<String, ApprovalType> types = typeRepository.findAllByOrderByNameAsc()
			.stream()
			.collect(Collectors.toMap(ApprovalType::getCode, Function.identity()));
		Role superAdmin = roleRepository.findByCode(RoleCodes.SUPER_ADMIN).orElseThrow();
		LocalDate today = calendar.today();
		int count = 0;
		count += approval(users, types, superAdmin, "karthik.raj", "PURCHASE", "External monitor for design reviews",
				new BigDecimal("14500"), today.plusDays(5), null);
		count += approval(users, types, superAdmin, "arun.kumar", "ACCESS", "Access to Google Search Console",
				null, today.plusDays(2), null);
		count += approval(users, types, superAdmin, "priya.menon", "MARKETING_CREATIVE",
				"Diwali LinkedIn ad creatives", null, today.plusDays(3), null);
		count += approval(users, types, superAdmin, "deepak.nair", "SOFTWARE", "Burp Suite licence", null, null,
				"sanjay-approves");
		count += approval(users, types, superAdmin, "meena.sekar", "EXPENSE", "Team offsite travel",
				new BigDecimal("8200"), null, "reject");
		return count;
	}

	/** {@code outcome}: null = left pending; "reject" = rejected by the manager; otherwise fully approved. */
	private int approval(Map<String, User> users, Map<String, ApprovalType> types, Role superAdmin, String requester,
			String typeCode, String title, BigDecimal amount, LocalDate due, String outcome) {
		User user = users.get(requester + "@teamops.local");
		ApprovalType type = types.get(typeCode);
		if (user == null || type == null) {
			return 0;
		}
		Instant now = calendar.now();
		Approval approval = new Approval();
		approval.setCode(codeGenerator.next(CodeGenerator.APPROVAL));
		approval.setType(type);
		approval.setTitle(title);
		approval.setDescription("Development seed request.");
		approval.setRequester(user);
		approval.setDepartment(user.getDepartment());
		approval.setAmount(amount);
		approval.setDueDate(due);
		approval.getSteps()
			.addAll(ApprovalEngine.materialize(approval, type.getSteps(), user.getDepartment().getManager(), superAdmin));
		ApprovalEngine.advance(approval, now);
		if (outcome != null) {
			// Decide as whoever each step names (or the first Super Admin for role steps).
			User rakesh = users.get("rakesh@teamops.local");
			while (approval.pendingStep().isPresent()) {
				var step = approval.pendingStep().get();
				User approver = step.getApprover() != null ? step.getApprover() : rakesh;
				AuthenticatedUser actor = new AuthenticatedUser(approver.getId(), approver.getEmail(),
						approver.getFullName(), approver.getDepartment().getId(),
						approver.getRoles().stream().map(Role::getCode).collect(Collectors.toSet()),
						Set.of("APPROVAL_VIEW", "APPROVAL_DECIDE"));
				boolean reject = outcome.equals("reject");
				ApprovalEngine.decide(approval, actor, approver, !reject, reject ? "Over the quarterly budget" : "Approved",
						now);
			}
		}
		approvalRepository.save(approval);
		return 1;
	}

	private int seedAnnouncements(Map<String, User> users, Map<String, Department> departments) {
		if (announcementRepository.count() > 0) {
			return 0;
		}
		Instant now = calendar.now();
		User rakesh = users.get("rakesh@teamops.local");
		announcement("Updated leave policy for 2027",
				"The leave policy has been updated for 2027. Please read the new carry-over rules in the HR section of the knowledge base and acknowledge.",
				null, AnnouncementPriority.IMPORTANT, now.minus(Duration.ofDays(1)), null, true, rakesh);
		announcement("Office closed for Diwali",
				"The office will be closed on the festival days. Urgent IT issues: raise a ticket marked Urgent.", null,
				AnnouncementPriority.NORMAL, now.minus(Duration.ofHours(5)), now.plus(Duration.ofDays(20)), false,
				rakesh);
		announcement("VPN maintenance on Saturday",
				"The VPN will be unavailable on Saturday from 10:00 to 12:00 IST while we upgrade the firewall.",
				departments.get("IT"), AnnouncementPriority.URGENT, now.minus(Duration.ofHours(2)), null, false,
				users.get("suresh.babu@teamops.local"));
		announcement("Quarterly town hall agenda",
				"Agenda for next week's town hall will be shared here.", null, AnnouncementPriority.NORMAL,
				now.plus(Duration.ofDays(2)), null, false, rakesh);
		return (int) announcementRepository.count();
	}

	private void announcement(String title, String body, Department target, AnnouncementPriority priority,
			Instant publishAt, Instant expiresAt, boolean ack, User createdBy) {
		Announcement announcement = new Announcement();
		announcement.setTitle(title);
		announcement.setBody(body);
		announcement.setTargetDepartment(target);
		announcement.setPriority(priority);
		announcement.setPublishAt(publishAt);
		announcement.setExpiresAt(expiresAt);
		announcement.setAckRequired(ack);
		announcement.setCreatedBy(createdBy);
		announcementRepository.save(announcement);
	}

	private int seedArticles(Map<String, User> users) {
		if (articleRepository.count() > 0) {
			return 0;
		}
		Map<String, KnowledgeCategory> categories = categoryRepository.findAll()
			.stream()
			.collect(Collectors.toMap(KnowledgeCategory::getSlug, Function.identity()));
		User suresh = users.get("suresh.babu@teamops.local");
		User lakshmi = users.get("lakshmi.priya@teamops.local");
		User anitha = users.get("anitha.raj@teamops.local");
		User priya = users.get("priya.menon@teamops.local");
		article("Connect to the office VPN", categories.get("it"), suresh, List.of("vpn", "remote-work"), """
				## Before you start
				You need your company account and the **TeamOps VPN** profile.

				## Steps
				1. Install the VPN client from the software centre.
				2. Import the profile shared by IT.
				3. Sign in with your company email and approve the MFA prompt.

				> Still stuck? Raise a ticket in the **Network** category.
				""");
		article("Reset your password", categories.get("it"), suresh, List.of("password", "account"), """
				Use the self-service portal to reset your password. Passwords must be at least 12 characters and
				are checked against known breaches. After resetting, sign out of every device and sign back in.
				""");
		article("Leave policy and carry-over rules", categories.get("hr"), lakshmi, List.of("leave", "policy"), """
				## Annual leave
				Everyone gets 18 days of annual leave a year. Up to **5 days** can be carried over to the next year.

				## Applying for leave
				Apply at least two weeks ahead for leave longer than three days.
				""");
		article("Report a phishing email", categories.get("security"), anitha, List.of("phishing", "security"), """
				Do not click links or open attachments. Forward the email to the security team and raise a
				**Security Incident** ticket. If you already clicked a link, change your password immediately.
				""");
		article("Brand colours and logo usage", categories.get("marketing"), priya, List.of("brand"), """
				Use the primary indigo for calls to action. Keep clear space around the logo equal to the height
				of the "T". Never stretch or recolour the logo.
				""");
		article("How to raise a good help desk ticket", categories.get("faq"), suresh, List.of("tickets"), """
				Pick the right category so the ticket reaches the right team, describe what you expected and what
				happened, and attach a screenshot. Choose **Urgent** only when work is blocked.
				""");
		return (int) articleRepository.count();
	}

	private void article(String title, KnowledgeCategory category, User author, List<String> tags, String body) {
		if (category == null) {
			return;
		}
		KnowledgeArticle article = new KnowledgeArticle();
		article.setTitle(title);
		article.setSlug(KnowledgeText.slugify(title));
		article.setBody(body.strip());
		article.setCategory(category);
		article.setAuthor(author);
		article.setStatus(ArticleStatus.PUBLISHED);
		article.setPublishedAt(calendar.now().minus(Duration.ofDays(3)));
		article.setViewCount(title.length() % 23);
		article.setTags(new java.util.HashSet<>(tagService.resolve(tags)));
		articleRepository.save(article);
	}

	private int seedDocuments(Map<String, User> users, Map<String, Department> departments) {
		if (documentRepository.count() > 0) {
			return 0;
		}
		User rakesh = users.get("rakesh@teamops.local");
		User lakshmi = users.get("lakshmi.priya@teamops.local");
		User suresh = users.get("suresh.babu@teamops.local");
		document("Employee handbook", "Company-wide policies and conduct.", null, rakesh,
				List.of("Employee handbook v1", "Employee handbook v2: updated remote-work section"));
		document("Leave application form", "Template for leave requests.", departments.get("HR"), lakshmi,
				List.of("Leave form"));
		document("Network diagram", "Office network layout.", departments.get("IT"), suresh,
				List.of("Network diagram v1", "Network diagram v2", "Network diagram v3: new Wi-Fi access points"));
		return (int) documentRepository.count();
	}

	private void document(String name, String description, Department department, User uploader,
			List<String> versions) {
		Document document = new Document();
		document.setName(name);
		document.setDescription(description);
		document.setDepartment(department);
		document.setUploadedBy(uploader);
		document.setCurrentVersionNo(versions.size());
		Document saved = documentRepository.save(document);
		for (int i = 0; i < versions.size(); i++) {
			DocumentVersion version = new DocumentVersion();
			version.setDocument(saved);
			version.setVersionNo(i + 1);
			version.setFile(storeText(name.toLowerCase().replace(' ', '-') + "-v" + (i + 1) + ".txt", versions.get(i),
					uploader));
			version.setChangeNote(i == 0 ? "First version" : versions.get(i));
			version.setUploadedBy(uploader);
			versionRepository.save(version);
		}
	}

	private StoredFile storeText(String fileName, String text, User uploader) {
		byte[] bytes = (text + "\n\nDevelopment seed document.\n").getBytes(StandardCharsets.UTF_8);
		try {
			StoredFile file = new StoredFile();
			file.setStorageProvider(storage.provider());
			file.setStorageKey(storage.put(new ByteArrayInputStream(bytes), "txt"));
			file.setOriginalName(fileName);
			file.setContentType("text/plain");
			file.setSizeBytes(bytes.length);
			file.setChecksumSha256(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
			file.setUploadedBy(uploader == null ? null : uploader.getId());
			return fileRepository.save(file);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
