package com.teamops.department.repository;

/** Aggregate projection: number of rows per department. */
public interface DepartmentCount {

	Long getDepartmentId();

	long getTotal();

}
