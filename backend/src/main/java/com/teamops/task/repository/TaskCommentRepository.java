package com.teamops.task.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.teamops.task.entity.TaskComment;

public interface TaskCommentRepository extends JpaRepository<TaskComment, Long> {

	@EntityGraph(attributePaths = "author")
	List<TaskComment> findByTaskIdOrderByCreatedAtAsc(Long taskId);

}
