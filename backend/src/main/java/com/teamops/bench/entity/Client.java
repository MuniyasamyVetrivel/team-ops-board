package com.teamops.bench.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import com.teamops.common.persistence.SoftDeletableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/** A client consultants are placed with (separate from internal Projects). The name is unique. */
@Getter
@Setter
@Entity
@SQLRestriction(SoftDeletableEntity.NOT_DELETED)
@Table(name = "bench_clients")
public class Client extends SoftDeletableEntity {

	@Column(name = "name", nullable = false, length = 200)
	private String name;

	@Column(name = "location", length = 150)
	private String location;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 16)
	private ClientStatus status = ClientStatus.ACTIVE;

	@Column(name = "notes", columnDefinition = "text")
	private String notes;

	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

}
