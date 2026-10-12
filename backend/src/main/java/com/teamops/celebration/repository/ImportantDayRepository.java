package com.teamops.celebration.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.teamops.celebration.entity.DayRecurrence;
import com.teamops.celebration.entity.ImportantDay;
import com.teamops.celebration.entity.Region;

public interface ImportantDayRepository extends JpaRepository<ImportantDay, Long>, JpaSpecificationExecutor<ImportantDay> {

	/** Days that can fall in [from, to]: one-off days dated in it, and yearly days that started by {@code to}. */
	@Query("""
			select d from ImportantDay d
			where (d.recurrence = com.teamops.celebration.entity.DayRecurrence.ONE_OFF and d.date between :from and :to)
			   or (d.recurrence = com.teamops.celebration.entity.DayRecurrence.YEARLY and d.date <= :to)
			""")
	List<ImportantDay> findCandidates(@Param("from") LocalDate from, @Param("to") LocalDate to);

	boolean existsByNameAndDateAndRegion(String name, LocalDate date, Region region);

	boolean existsByNameAndRegionAndRecurrence(String name, Region region, DayRecurrence recurrence);

	boolean existsByNameAndDateAndRegionAndIdNot(String name, LocalDate date, Region region, Long id);

}
