package com.teamops.bench.entity;

import com.teamops.common.audit.AuditRef;
import com.teamops.common.audit.AuditedChild;
import com.teamops.common.persistence.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** One skill of a consultant ("SAP S/4HANA", "Tosca"), unique per consultant ignoring case. */
@Getter
@Setter
@Entity
@Table(name = "bench_consultant_skills")
public class ConsultantSkill extends AuditedEntity implements AuditedChild {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "consultant_id", nullable = false)
	private Consultant consultant;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Column(name = "position", nullable = false)
	private int position;

	@Override
	public AuditRef auditParent() {
		return consultant == null ? null : AuditRef.of(Consultant.class, consultant.getId());
	}

}
