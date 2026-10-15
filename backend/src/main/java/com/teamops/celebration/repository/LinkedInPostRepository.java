package com.teamops.celebration.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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

	/** A subject's posts, removed one by one with it so each removal reaches the audit history. */
	List<LinkedInPost> findBySubjectTypeInAndSubjectId(Collection<OccasionKind> types, Long subjectId);

}
