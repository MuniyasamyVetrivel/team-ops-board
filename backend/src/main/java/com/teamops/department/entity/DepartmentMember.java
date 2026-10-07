package com.teamops.department.entity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.teamops.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Secondary membership of a user in a department other than their primary one (users.department_id). */
@Getter
@Setter
@Entity
@Table(name = "department_members")
public class DepartmentMember {

	@EmbeddedId
	private DepartmentMemberId id;

	@MapsId("departmentId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "department_id")
	private Department department;

	@MapsId("userId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id")
	private User user;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "member_role", nullable = false, length = 32)
	private DepartmentMemberRole memberRole = DepartmentMemberRole.MEMBER;

	@CreationTimestamp
	@Column(name = "joined_at", nullable = false, updatable = false)
	private Instant joinedAt;

	public static DepartmentMember of(Department department, User user, DepartmentMemberRole role) {
		DepartmentMember member = new DepartmentMember();
		member.setId(new DepartmentMemberId(department.getId(), user.getId()));
		member.setDepartment(department);
		member.setUser(user);
		member.setMemberRole(role);
		return member;
	}

}
