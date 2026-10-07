package com.teamops.task.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageRequests;
import com.teamops.common.web.PageResponse;
import com.teamops.task.dto.DueFilter;
import com.teamops.task.dto.MyTaskSummary;
import com.teamops.task.dto.TaskDetail;
import com.teamops.task.dto.TaskListItem;
import com.teamops.task.dto.TaskRequests;
import com.teamops.task.dto.TaskSearchCriteria;
import com.teamops.task.dto.TaskView;
import com.teamops.task.entity.TaskPriority;
import com.teamops.task.entity.TaskStatus;
import com.teamops.task.service.TaskCollaborationService;
import com.teamops.task.service.TaskService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Task API. {@code @PreAuthorize} checks the permission; the services check scope (department / own / watcher) on
 * every call.
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('TASK_VIEW')")
public class TaskController {

	static final Map<String, List<String>> SORT_FIELDS = Map.of("due", List.of("dueDate", "id"), "priority",
			List.of("priority", "dueDate"), "updated", List.of("updatedAt"), "created", List.of("createdAt"), "code",
			List.of("id"), "title", List.of("title"), "status", List.of("status", "dueDate"));

	private final TaskService taskService;

	private final TaskCollaborationService collaborationService;

	@GetMapping
	public PageResponse<TaskListItem> search(@RequestParam(required = false) String search,
			@RequestParam(required = false) Set<TaskStatus> status,
			@RequestParam(required = false) Set<TaskPriority> priority,
			@RequestParam(required = false) Long assigneeId, @RequestParam(required = false) Long departmentId,
			@RequestParam(required = false) Long projectId, @RequestParam(required = false) DueFilter due,
			@RequestParam(defaultValue = "ALL") TaskView view, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size, @RequestParam(required = false) String sort,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		TaskSearchCriteria criteria = new TaskSearchCriteria(search, status, priority, assigneeId, departmentId,
				projectId, due, view);
		return taskService.search(criteria, PageRequests.of(page, size, sort, SORT_FIELDS, "due,asc"), actor);
	}

	@GetMapping("/my/summary")
	public MyTaskSummary mySummary(@AuthenticationPrincipal AuthenticatedUser actor) {
		return taskService.mySummary(actor);
	}

	@GetMapping("/{id}")
	public TaskDetail get(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor) {
		return taskService.get(id, actor);
	}

