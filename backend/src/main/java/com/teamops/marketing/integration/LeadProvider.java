package com.teamops.marketing.integration;

import java.util.List;

import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.integration.ProviderRecords.ExternalLead;

/** Leads captured outside the app in a month. Later: website forms, CRM. */
public interface LeadProvider extends MarketingDataProvider {

	List<ExternalLead> fetchLeads(MarketingPeriod period);

}
