package com.teamops.celebration.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.teamops.celebration.entity.LinkedInPost;
import com.teamops.celebration.entity.OccasionKind;

public interface LinkedInPostRepository extends JpaRepository<LinkedInPost, Long> {

	@EntityGraph(attributePaths = "owner")
	List<LinkedInPost> findByOccurrenceDateBetween(LocalDate from, LocalDate to);

	@EntityGraph(attributePaths = "owner")
	Optional<LinkedInPost> findBySubjectTypeAndSubjectIdAndOccurrenceDate(OccasionKind subjectType, Long subjectId,
			LocalDate occurrenceDate);

	@EntityGraph(attributePaths = "owner")
	List<LinkedInPost> findBySubjectTypeAndSubjectIdIn(OccasionKind subjectType, Collection<Long> subjectIds);

	/** Posts of a subject's occurrences on or after {@code from} (the ones a date change moves). */
	List<LinkedInPost> findBySubjectTypeAndSubjectIdAndOccurrenceDateGreaterThanEqual(OccasionKind subjectType,
			Long subjectId, LocalDate from);

	@Modifying
	@Query("delete from LinkedInPost p where p.subjectType in :types and p.subjectId = :subjectId")
	int deleteForSubject(@Param("types") Collection<OccasionKind> types, @Param("subjectId") Long subjectId);

}
