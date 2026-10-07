package com.teamops.department.entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor
public class DepartmentMemberId implements Serializable {

	@Column(name = "department_id")
	private Long departmentId;

	@Column(name = "user_id")
	private Long userId;

	public DepartmentMemberId(Long departmentId, Long userId) {
		this.departmentId = departmentId;
		this.userId = userId;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof DepartmentMemberId that && Objects.equals(departmentId, that.departmentId)
				&& Objects.equals(userId, that.userId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(departmentId, userId);
	}

}
