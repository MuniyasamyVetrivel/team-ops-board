package com.teamops.document.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditService;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AccessScope;
import com.teamops.common.security.AccessScopeService;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.storage.FileService;
import com.teamops.common.storage.StoredFile;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageResponse;
import com.teamops.department.dto.DepartmentSummary;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.document.dto.DocumentDtos.DocumentDetail;
import com.teamops.document.dto.DocumentDtos.DocumentItem;
import com.teamops.document.dto.DocumentDtos.UpdateDocument;
import com.teamops.document.dto.DocumentDtos.VersionResponse;
import com.teamops.document.entity.Document;
import com.teamops.document.entity.DocumentVersion;
import com.teamops.document.repository.DocumentRepository;
import com.teamops.document.repository.DocumentVersionRepository;
import com.teamops.project.dto.ProjectRef;
import com.teamops.project.entity.Project;
import com.teamops.project.repository.ProjectRepository;
import com.teamops.project.service.ProjectAccess;
import com.teamops.user.dto.UserSummary;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Versioned documents (brief section 17). Visible: company-wide documents, documents of the viewer's own or managed
 * departments, and their own uploads. Changing a document needs DOCUMENT_EDIT plus management of its department or
 * being the uploader; company-wide documents are for Super Admins. Uploads go through {@link FileService}.
 */
@Service
@RequiredArgsConstructor
public class DocumentService {

	static final String DOCUMENT_EDIT = "DOCUMENT_EDIT";

	private final DocumentRepository documentRepository;

	private final DocumentVersionRepository versionRepository;

	private final DepartmentRepository departmentRepository;

	private final ProjectRepository projectRepository;

	private final UserRepository userRepository;

	private final AccessScopeService accessScopeService;

	private final FileService fileService;

	private final AuditService auditService;

	@Transactional(readOnly = true)
	public PageResponse<DocumentItem> search(String search, Long departmentId, Long projectId, Pageable pageable,
			AuthenticatedUser actor) {
		AccessScope scope = accessScopeService.scopeFor(actor);
		var spec = DocumentRepository.visibleTo(actor.id(), departmentsOf(actor, scope))
			.and(DocumentRepository.matches(search))
			.and(DocumentRepository.inDepartment(departmentId))
			.and(DocumentRepository.inProject(projectId));
		var page = documentRepository.findAll(spec, pageable);
		Map<Long, DocumentVersion> current = page.getContent().isEmpty() ? Map.of()
				: documentRepository.findCurrentVersions(page.getContent().stream().map(Document::getId).toList())
					.stream()
					.collect(Collectors.toMap(v -> v.getDocument().getId(), Function.identity()));
		return PageResponse.of(page.map(document -> new DocumentItem(document.getId(), document.getName(),
				document.getDescription(), department(document), ProjectRef.of(document.getProject()),
				UserSummary.of(document.getUploadedBy()), VersionResponse.of(current.get(document.getId())),
				document.getCurrentVersionNo(), document.getCreatedAt(), document.getUpdatedAt(),
				canEdit(document, actor, scope))));
	}

	@Transactional(readOnly = true)
	public DocumentDetail get(Long id, AuthenticatedUser actor) {
		AccessScope scope = accessScopeService.scopeFor(actor);
		return toDetail(loadVisible(id, actor, scope), actor, scope);
	}

	@Transactional
	public DocumentDetail upload(MultipartFile file, String name, String description, Long departmentId,
			Long projectId, AuthenticatedUser actor, ClientInfo client) {
		AccessScope scope = accessScopeService.scopeFor(actor);
		if (departmentId == null ? !scope.isAll()
				: !(scope.coversDepartment(departmentId) || departmentId.equals(actor.departmentId()))) {
			throw ApiException.forbidden("FORBIDDEN", departmentId == null
					? "Only a Super Admin can add company-wide documents" : "You cannot add documents to that department");
		}
		Document document = new Document();
		document.setName(StringUtils.hasText(name) ? name.trim() : defaultName(file));
		if (document.getName().length() > 200) {
			throw ApiException.badRequest("INVALID_NAME", "The name can be at most 200 characters");
		}
		document.setDescription(StringUtils.hasText(description) ? description.trim() : null);
		document.setDepartment(departmentId == null ? null
				: departmentRepository.findById(departmentId)
					.orElseThrow(() -> ApiException.badRequest("UNKNOWN_DEPARTMENT", "Department not found")));
		document.setProject(resolveProject(projectId, actor, scope));
		document.setUploadedBy(userRepository.getReferenceById(actor.id()));
		Document saved = documentRepository.save(document);
		addVersion(saved, file, null, 1, actor);
		auditService.record(AuditAction.DOCUMENT_UPLOADED, actor.id(), "DOCUMENT", saved.getId(),
				Map.of("name", saved.getName(), "version", 1), client);
		return toDetail(saved, actor, scope);
	}

	@Transactional
	public DocumentDetail addVersion(Long id, MultipartFile file, String changeNote, AuthenticatedUser actor,
			ClientInfo client) {
		AccessScope scope = accessScopeService.scopeFor(actor);
		Document document = editable(id, actor, scope);
		int next = document.getCurrentVersionNo() + 1;
		addVersion(document, file, changeNote, next, actor);
		document.setCurrentVersionNo(next);
		documentRepository.flush();
		auditService.record(AuditAction.DOCUMENT_UPLOADED, actor.id(), "DOCUMENT", document.getId(),
				Map.of("name", document.getName(), "version", next), client);
		return toDetail(document, actor, scope);
	}

