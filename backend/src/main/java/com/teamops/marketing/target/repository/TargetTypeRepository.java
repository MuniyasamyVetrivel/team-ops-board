package com.teamops.marketing.target.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.teamops.marketing.target.entity.TargetType;

public interface TargetTypeRepository extends JpaRepository<TargetType, Long> {

	List<TargetType> findAllByOrderByPositionAscNameAsc();

	/** Codes and names compare case-insensitively (the column collation). */
	boolean existsByCode(String code);

	boolean existsByName(String name);

	boolean existsByNameAndIdNot(String name, Long id);

	@Query("select coalesce(max(t.position), 0) from TargetType t")
	int maxPosition();

}
