package com.teamops.marketing.integration.manual;

import java.util.List;

import org.springframework.stereotype.Component;

import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.integration.LeadProvider;
import com.teamops.marketing.integration.ProviderRecords.ExternalLead;

/** Manual entry and CSV import: nothing to fetch. */
@Component
public class ManualLeadProvider implements LeadProvider {

	private static final ProviderInfo INFO = new ProviderInfo("MANUAL", "Manual entry", Category.LEADS, false,
			false, "Leads are logged by the team or imported from CSV.",
			List.of("Website forms", "CRM"));

	@Override
	public ProviderInfo info() {
		return INFO;
	}

	@Override
	public List<ExternalLead> fetchLeads(MarketingPeriod period) {
		return List.of();
	}

}