	@Transactional
	public DocumentDetail update(Long id, UpdateDocument request, AuthenticatedUser actor) {
		AccessScope scope = accessScopeService.scopeFor(actor);
		Document document = editable(id, actor, scope);
		if (!Objects.equals(document.getVersion(), request.version())) {
			throw ApiException.conflict("STALE_UPDATE", "Someone else changed this document just now. Reload and try again.");
		}
		document.setName(request.name().trim());
		document.setDescription(StringUtils.hasText(request.description()) ? request.description().trim() : null);
		documentRepository.flush();
		return toDetail(document, actor, scope);
	}

	/** Deletes the document and every version's file. */
	@Transactional
	public void delete(Long id, AuthenticatedUser actor, ClientInfo client) {
		Document document = editable(id, actor, accessScopeService.scopeFor(actor));
		List<DocumentVersion> versions = documentRepository.findVersions(id);
		versionRepository.deleteAll(versions);
		versionRepository.flush();
		versions.forEach(version -> fileService.delete(version.getFile()));
		documentRepository.delete(document);
		auditService.record(AuditAction.DOCUMENT_DELETED, actor.id(), "DOCUMENT", id,
				Map.of("name", document.getName(), "versions", versions.size()), client);
	}

	@Transactional(readOnly = true)
	public Download download(Long id, int versionNo, AuthenticatedUser actor) {
		loadVisible(id, actor, accessScopeService.scopeFor(actor));
		StoredFile file = versionRepository.findByDocumentIdAndVersionNo(id, versionNo)
			.orElseThrow(() -> ApiException.notFound("VERSION_NOT_FOUND", "Version not found"))
			.getFile();
		return new Download(file.getOriginalName(), file.getContentType(), file.getSizeBytes(), fileService.content(file));
	}

	public record Download(String fileName, String contentType, long sizeBytes, Resource content) {
	}

	// --- helpers --------------------------------------------------------------------------------------------

	/** Own and managed departments; {@code null} = everything (Super Admin). */
	static Set<Long> departmentsOf(AuthenticatedUser actor, AccessScope scope) {
		if (scope.isAll()) {
			return null;
		}
		Set<Long> ids = new HashSet<>(scope.departmentIds());
		ids.add(actor.departmentId());
		return ids;
	}

	public static boolean canView(Document document, AuthenticatedUser actor, AccessScope scope) {
		if (scope.isAll() || document.getDepartment() == null || document.isUploadedBy(actor.id())) {
			return true;
		}
		Long departmentId = document.getDepartment().getId();
		return departmentId.equals(actor.departmentId()) || scope.coversDepartment(departmentId);
	}

	public static boolean canEdit(Document document, AuthenticatedUser actor, AccessScope scope) {
		if (!actor.hasPermission(DOCUMENT_EDIT)) {
			return false;
		}
		if (scope.isAll() || document.isUploadedBy(actor.id())) {
			return true;
		}
		return document.getDepartment() != null && scope.coversDepartment(document.getDepartment().getId());
	}

	private Document loadVisible(Long id, AuthenticatedUser actor, AccessScope scope) {
		return documentRepository.findDetailedById(id)
			.filter(d -> canView(d, actor, scope))
			.orElseThrow(() -> ApiException.notFound("DOCUMENT_NOT_FOUND", "Document not found"));
	}

	private Document editable(Long id, AuthenticatedUser actor, AccessScope scope) {
		Document document = loadVisible(id, actor, scope);
		if (!canEdit(document, actor, scope)) {
			throw ApiException.forbidden("FORBIDDEN", "You do not have permission to change this document");
		}
		return document;
	}

	private void addVersion(Document document, MultipartFile upload, String changeNote, int versionNo,
			AuthenticatedUser actor) {
		if (changeNote != null && changeNote.length() > 500) {
			throw ApiException.badRequest("INVALID_NOTE", "The change note can be at most 500 characters");
		}
		StoredFile file = fileService.store(upload, actor.id());
		DocumentVersion version = new DocumentVersion();
		version.setDocument(document);
		version.setVersionNo(versionNo);
		version.setFile(file);
		version.setChangeNote(StringUtils.hasText(changeNote) ? changeNote.trim() : null);
		version.setUploadedBy(userRepository.getReferenceById(actor.id()));
		versionRepository.save(version);
	}

	private Project resolveProject(Long projectId, AuthenticatedUser actor, AccessScope scope) {
		if (projectId == null) {
			return null;
		}
		return projectRepository.findDetailedById(projectId)
			.filter(project -> new ProjectAccess(actor, scope).canView(project))
			.orElseThrow(() -> ApiException.badRequest("UNKNOWN_PROJECT", "Project not found"));
	}

	private DocumentDetail toDetail(Document document, AuthenticatedUser actor, AccessScope scope) {
		return new DocumentDetail(document.getId(), document.getName(), document.getDescription(),
				department(document), ProjectRef.of(document.getProject()), UserSummary.of(document.getUploadedBy()),
				documentRepository.findVersions(document.getId()).stream().map(VersionResponse::of).toList(),
				document.getCreatedAt(), document.getUpdatedAt(), document.getVersion(),
				canEdit(document, actor, scope));
	}

	private static DepartmentSummary department(Document document) {
		return document.getDepartment() == null ? null : DepartmentSummary.of(document.getDepartment());
	}

	private static String defaultName(MultipartFile file) {
		String original = file.getOriginalFilename();
		if (!StringUtils.hasText(original)) {
			return "Untitled document";
		}
		String base = original.replaceAll("^.*[\\\\/]", "");
		int dot = base.lastIndexOf('.');
		return dot > 0 ? base.substring(0, dot) : base;
	}

}
