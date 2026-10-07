package com.teamops.workload;

import java.time.LocalDate;
import java.util.Set;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.teamops.common.security.AuthenticatedUser;
import com.teamops.task.entity.TaskPriority;
import com.teamops.task.entity.TaskStatus;

import lombok.RequiredArgsConstructor;

/**
 * Workload API. Needs WORKLOAD_VIEW, except that anyone may request their own row ({@code userId} = themselves),
 * which the team profile uses. The service enforces both rules and the viewer's scope.
 */
@RestController
@RequestMapping("/api/workload")
@RequiredArgsConstructor
public class WorkloadController {

	private final WorkloadService workloadService;

	@GetMapping
	public WorkloadDtos.Response workload(@RequestParam(required = false) Long departmentId,
			@RequestParam(required = false) Long userId, @RequestParam(required = false) String search,
			@RequestParam(required = false) Set<TaskStatus> status,
			@RequestParam(required = false) Set<TaskPriority> priority,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(defaultValue = "HIGHEST") WorkloadDtos.Sort sort,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return workloadService.workload(
				new WorkloadDtos.Criteria(departmentId, userId, search, status, priority, from, to, sort), actor);
	}

}
