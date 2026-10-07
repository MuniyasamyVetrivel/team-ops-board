package com.teamops.task.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.teamops.task.entity.TaskAttachment;

public interface TaskAttachmentRepository extends JpaRepository<TaskAttachment, TaskAttachment.Id> {

	@EntityGraph(attributePaths = { "file", "addedBy" })
	List<TaskAttachment> findByTaskIdOrderByAddedAtAsc(Long taskId);

}
