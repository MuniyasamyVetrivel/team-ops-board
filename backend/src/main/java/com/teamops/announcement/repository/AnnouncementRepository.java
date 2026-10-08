package com.teamops.announcement.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.teamops.announcement.entity.Announcement;
import com.teamops.announcement.entity.AnnouncementState;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long>,
		JpaSpecificationExecutor<Announcement> {

	@Override
	@EntityGraph(attributePaths = { "targetDepartment", "createdBy" })
	Page<Announcement> findAll(Specification<Announcement> spec, Pageable pageable);

	@Override
	@EntityGraph(attributePaths = { "targetDepartment", "createdBy" })
	List<Announcement> findAll(Specification<Announcement> spec);

	@EntityGraph(attributePaths = { "targetDepartment", "createdBy" })
	Optional<Announcement> findDetailedById(Long id);

	/** For everyone (null target) or one of the given departments. {@code null} departments = no restriction. */
	static Specification<Announcement> audience(Set<Long> departmentIds) {
		if (departmentIds == null) {
			return (root, query, cb) -> cb.conjunction();
		}
		return (root, query, cb) -> cb.or(cb.isNull(root.get("targetDepartment")),
				root.get("targetDepartment").get("id").in(departmentIds));
	}

	static Specification<Announcement> inState(AnnouncementState state, Instant now) {
		return (root, query, cb) -> switch (state) {
			case SCHEDULED -> cb.greaterThan(root.get("publishAt"), now);
			case EXPIRED -> cb.lessThanOrEqualTo(root.get("expiresAt"), now);
			case ACTIVE -> cb.and(cb.lessThanOrEqualTo(root.get("publishAt"), now),
					cb.or(cb.isNull(root.get("expiresAt")), cb.greaterThan(root.get("expiresAt"), now)));
		};
	}

	/** Announcements the actor may manage: their departments' or ones they created. */
	static Specification<Announcement> manageableBy(Long userId, Set<Long> departmentIds) {
		if (departmentIds == null) {
			return (root, query, cb) -> cb.conjunction();
		}
		return (root, query, cb) -> cb.or(cb.equal(root.get("createdBy").get("id"), userId),
				departmentIds.isEmpty() ? cb.disjunction() : root.get("targetDepartment").get("id").in(departmentIds));
	}

}
