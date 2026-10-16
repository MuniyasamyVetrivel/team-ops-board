package com.teamops.bench.entity;

import java.time.LocalDate;

import org.hibernate.annotations.SQLRestriction;

import com.teamops.common.persistence.SoftDeletableEntity;
import com.teamops.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * An engagement with a client, which consultants are allocated to. The name is unique per client. Its status
 * (upcoming, active, ended) follows from the dates and is never stored. A live engagement's client is always live: a
 * client with engagements cannot be deleted, and an engagement whose client is in the bin cannot be restored.
 */
@Getter
@Setter
@Entity
@SQLRestriction(SoftDeletableEntity.NOT_DELETED)
@Table(name = "bench_client_engagements")
public class ClientEngagement extends SoftDeletableEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "client_id", nullable = false)
	private Client client;

	@Column(name = "name", nullable = false, length = 200)
	private String name;

	@Column(name = "location", length = 150)
	private String location;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	/** Open-ended when null. */
	@Column(name = "end_date")
	private LocalDate endDate;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "account_manager_id")
	private User accountManager;

	@Column(name = "notes", columnDefinition = "text")
	private String notes;

	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

}
