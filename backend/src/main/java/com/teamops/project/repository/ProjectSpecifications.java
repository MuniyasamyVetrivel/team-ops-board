package com.teamops.project.repository;

import java.util.Locale;
import java.util.Set;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import com.teamops.project.entity.Project;
import com.teamops.project.entity.ProjectStatus;
import com.teamops.project.service.ProjectAccess;
import com.teamops.user.entity.User;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/** Composable project filters, mirroring {@link ProjectAccess#canView}. Membership uses a subquery. */
public final class ProjectSpecifications {

	private ProjectSpecifications() {
	}

	public static Specification<Project> all() {
		return (root, query, cb) -> cb.conjunction();
	}

	public static Specification<Project> visibleTo(ProjectAccess access) {
		if (access.scope().isAll()) {
			return all();
		}
		Long me = access.me();
		Set<Long> departments = access.departmentIds();
		return (root, query, cb) -> {
			Subquery<Long> memberOf = query.subquery(Long.class);
			Root<Project> project = memberOf.from(Project.class);
			Join<Project, User> member = project.join("members");
			memberOf.select(project.get("id")).where(cb.equal(member.get("id"), me));
			return cb.or(root.get("department").get("id").in(departments), cb.equal(root.get("owner").get("id"), me),
					root.get("id").in(memberOf));
		};
	}

	public static Specification<Project> matches(String search) {
		if (!StringUtils.hasText(search)) {
			return all();
		}
		String pattern = "%" + search.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%")
			.replace("_", "\\_") + "%";
		return (root, query, cb) -> cb.or(cb.like(cb.lower(root.get("name")), pattern, '\\'),
				cb.like(cb.lower(root.get("code")), pattern, '\\'));
	}

	public static Specification<Project> statusIn(Set<ProjectStatus> statuses) {
		return statuses.isEmpty() ? all() : (root, query, cb) -> root.get("status").in(statuses);
	}

	public static Specification<Project> department(Long departmentId) {
		return departmentId == null ? all()
				: (root, query, cb) -> cb.equal(root.get("department").get("id"), departmentId);
	}

}
