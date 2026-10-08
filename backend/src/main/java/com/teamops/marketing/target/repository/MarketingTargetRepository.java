package com.teamops.marketing.target.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.teamops.marketing.target.entity.MarketingTarget;

public interface MarketingTargetRepository extends JpaRepository<MarketingTarget, Long> {

	/** The month's targets in type order. */
	@EntityGraph(attributePaths = { "type", "owner", "department" })
	@Query("select t from MarketingTarget t where t.year = :year and t.month = :month "
			+ "order by t.type.position, t.type.name")
	List<MarketingTarget> findByPeriod(@Param("month") int month, @Param("year") int year);

	@EntityGraph(attributePaths = { "type", "owner", "department" })
	Optional<MarketingTarget> findDetailedById(Long id);

	boolean existsByTypeIdAndMonthAndYear(Long typeId, Integer month, Integer year);

	@Query("select t.type.id from MarketingTarget t where t.year = :year and t.month = :month and t.type.id in :typeIds")
	List<Long> findTypeIdsWithTarget(@Param("typeIds") List<Long> typeIds, @Param("month") int month,
			@Param("year") int year);

	/** A type's targets between two months inclusive ({@code year × 12 + month}), oldest first. */
	@Query("select t from MarketingTarget t where t.type.id = :typeId "
			+ "and (t.year * 12 + t.month) between :from and :to order by t.year, t.month")
	List<MarketingTarget> findForTrend(@Param("typeId") Long typeId, @Param("from") int from, @Param("to") int to);

	/** Target counts per type, for the type admin (unit and source are fixed once a type is in use). */
	@Query("select t.type.id as typeId, count(t) as total from MarketingTarget t group by t.type.id")
	List<TypeCount> countByType();

	boolean existsByTypeId(Long typeId);

	long countByTypeId(Long typeId);

	interface TypeCount {

		Long getTypeId();

		long getTotal();

	}

}
