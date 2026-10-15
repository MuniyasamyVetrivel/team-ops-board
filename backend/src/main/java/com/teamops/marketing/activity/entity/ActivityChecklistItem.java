package com.teamops.marketing.activity.entity;

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

/** One line of an activity's checklist template, copied into each generated task. */
@Getter
@Setter
@Entity
@Table(name = "marketing_activity_checklist_items")
public class ActivityChecklistItem extends AuditedEntity implements AuditedChild {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "activity_id", nullable = false)
	private MarketingActivity activity;

	@Column(name = "content", nullable = false, length = 500)
	private String content;

	@Column(name = "position", nullable = false)
	private int position;

	@Override
	public AuditRef auditParent() {
		return activity == null ? null : AuditRef.of(MarketingActivity.class, activity.getId());
	}

}
