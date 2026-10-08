package com.teamops.marketing.lead.entity;

/** Where a lead record came from; {@code external_id} is unique per provider. */
public enum LeadOrigin {

	MANUAL, CSV, WEBSITE, CRM

}
