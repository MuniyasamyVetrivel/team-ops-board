package com.teamops.task.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.teamops.task.entity.TaskHistory;

public interface TaskHistoryRepository extends JpaRepository<TaskHistory, Long> {

	@EntityGraph(attributePaths = "changedBy")
	List<TaskHistory> findTop100ByTaskIdOrderByChangedAtDescIdDesc(Long taskId);

}
