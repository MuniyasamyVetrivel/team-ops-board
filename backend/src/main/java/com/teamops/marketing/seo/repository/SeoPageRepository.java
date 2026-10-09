package com.teamops.marketing.seo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.teamops.marketing.seo.entity.PageStatus;
import com.teamops.marketing.seo.entity.SeoPage;

public interface SeoPageRepository extends JpaRepository<SeoPage, Long>, JpaSpecificationExecutor<SeoPage> {

	@Override
	@EntityGraph(attributePaths = { "department", "owner" })
	Page<SeoPage> findAll(Specification<SeoPage> spec, Pageable pageable);

	@EntityGraph(attributePaths = { "department", "owner" })
	Optional<SeoPage> findDetailedById(Long id);

	/** Pages that are not archived (the dashboard's total pages). */
	long countByStatusNot(PageStatus status);

	long countByStatusNotAndOwner_Id(PageStatus status, Long ownerId);

	/** Page pickers: everything not archived, by title. */
	List<SeoPage> findByStatusNotOrderByTitleAsc(PageStatus status);

	/** URLs compare case-insensitively (the column collation), so "/Services" and "/services" are one page. */
	boolean existsByUrl(String url);

	boolean existsByUrlAndIdNot(String url, Long id);

}
