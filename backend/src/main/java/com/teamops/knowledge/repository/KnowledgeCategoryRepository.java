package com.teamops.knowledge.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.teamops.knowledge.entity.KnowledgeCategory;

public interface KnowledgeCategoryRepository extends JpaRepository<KnowledgeCategory, Long> {

	List<KnowledgeCategory> findAllByOrderByPositionAscNameAsc();

}
