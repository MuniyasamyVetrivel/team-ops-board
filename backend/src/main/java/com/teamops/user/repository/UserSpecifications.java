package com.teamops.user.repository;

import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import com.teamops.user.entity.Role;
import com.teamops.user.entity.User;
import com.teamops.user.entity.UserStatus;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/** Composable filters for user search. Each returns "match everything" when its input is empty. */
public final class UserSpecifications {

	private UserSpecifications() {
	}

	public static Specification<User> all() {
		return (root, query, cb) -> cb.conjunction();
	}

	/** Case-insensitive match on first name, last name, full name, email or job title. */
	public static Specification<User> matches(String search) {
		if (!StringUtils.hasText(search)) {
			return all();
		}
		String pattern = "%" + escapeLike(search.trim().toLowerCase(Locale.ROOT)) + "%";
		return (root, query, cb) -> cb.or(cb.like(cb.lower(root.get("firstName")), pattern, '\\'),
				cb.like(cb.lower(root.get("lastName")), pattern, '\\'),
				cb.like(cb.lower(cb.concat(cb.concat(root.get("firstName"), " "), root.get("lastName"))), pattern,
						'\\'),
				cb.like(cb.lower(root.get("email")), pattern, '\\'),
				cb.like(cb.lower(root.get("jobTitle")), pattern, '\\'));
	}

	public static Specification<User> inDepartment(Long departmentId) {
		if (departmentId == null) {
			return all();
		}
		return (root, query, cb) -> cb.equal(root.get("department").get("id"), departmentId);
	}

	public static Specification<User> withStatus(UserStatus status) {
		if (status == null) {
			return all();
		}
		return (root, query, cb) -> cb.equal(root.get("status"), status);
	}

	/** Uses a subquery rather than a join so paging and sorting are not affected by duplicate rows. */
	public static Specification<User> withRole(String roleCode) {
		if (!StringUtils.hasText(roleCode)) {
			return all();
		}
		return (root, query, cb) -> {
			Subquery<Long> subquery = query.subquery(Long.class);
			Root<User> user = subquery.from(User.class);
			Join<User, Role> role = user.join("roles");
			subquery.select(user.get("id")).where(cb.equal(role.get("code"), roleCode));
			return root.get("id").in(subquery);
		};
	}

	static String escapeLike(String value) {
		return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

}
