package com.teamops.finance.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.teamops.finance.entity.ExpenseCategory;
import com.teamops.finance.entity.FinanceExpense;

public interface FinanceExpenseRepository extends JpaRepository<FinanceExpense, Long>, JpaSpecificationExecutor<FinanceExpense> {

	@EntityGraph(attributePaths = { "department", "receipt", "approval" })
	@Query("select e from FinanceExpense e where e.id = :id")
	Optional<FinanceExpense> findDetailedById(@Param("id") Long id);

	/** Live expenses with a vendor (they keep it from being deleted). */
	long countByVendorId(Long vendorId);

	Page<FinanceExpense> findByVendorId(Long vendorId, Pageable pageable);

	interface MonthTotal {

		int getYear();

		int getMonth();

		BigDecimal getTotal();

	}

	interface DepartmentTotal {

		Long getDepartmentId();

		BigDecimal getTotal();

	}

	interface CategoryTotal {

		ExpenseCategory getCategory();

		BigDecimal getTotal();

		long getCount();

	}

	/** AUD spend per calendar month between the two days (both included); months without expenses are absent. */
	@Query("""
			select extract(year from e.expenseDate) as year, extract(month from e.expenseDate) as month,
			       sum(e.amountAud) as total
			from FinanceExpense e where e.expenseDate between :from and :to
			group by extract(year from e.expenseDate), extract(month from e.expenseDate)
			""")
	List<MonthTotal> totalsByMonth(@Param("from") LocalDate from, @Param("to") LocalDate to);

	/** AUD spend per department between the two days; expenses without a department under a null id. */
	@Query("""
			select d.id as departmentId, sum(e.amountAud) as total
			from FinanceExpense e left join e.department d where e.expenseDate between :from and :to
			group by d.id
			""")
	List<DepartmentTotal> totalsByDepartment(@Param("from") LocalDate from, @Param("to") LocalDate to);

	/** AUD spend and expense count per category between the two days. */
	@Query("""
			select e.category as category, sum(e.amountAud) as total, count(e) as count
			from FinanceExpense e where e.expenseDate between :from and :to group by e.category
			""")
	List<CategoryTotal> totalsByCategory(@Param("from") LocalDate from, @Param("to") LocalDate to);

}