	@PostMapping
	@PreAuthorize("hasAuthority('TASK_CREATE')")
	public ResponseEntity<TaskDetail> create(@Valid @RequestBody TaskRequests.CreateTask request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return ResponseEntity.status(HttpStatus.CREATED).body(taskService.create(request, actor, ClientInfo.from(http)));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('TASK_EDIT')")
	public TaskDetail update(@PathVariable Long id, @Valid @RequestBody TaskRequests.UpdateTask request,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return taskService.update(id, request, actor);
	}

	@PutMapping("/{id}/assignee")
	@PreAuthorize("hasAuthority('TASK_EDIT')")
	public TaskDetail assign(@PathVariable Long id, @RequestBody TaskRequests.Assign request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return taskService.assign(id, request.assigneeId(), actor, ClientInfo.from(http));
	}

	@PutMapping("/{id}/status")
	@PreAuthorize("hasAuthority('TASK_EDIT')")
	public TaskDetail changeStatus(@PathVariable Long id, @Valid @RequestBody TaskRequests.ChangeStatus request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return taskService.changeStatus(id, request.status(), actor, ClientInfo.from(http));
	}

	// --- comments -------------------------------------------------------------------------------------------

	@PostMapping("/{id}/comments")
	public TaskDetail addComment(@PathVariable Long id, @Valid @RequestBody TaskRequests.Comment request,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return collaborationService.addComment(id, request.body(), actor);
	}

	@PutMapping("/{id}/comments/{commentId}")
	public TaskDetail editComment(@PathVariable Long id, @PathVariable Long commentId,
			@Valid @RequestBody TaskRequests.Comment request, @AuthenticationPrincipal AuthenticatedUser actor) {
		return collaborationService.editComment(id, commentId, request.body(), actor);
	}

	@DeleteMapping("/{id}/comments/{commentId}")
	public TaskDetail deleteComment(@PathVariable Long id, @PathVariable Long commentId,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return collaborationService.deleteComment(id, commentId, actor);
	}

	// --- checklist ------------------------------------------------------------------------------------------

	@PostMapping("/{id}/checklist")
	@PreAuthorize("hasAuthority('TASK_EDIT')")
	public TaskDetail addChecklistItem(@PathVariable Long id, @Valid @RequestBody TaskRequests.ChecklistItem request,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return collaborationService.addChecklistItem(id, request.content(), actor);
	}

	@PutMapping("/{id}/checklist/{itemId}")
	@PreAuthorize("hasAuthority('TASK_EDIT')")
	public TaskDetail toggleChecklistItem(@PathVariable Long id, @PathVariable Long itemId,
			@RequestBody TaskRequests.ChecklistToggle request, @AuthenticationPrincipal AuthenticatedUser actor) {
		return collaborationService.toggleChecklistItem(id, itemId, request.done(), actor);
	}

	@DeleteMapping("/{id}/checklist/{itemId}")
	@PreAuthorize("hasAuthority('TASK_EDIT')")
	public TaskDetail deleteChecklistItem(@PathVariable Long id, @PathVariable Long itemId,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return collaborationService.deleteChecklistItem(id, itemId, actor);
	}

	// --- watchers -------------------------------------------------------------------------------------------

	@PostMapping("/{id}/watchers")
	public TaskDetail addWatcher(@PathVariable Long id, @Valid @RequestBody TaskRequests.Watcher request,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return collaborationService.addWatcher(id, request.userId(), actor);
	}

	/** 204 when the caller removed themselves and can no longer see the task. */
	@DeleteMapping("/{id}/watchers/{userId}")
	public ResponseEntity<TaskDetail> removeWatcher(@PathVariable Long id, @PathVariable Long userId,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return collaborationService.removeWatcher(id, userId, actor)
			.map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.noContent().build());
	}

	// --- dependencies ---------------------------------------------------------------------------------------

	@PostMapping("/{id}/dependencies")
	@PreAuthorize("hasAuthority('TASK_EDIT')")
	public TaskDetail addDependency(@PathVariable Long id, @Valid @RequestBody TaskRequests.Dependency request,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return collaborationService.addDependency(id, request.dependsOnTaskId(), actor);
	}

	@DeleteMapping("/{id}/dependencies/{dependsOnTaskId}")
	@PreAuthorize("hasAuthority('TASK_EDIT')")
	public TaskDetail removeDependency(@PathVariable Long id, @PathVariable Long dependsOnTaskId,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return collaborationService.removeDependency(id, dependsOnTaskId, actor);
	}

	// --- attachments ----------------------------------------------------------------------------------------

	@PostMapping(path = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAuthority('TASK_EDIT')")
	public TaskDetail addAttachment(@PathVariable Long id, @RequestPart("file") MultipartFile file,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return collaborationService.addAttachment(id, file, actor);
	}

	/** Always served as a download, with the content type derived from the validated extension. */
	@GetMapping("/{id}/attachments/{fileId}")
	public ResponseEntity<Resource> download(@PathVariable Long id, @PathVariable Long fileId,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		TaskCollaborationService.Download download = collaborationService.download(id, fileId, actor);
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(download.contentType()))
			.contentLength(download.sizeBytes())
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename(download.fileName(), StandardCharsets.UTF_8).build().toString())
			.header("X-Content-Type-Options", "nosniff")
			.body(download.content());
	}

	@DeleteMapping("/{id}/attachments/{fileId}")
	public TaskDetail deleteAttachment(@PathVariable Long id, @PathVariable Long fileId,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return collaborationService.deleteAttachment(id, fileId, actor);
	}

}
