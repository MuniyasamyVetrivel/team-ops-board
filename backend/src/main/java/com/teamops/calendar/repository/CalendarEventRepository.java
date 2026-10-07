package com.teamops.calendar.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.teamops.calendar.entity.CalendarEvent;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

	/**
	 * Events overlapping [from, to) that the viewer may see: company-wide, in one of {@code departmentIds}, about
	 * them (leave), or created by them. {@code all} skips the department filter (Super Admin).
	 */
	@Query("""
			select e from CalendarEvent e
			left join fetch e.department d
			left join fetch e.user u
			left join e.createdBy c
			where e.startAt < :to and e.endAt > :from
			  and (:all = true or d is null or d.id in :departmentIds or u.id = :me or c.id = :me)
			order by e.startAt, e.id
			""")
	List<CalendarEvent> findVisibleInRange(@Param("from") Instant from, @Param("to") Instant to,
			@Param("all") boolean all, @Param("departmentIds") Collection<Long> departmentIds,
			@Param("me") Long me);

	@EntityGraph(attributePaths = { "department", "user", "createdBy" })
	Optional<CalendarEvent> findDetailedById(Long id);

}
