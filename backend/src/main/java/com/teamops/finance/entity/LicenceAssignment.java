package com.teamops.finance.entity;

import com.teamops.common.audit.AuditRef;
import com.teamops.common.audit.AuditedChild;
import com.teamops.common.persistence.AuditedEntity;
import com.teamops.user.entity.User;

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

/** A seat of a licence assigned to a user; the licence's seats used are its assignments. */
@Getter
@Setter
@Entity
@Table(name = "finance_licence_assignments")
public class LicenceAssignment extends AuditedEntity implements AuditedChild {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "licence_id", nullable = false)
	private FinanceLicence licence;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Override
	public AuditRef auditParent() {
		return licence == null ? null : AuditRef.of(FinanceLicence.class, licence.getId());
	}

}
