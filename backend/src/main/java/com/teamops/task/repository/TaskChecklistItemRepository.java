package com.teamops.task.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.teamops.task.entity.TaskChecklistItem;

public interface TaskChecklistItemRepository extends JpaRepository<TaskChecklistItem, Long> {

	List<TaskChecklistItem> findByTaskIdOrderByPositionAscIdAsc(Long taskId);

	@Query("select coalesce(max(c.position), -1) from TaskChecklistItem c where c.task.id = :taskId")
	int maxPosition(@Param("taskId") Long taskId);

}
