package com.teamops.task.service;

import org.springframework.stereotype.Component;

import com.teamops.common.storage.FileService;
import com.teamops.recyclebin.service.RecycleBinHandler;
import com.teamops.task.entity.Task;
import com.teamops.task.entity.TaskAttachment;
import com.teamops.task.repository.TaskAttachmentRepository;

import lombok.RequiredArgsConstructor;

/**
 * A task in the recycle bin keeps its comments, checklist, attachments, watchers, tags and history. The permanent
 * delete removes the attachments' stored files; the other children go with the row (database cascades).
 */
@Component
@RequiredArgsConstructor
class TaskRecycleHandler implements RecycleBinHandler<Task> {

	private final TaskAttachmentRepository attachmentRepository;

	private final FileService fileService;

	@Override
	public Class<Task> type() {
		return Task.class;
	}

	@Override
	public void beforePurge(Task task) {
		for (TaskAttachment attachment : attachmentRepository.findByTaskIdOrderByAddedAtAsc(task.getId())) {
			attachmentRepository.delete(attachment);
			fileService.delete(attachment.getFile());
		}
		attachmentRepository.flush();
	}

}
