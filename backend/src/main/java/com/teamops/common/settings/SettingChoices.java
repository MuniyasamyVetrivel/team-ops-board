package com.teamops.common.settings;

import java.util.List;

/**
 * The values a choice setting accepts, when they depend on data (for example the Finance Tracker's default currency:
 * INR, AUD, USD and every currency in the exchange rates table). One bean per choice setting, named by its key.
 */
public interface SettingChoices {

	/** The setting this lists the options of, e.g. {@code finance.defaultCurrency}. */
	String key();

	/** The accepted values, in the order the settings page shows them. */
	List<String> options();

}
