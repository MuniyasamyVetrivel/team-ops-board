package com.teamops.celebration.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.teamops.celebration.entity.OccasionAttachment;
import com.teamops.celebration.entity.OccasionKind;

public interface OccasionAttachmentRepository extends JpaRepository<OccasionAttachment, Long> {

	@EntityGraph(attributePaths = { "file", "addedBy" })
	List<OccasionAttachment> findBySubjectTypeAndSubjectIdOrderByAddedAtAsc(OccasionKind subjectType, Long subjectId);

	@EntityGraph(attributePaths = "file")
	Optional<OccasionAttachment> findBySubjectTypeAndSubjectIdAndFileId(OccasionKind subjectType, Long subjectId,
			Long fileId);

	/** Attachment counts for a page of a list: rows of [subject id, count]. */
	@Query("""
			select a.subjectId, count(a) from OccasionAttachment a
			where a.subjectType = :type and a.subjectId in :ids group by a.subjectId
			""")
	List<Object[]> countBySubjects(@Param("type") OccasionKind type, @Param("ids") Collection<Long> ids);

}
