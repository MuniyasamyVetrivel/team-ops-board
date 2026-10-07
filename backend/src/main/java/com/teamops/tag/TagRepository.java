package com.teamops.tag;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, Long> {

	List<Tag> findByNameIn(Set<String> names);

}
