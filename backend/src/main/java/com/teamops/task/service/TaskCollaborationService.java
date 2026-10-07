package com.teamops.task.service;

import java.util.Optional;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.storage.FileService;
import com.teamops.common.storage.StoredFile;
import com.teamops.task.dto.TaskDetail;
import com.teamops.task.entity.Task;
import com.teamops.task.entity.TaskAttachment;
import com.teamops.task.entity.TaskChecklistItem;
import com.teamops.task.entity.TaskComment;
import com.teamops.task.repository.TaskAttachmentRepository;
import com.teamops.task.repository.TaskChecklistItemRepository;
import com.teamops.task.repository.TaskCommentRepository;
import com.teamops.task.repository.TaskRepository;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Comments, checklist, watchers, dependencies and attachments. Anyone who can see a task may comment on it and
 * watch it; changing the checklist, dependencies, other watchers or attachments needs edit rights. Every method
 * returns the refreshed task detail so the UI updates in one round trip.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TaskCollaborationService {

	private final TaskService taskService;

	private final TaskRepository taskRepository;

	private final TaskCommentRepository commentRepository;

	private final TaskChecklistItemRepository checklistRepository;

	private final TaskAttachmentRepository attachmentRepository;

	private final UserRepository userRepository;

	private final FileService fileService;

	private final BusinessCalendar calendar;

	// --- comments -------------------------------------------------------------------------------------------

	public TaskDetail addComment(Long taskId, String body, AuthenticatedUser actor) {
		TaskAccess access = taskService.access(actor);
		Task task = taskService.loadVisible(taskId, access);
		TaskComment comment = new TaskComment();
		comment.setTask(task);
		comment.setAuthor(userRepository.getReferenceById(actor.id()));
		comment.setBody(body.trim());
		commentRepository.save(comment);
		return taskService.toDetail(task, access);
	}

	/** Only the author may edit a comment. */
	public TaskDetail editComment(Long taskId, Long commentId, String body, AuthenticatedUser actor) {
		TaskAccess access = taskService.access(actor);
		Task task = taskService.loadVisible(taskId, access);
		TaskComment comment = loadComment(task, commentId);
		if (comment.getAuthor() == null || !comment.getAuthor().getId().equals(actor.id())) {
			throw ApiException.forbidden("FORBIDDEN", "You can only edit your own comments");
		}
		comment.setBody(body.trim());
		comment.setEdited(true);
		commentRepository.flush();
		return taskService.toDetail(task, access);
	}

	/** The author, or anyone who can edit the task, may delete a comment. */
	public TaskDetail deleteComment(Long taskId, Long commentId, AuthenticatedUser actor) {
		TaskAccess access = taskService.access(actor);
		Task task = taskService.loadVisible(taskId, access);
		TaskComment comment = loadComment(task, commentId);
		boolean author = comment.getAuthor() != null && comment.getAuthor().getId().equals(actor.id());
		if (!author && !access.canEdit(task)) {
			throw ApiException.forbidden("FORBIDDEN", "You cannot delete this comment");
		}
		commentRepository.delete(comment);
		return taskService.toDetail(task, access);
	}

	// --- checklist ------------------------------------------------------------------------------------------

	public TaskDetail addChecklistItem(Long taskId, String content, AuthenticatedUser actor) {
		TaskAccess access = taskService.access(actor);
		Task task = editable(taskId, access);
		TaskChecklistItem item = new TaskChecklistItem();
		item.setTask(task);
		item.setContent(content.trim());
		item.setPosition(checklistRepository.maxPosition(taskId) + 1);
		checklistRepository.save(item);
		return taskService.toDetail(task, access);
	}

	public TaskDetail toggleChecklistItem(Long taskId, Long itemId, boolean done, AuthenticatedUser actor) {
		TaskAccess access = taskService.access(actor);
		Task task = editable(taskId, access);
		TaskChecklistItem item = loadChecklistItem(task, itemId);
		item.setDone(done);
		item.setDoneBy(done ? userRepository.getReferenceById(actor.id()) : null);
		item.setDoneAt(done ? calendar.now() : null);
		checklistRepository.flush();
		return taskService.toDetail(task, access);
	}

	public TaskDetail deleteChecklistItem(Long taskId, Long itemId, AuthenticatedUser actor) {
		TaskAccess access = taskService.access(actor);
		Task task = editable(taskId, access);
		checklistRepository.delete(loadChecklistItem(task, itemId));
		return taskService.toDetail(task, access);
	}

	// --- watchers -------------------------------------------------------------------------------------------

	/** Anyone who can see the task may watch it themselves; adding someone else needs edit rights. */
	public TaskDetail addWatcher(Long taskId, Long userId, AuthenticatedUser actor) {
		TaskAccess access = taskService.access(actor);
		Task task = taskService.loadVisible(taskId, access);
		if (!userId.equals(actor.id())) {
			TaskService.requireEdit(access, task);
		}
		User user = userRepository.findById(userId)
			.filter(User::isActive)
			.orElseThrow(() -> ApiException.badRequest("UNKNOWN_USER", "User not found or disabled"));
		if (!task.isWatchedBy(userId)) {
			task.getWatchers().add(user);
			taskService.history(task, actor, "watcher added", null, user.getFullName());
		}
		taskRepository.flush();
		return taskService.toDetail(task, access);
	}

	/** Empty when the actor removed themselves and can no longer see the task. */
	public Optional<TaskDetail> removeWatcher(Long taskId, Long userId, AuthenticatedUser actor) {
		TaskAccess access = taskService.access(actor);
		Task task = taskService.loadVisible(taskId, access);
		if (!userId.equals(actor.id())) {
			TaskService.requireEdit(access, task);
		}
		task.getWatchers().removeIf(user -> user.getId().equals(userId));
		taskRepository.flush();
		return access.canView(task) ? Optional.of(taskService.toDetail(task, access)) : Optional.empty();
	}

	// --- dependencies ---------------------------------------------------------------------------------------

	/** "This task depends on X": rejects self-references and cycles. */
	public TaskDetail addDependency(Long taskId, Long dependsOnTaskId, AuthenticatedUser actor) {
		TaskAccess access = taskService.access(actor);
		Task task = editable(taskId, access);
		if (taskId.equals(dependsOnTaskId)) {
			throw ApiException.badRequest("INVALID_DEPENDENCY", "A task cannot depend on itself");
		}
		Task target = taskService.loadVisible(dependsOnTaskId, access);
		if (taskRepository.countDependencyPath(dependsOnTaskId, taskId) > 0) {
			throw ApiException.badRequest("DEPENDENCY_CYCLE",
					target.getCode() + " already depends on " + task.getCode() + "; this would create a loop");
		}
		if (task.getDependsOn().add(target)) {
			taskService.history(task, actor, "dependency added", null, target.getCode());
		}
		taskRepository.flush();
		return taskService.toDetail(task, access);
	}

	public TaskDetail removeDependency(Long taskId, Long dependsOnTaskId, AuthenticatedUser actor) {
		TaskAccess access = taskService.access(actor);
		Task task = editable(taskId, access);
		task.getDependsOn().stream().filter(t -> t.getId().equals(dependsOnTaskId)).findFirst().ifPresent(target -> {
			task.getDependsOn().remove(target);
			taskService.history(task, actor, "dependency removed", target.getCode(), null);
		});
		taskRepository.flush();
		return taskService.toDetail(task, access);
	}

	// --- attachments ----------------------------------------------------------------------------------------

	public TaskDetail addAttachment(Long taskId, MultipartFile upload, AuthenticatedUser actor) {
		TaskAccess access = taskService.access(actor);
		Task task = editable(taskId, access);
		StoredFile file = fileService.store(upload, actor.id());
		attachmentRepository.save(TaskAttachment.of(task, file, userRepository.getReferenceById(actor.id())));
		taskService.history(task, actor, "attachment added", null, file.getOriginalName());
		return taskService.toDetail(task, access);
	}

	@Transactional(readOnly = true)
	public Download download(Long taskId, Long fileId, AuthenticatedUser actor) {
		TaskAccess access = taskService.access(actor);
		taskService.loadVisible(taskId, access);
		StoredFile file = loadAttachment(taskId, fileId).getFile();
		return new Download(file.getOriginalName(), file.getContentType(), file.getSizeBytes(),
				fileService.content(file));
	}

	/** The uploader, or anyone who can edit the task, may remove an attachment. */
	public TaskDetail deleteAttachment(Long taskId, Long fileId, AuthenticatedUser actor) {
		TaskAccess access = taskService.access(actor);
		Task task = taskService.loadVisible(taskId, access);
		TaskAttachment attachment = loadAttachment(taskId, fileId);
		boolean uploader = attachment.getAddedBy() != null && attachment.getAddedBy().getId().equals(actor.id());
		if (!uploader && !access.canEdit(task)) {
			throw ApiException.forbidden("FORBIDDEN", "You cannot remove this attachment");
		}
		String name = attachment.getFile().getOriginalName();
		attachmentRepository.delete(attachment);
		fileService.delete(attachment.getFile());
		taskService.history(task, actor, "attachment removed", name, null);
		return taskService.toDetail(task, access);
	}

	public record Download(String fileName, String contentType, long sizeBytes, Resource content) {
	}

	// --- helpers --------------------------------------------------------------------------------------------

	private Task editable(Long taskId, TaskAccess access) {
		Task task = taskService.loadVisible(taskId, access);
		TaskService.requireEdit(access, task);
		return task;
	}

	private TaskComment loadComment(Task task, Long commentId) {
		return commentRepository.findById(commentId)
			.filter(comment -> comment.getTask().getId().equals(task.getId()))
			.orElseThrow(() -> ApiException.notFound("COMMENT_NOT_FOUND", "Comment not found"));
	}

	private TaskChecklistItem loadChecklistItem(Task task, Long itemId) {
		return checklistRepository.findById(itemId)
			.filter(item -> item.getTask().getId().equals(task.getId()))
			.orElseThrow(() -> ApiException.notFound("CHECKLIST_ITEM_NOT_FOUND", "Checklist item not found"));
	}

	private TaskAttachment loadAttachment(Long taskId, Long fileId) {
		return attachmentRepository.findById(new TaskAttachment.Id(taskId, fileId))
			.orElseThrow(() -> ApiException.notFound("ATTACHMENT_NOT_FOUND", "Attachment not found"));
	}

}
