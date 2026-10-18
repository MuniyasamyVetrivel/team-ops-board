package com.teamops.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.finance.service.FinanceReminderService;
import com.teamops.support.IntegrationUsers;
import com.teamops.user.entity.RoleCodes;
import com.teamops.user.repository.UserRepository;
import com.teamops.user.service.UserService;

/**
 * Finance Tracker part 1 (Phase X8) end to end against MySQL, in a throwaway department with Finance and Vendors
 * switched on: tools and licences with seats, costs and AUD equivalents, the manual exchange rates table, permissions
 * and the module switch, the vendor page link, audit history (hidden from non-finance auditors), the recycle bin, CSV
 * import and export, renewal reminders and global search. Rates use XTS, the ISO code reserved for testing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = { "app.dev-seed.enabled=false", "app.storage.dir=target/it-uploads" })
class FinanceIT {

	private static final String API = "/api/modules/finance";

	private static final String TEST_CURRENCY = "XTS";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DepartmentRepository departmentRepository;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private BusinessCalendar calendar;

	@Autowired
	private FinanceReminderService reminders;

	private IntegrationUsers users;

	private String nonce;

	private Long departmentId;

	private Long viewerId;

	private Long plainId;

	private Long outsiderId;

	private String adminToken;

	private String viewerToken;

	private String plainToken;

	private String outsiderToken;

	private String vendorOnlyToken;

	private String auditorToken;

	private String binManagerToken;

	private String exporterToken;

	private String financeExporterToken;

	@BeforeEach
	void setUp() throws Exception {
		users = new IntegrationUsers(userService, userRepository, departmentRepository);
		Long adminId = users.create("IT", RoleCodes.SUPER_ADMIN);
		adminToken = login(adminId);
		nonce = Long.toHexString(System.nanoTime()).toUpperCase();
		String code = "FIN_" + nonce;
		departmentId = id(as(adminToken, post("/api/departments").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Finance %s\",\"code\":\"%s\"}".formatted(code, code))).andExpect(status().isCreated()));
		for (String module : List.of("finance", "vendors")) {
			as(adminToken, put("/api/admin/modules/" + module + "/departments/" + departmentId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"enabled\":true}")).andExpect(status().isOk());
		}
		// Reads finance and record history; changes nothing.
		viewerId = users.create(departmentId, RoleCodes.EMPLOYEE, "FINANCE_VIEW", "HISTORY_VIEW", "VENDOR_VIEW");
		viewerToken = login(viewerId);
		plainId = users.create(departmentId, RoleCodes.EMPLOYEE);
		plainToken = login(plainId);
		// Holds both permissions, but the module is not switched on for the IT department.
		outsiderId = users.create("IT", RoleCodes.EMPLOYEE, "FINANCE_VIEW", "FINANCE_EDIT");
		outsiderToken = login(outsiderId);
		// Sees vendors but not finance data.
		vendorOnlyToken = login(users.create(departmentId, RoleCodes.EMPLOYEE, "VENDOR_VIEW"));
		// Reads the audit log and any record's history, but not finance data.
		auditorToken = login(users.create(departmentId, RoleCodes.EMPLOYEE, "AUDIT_VIEW", "HISTORY_VIEW"));
		// Manages the recycle bin and exports all data, but cannot see finance data.
		binManagerToken = login(users.create(departmentId, RoleCodes.EMPLOYEE, "RECYCLE_BIN_MANAGE"));
		exporterToken = login(users.create(departmentId, RoleCodes.EMPLOYEE, "DATA_EXPORT"));
		// Exports all data and sees finance data, without being a Super Admin.
		financeExporterToken = login(users.create(departmentId, RoleCodes.EMPLOYEE, "DATA_EXPORT", "FINANCE_VIEW"));
	}

	@AfterEach
	void cleanUp() {
		// Deleted rows are invisible to the repositories, so clean up in SQL (assignments cascade).
		String licences = "SELECT id FROM finance_licences WHERE name LIKE '%" + nonce + "%'";
		String rates = "SELECT id FROM finance_exchange_rates WHERE currency = '" + TEST_CURRENCY + "'";
		String vendors = "SELECT id FROM vendors WHERE name LIKE '%" + nonce + "%'";
		jdbc.update("DELETE FROM notifications WHERE entity_type = 'FINANCE_LICENCE' AND entity_id IN (" + licences + ")");
		jdbc.update("DELETE FROM recycle_bin_items WHERE entity_type = 'FINANCE_LICENCE' AND entity_id IN (" + licences + ")");
		jdbc.update("DELETE FROM recycle_bin_items WHERE entity_type = 'EXCHANGE_RATE' AND entity_id IN (" + rates + ")");
		jdbc.update("DELETE FROM recycle_bin_items WHERE entity_type = 'VENDOR' AND entity_id IN (" + vendors + ")");
		jdbc.update("DELETE FROM finance_licences WHERE name LIKE ?", "%" + nonce + "%");
		String expenses = "SELECT id FROM finance_expenses WHERE description LIKE '%" + nonce + "%'";
		String budgets = "SELECT id FROM finance_budgets WHERE department_id = " + departmentId;
		jdbc.update("DELETE FROM recycle_bin_items WHERE entity_type = 'FINANCE_EXPENSE' AND entity_id IN (" + expenses + ")");
		jdbc.update("DELETE FROM recycle_bin_items WHERE entity_type = 'FINANCE_BUDGET' AND entity_id IN (" + budgets + ")");
		jdbc.update("DELETE FROM files WHERE id IN (SELECT receipt_file_id FROM (SELECT e.receipt_file_id FROM "
				+ "finance_expenses e WHERE e.description LIKE ? AND e.receipt_file_id IS NOT NULL) r)", "%" + nonce + "%");
		jdbc.update("DELETE FROM finance_expenses WHERE description LIKE ?", "%" + nonce + "%");
		jdbc.update("DELETE FROM finance_budgets WHERE department_id = ?", departmentId);
		jdbc.update("UPDATE app_settings SET setting_value = 'INR' WHERE setting_key = 'finance.defaultCurrency'");
		jdbc.update("DELETE FROM finance_exchange_rates WHERE currency = ?", TEST_CURRENCY);
		jdbc.update("DELETE FROM vendors WHERE name LIKE ?", "%" + nonce + "%");
		users.deleteAll();
		departmentRepository.deleteById(departmentId);
	}

	@Test
	void licencesCountSeatsConvertCostsToAudAndFollowTheirVendor() throws Exception {
		LocalDate today = calendar.today();
		createRate(today.minusDays(30), "0.50000000");
		Long vendorId = createVendor("Atlassian");
		String body = """
				{"name":"Jira %s","vendorId":%d,"departmentId":%d,"seatsPaid":10,"billingCycle":"MONTHLY","cost":100,
				"currency":"%s","renewalDate":"%s","paymentMethod":"Corporate Amex","cardLast4":"1234",
				"assignedUserIds":[%d,%d]}
				""".formatted(nonce, vendorId, departmentId, TEST_CURRENCY, today.plusDays(20), viewerId, plainId);
		Long licenceId = id(as(adminToken, post(API + "/licences").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.vendor.id").value(vendorId))
			.andExpect(jsonPath("$.seatsUsed").value(2))
			.andExpect(jsonPath("$.seatsFree").value(8))
			.andExpect(jsonPath("$.utilisationPercent").value(20.0))
			.andExpect(jsonPath("$.underUsed").value(true))
			.andExpect(jsonPath("$.annualCost").value(1200.0))
			.andExpect(jsonPath("$.annualCostAud").value(600.0))
			.andExpect(jsonPath("$.audRate.effectiveFrom").value(today.minusDays(30).toString()))
			.andExpect(jsonPath("$.daysUntilRenewal").value(20))
			.andExpect(jsonPath("$.cardLast4").value("1234")));
		as(adminToken, post(API + "/licences").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("LICENCE_NAME_TAKEN"));
		as(adminToken, post(API + "/licences").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Card %s\",\"billingCycle\":\"YEARLY\",\"paymentMethod\":\"Visa 4111 1111 1111 1111\"}"
				.formatted(nonce))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("CARD_NUMBER_NOT_ALLOWED"));

		// FINANCE_VIEW reads; changing needs FINANCE_EDIT; no permission or no switch for the department means no access.
		as(viewerToken, get(API + "/licences").param("search", "atlassian " + nonce)).andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1));
		as(viewerToken, get(API + "/licences").param("search", nonce).param("renewalWithinDays", "30"))
			.andExpect(jsonPath("$.totalElements").value(1));
		as(viewerToken, put(API + "/licences/" + licenceId).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isForbidden());
		as(plainToken, get(API + "/licences")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
		as(outsiderToken, get(API + "/licences")).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("MODULE_DISABLED"));

		// The vendor page lists the licence to finance viewers only, and the vendor stays while the licence uses it.
		as(viewerToken, get("/api/modules/vendors/vendors/" + vendorId + "/links")).andExpect(status().isOk())
			.andExpect(jsonPath("$[0].kind").value("LICENCES"))
			.andExpect(jsonPath("$[0].total").value(1))
			.andExpect(jsonPath("$[0].items[0].detail").value("2 of 10 seats · renews " + today.plusDays(20)));
		as(vendorOnlyToken, get("/api/modules/vendors/vendors/" + vendorId + "/links")).andExpect(status().isOk())
			.andExpect(jsonPath("$").isEmpty());
		as(adminToken, delete("/api/modules/vendors/vendors/" + vendorId)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("VENDOR_IN_USE"));

		// Edits need the current version; seats follow the assigned users.
		String update = """
				{"name":"Jira %s","vendorId":%d,"seatsPaid":10,"billingCycle":"YEARLY","cost":1500,"currency":"AUD",
				"status":"TRIAL","assignedUserIds":[%d],"version":%d}
				""";
		as(adminToken, put(API + "/licences/" + licenceId).contentType(MediaType.APPLICATION_JSON)
			.content(update.formatted(nonce, vendorId, viewerId, 9))).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STALE_UPDATE"));
		as(adminToken, put(API + "/licences/" + licenceId).contentType(MediaType.APPLICATION_JSON)
			.content(update.formatted(nonce, vendorId, viewerId, version(API + "/licences/" + licenceId))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.seatsUsed").value(1))
			.andExpect(jsonPath("$.assignedUsers[0].id").value(viewerId))
			.andExpect(jsonPath("$.annualCostAud").value(1500.0))
			.andExpect(jsonPath("$.audRate.rate").value(1))
			.andExpect(jsonPath("$.status").value("TRIAL"));
		List<String> actions = JsonPath.read(body(as(viewerToken, get("/api/history/FINANCE_LICENCE/" + licenceId)
			.param("size", "100")).andExpect(status().isOk())), "$.content[*].action");
		assertThat(actions).contains("RECORD_CREATED", "RECORD_UPDATED", "LICENCE_CREATED", "LICENCE_UPDATED");

		// Finance Tracker entries are finance data as a whole: an auditor without FINANCE_VIEW sees neither details nor name.
		String audit = body(as(auditorToken, get("/api/admin/audit-logs").param("entityType", "FINANCE_LICENCE")
			.param("entityId", licenceId.toString())).andExpect(status().isOk()));
		assertThat(JsonPath.<List<Object>>read(audit, "$.content[*].details.hidden")).isNotEmpty().containsOnly(true);
		assertThat(JsonPath.<List<Object>>read(audit, "$.content[*].entityLabel")).containsOnlyNulls();
		assertThat(audit).doesNotContain("Jira " + nonce).doesNotContain("1500");
		as(auditorToken, get("/api/admin/audit-logs").param("search", "Jira " + nonce).param("entityType", "FINANCE_LICENCE"))
			.andExpect(jsonPath("$.totalElements").value(0));
		String adminAudit = body(as(adminToken, get("/api/admin/audit-logs").param("entityType", "FINANCE_LICENCE")
			.param("entityId", licenceId.toString())));
		assertThat(adminAudit).contains("Jira " + nonce);

		// The licence goes to the bin with its seats; it comes back only while its vendor is live.
		as(adminToken, delete(API + "/licences/" + licenceId)).andExpect(status().isNoContent());
		as(viewerToken, get(API + "/licences/" + licenceId)).andExpect(status().isNotFound());
		as(adminToken, delete("/api/modules/vendors/vendors/" + vendorId)).andExpect(status().isNoContent());
		Long item = binItem("FINANCE_LICENCE", licenceId);
		as(adminToken, post("/api/recycle-bin/%d/restore".formatted(item))).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESTORE_CONFLICT"));
		as(adminToken, post("/api/recycle-bin/%d/restore".formatted(binItem("VENDOR", vendorId)))).andExpect(status().isOk());
		as(adminToken, post("/api/recycle-bin/%d/restore".formatted(item))).andExpect(status().isOk());
		as(viewerToken, get(API + "/licences/" + licenceId)).andExpect(status().isOk())
			.andExpect(jsonPath("$.seatsUsed").value(1));
	}

	@Test
	void exchangeRatesApplyFromTheirDateAndConvertToAud() throws Exception {
		LocalDate today = calendar.today();
		Long older = createRate(today.minusDays(40), "0.40000000");
		Long newer = createRate(today.minusDays(5), "0.50000000");
		as(adminToken, post(API + "/exchange-rates").contentType(MediaType.APPLICATION_JSON)
			.content(rate(today.minusDays(5), "0.6"))).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RATE_EXISTS"));
		as(adminToken, post(API + "/exchange-rates").contentType(MediaType.APPLICATION_JSON)
			.content("{\"currency\":\"AUD\",\"effectiveFrom\":\"2026-01-01\",\"rateToAud\":1}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BASE_CURRENCY"));
		as(viewerToken, post(API + "/exchange-rates").contentType(MediaType.APPLICATION_JSON)
			.content(rate(today.minusDays(1), "0.7"))).andExpect(status().isForbidden());

		as(viewerToken, get(API + "/exchange-rates/current")).andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.currency == 'XTS')].id").value(contains(newer.intValue())));
		as(viewerToken, get(API + "/exchange-rates").param("currency", "xts")).andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.content[0].id").value(newer))
			.andExpect(jsonPath("$.content[0].inForce").value(true))
			.andExpect(jsonPath("$.content[1].inForce").value(false))
			.andExpect(jsonPath("$.content[0].unitsPerAud").value(2.0));
		as(viewerToken, get(API + "/exchange-rates/convert").param("amount", "100").param("currency", "XTS"))
			.andExpect(jsonPath("$.aud").value(50.0))
			.andExpect(jsonPath("$.rateFrom").value(today.minusDays(5).toString()));
		as(viewerToken, get(API + "/exchange-rates/convert").param("amount", "100").param("currency", "XTS")
			.param("date", today.minusDays(10).toString())).andExpect(jsonPath("$.aud").value(40.0));
		as(viewerToken, get(API + "/exchange-rates/convert").param("amount", "100").param("currency", "XTS")
			.param("date", today.minusDays(100).toString())).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("NO_EXCHANGE_RATE"));

		// A deleted rate hands over to the previous one, and its date stays taken while it is in the bin.
		as(adminToken, delete(API + "/exchange-rates/" + newer)).andExpect(status().isNoContent());
		as(viewerToken, get(API + "/exchange-rates/convert").param("amount", "100").param("currency", "XTS"))
			.andExpect(jsonPath("$.aud").value(40.0));
		as(adminToken, post(API + "/exchange-rates").contentType(MediaType.APPLICATION_JSON)
			.content(rate(today.minusDays(5), "0.55"))).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("IN_RECYCLE_BIN"));
		as(adminToken, get("/api/recycle-bin").param("entityType", "EXCHANGE_RATE").param("size", "100"))
			.andExpect(jsonPath("$.content[?(@.entityId == %d)].label".formatted(newer))
				.value(contains("XTS rate from " + today.minusDays(5))));
		assertThat(older).isNotNull();
	}

	@Test
	void licencesAndRatesImportFromCsvAfterAPreviewAndExportInTheSameColumns() throws Exception {
		createVendor("Canva");
		as(adminToken, get(API + "/imports")).andExpect(status().isOk())
			.andExpect(jsonPath("$[*].type").value(containsInAnyOrder("licences", "exchange-rates")));
		as(adminToken, get(API + "/imports/licences/template")).andExpect(status().isOk())
			.andExpect(content().string(containsString("name,vendor,owner_email,department")));

		String viewerEmail = users.email(viewerId);
		MockMultipartFile file = new MockMultipartFile("file", "licences.csv", "text/csv", """
				name,vendor,seats_paid,billing_cycle,cost,currency,payment_method,card_last4,assigned_users
				Canva Teams %1$s,canva %1$s,5,yearly,"1,200.50",aud,Corporate Amex,4321,%2$s
				Card %1$s,,,MONTHLY,,,Visa 4111 1111 1111 1111,,
				Nobody %1$s,Nobody Ltd %1$s,,YEARLY,,,,,
				""".formatted(nonce, viewerEmail).getBytes(StandardCharsets.UTF_8));
		as(viewerToken, multipart(API + "/imports/licences/preview").file(file)).andExpect(status().isForbidden());
		as(outsiderToken, multipart(API + "/imports/licences/preview").file(file)).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("MODULE_DISABLED"));
		String preview = body(as(adminToken, multipart(API + "/imports/licences/preview").file(file))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.validCount").value(1))
			.andExpect(jsonPath("$.invalidCount").value(2))
			.andExpect(jsonPath("$.invalidRows[0].errors[0].column").value("payment_method"))
			.andExpect(jsonPath("$.invalidRows[1].errors[0].message").value("No vendor has this name")));
		as(adminToken, multipart(API + "/imports/licences/commit").file(file)
			.param("checksum", JsonPath.<String>read(preview, "$.checksum"))
			.param("skipInvalid", "true")).andExpect(status().isOk()).andExpect(jsonPath("$.imported").value(1));
		as(viewerToken, get(API + "/licences").param("search", "Canva Teams " + nonce))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].seatsUsed").value(1))
			.andExpect(jsonPath("$.content[0].cost").value(1200.5));
		as(viewerToken, get(API + "/licences/export").param("search", nonce)).andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("licences.csv")))
			.andExpect(content().string(containsString("Canva Teams " + nonce + ",Canva " + nonce + ",,,5,YEARLY,1200.50,AUD,,,"
					+ "Corporate Amex,4321,ACTIVE," + viewerEmail + ",")));

		MockMultipartFile rates = new MockMultipartFile("file", "rates.csv", "text/csv", """
				currency,effective_from,rate_to_aud,source
				xts,2020-01-01,0.25,Test
				AUD,2020-01-01,1,
				""".getBytes(StandardCharsets.UTF_8));
		String ratePreview = body(as(adminToken, multipart(API + "/imports/exchange-rates/preview").file(rates))
			.andExpect(jsonPath("$.validCount").value(1))
			.andExpect(jsonPath("$.invalidRows[0].errors[0].message").value("AUD is the base currency: its rate is always 1")));
		as(adminToken, multipart(API + "/imports/exchange-rates/commit").file(rates)
			.param("checksum", JsonPath.<String>read(ratePreview, "$.checksum"))
			.param("skipInvalid", "true")).andExpect(jsonPath("$.imported").value(1));
		as(viewerToken, get(API + "/exchange-rates/export").param("currency", "XTS"))
			.andExpect(content().string(containsString("XTS,2020-01-01,0.25000000,Test")));
	}

	@Test
	void renewalRemindersReachThoseWhoMayOpenTheModuleAndSearchShowsLicencesToThemOnly() throws Exception {
		LocalDate today = calendar.today();
		Long soon = createLicence("Slack", today.plusDays(5), "ACTIVE");
		Long trial = createLicence("Figma", today.plusDays(25), "TRIAL");
		createLicence("Zoom", today.plusDays(5), "CANCELLED");
		createLicence("Miro", today.plusDays(60), "ACTIVE");

		reminders.sendReminders();
		reminders.sendReminders();
		String sevenDay = "LICENCE_RENEWAL:" + soon + ":" + today.plusDays(5) + ":7:%d";
		String thirtyDay = "LICENCE_RENEWAL:" + trial + ":" + today.plusDays(25) + ":30:%d";
		assertThat(reminderCount(sevenDay.formatted(viewerId))).isEqualTo(1);
		assertThat(reminderCount(thirtyDay.formatted(viewerId))).isEqualTo(1);
		assertThat(reminderCount(sevenDay.formatted(plainId))).isZero();
		assertThat(reminderCount(sevenDay.formatted(outsiderId))).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE type = 'LICENCE_RENEWAL' AND user_id = ? "
				+ "AND entity_id IN (SELECT id FROM finance_licences WHERE name LIKE ?)", Integer.class, viewerId,
				"%" + nonce + "%")).isEqualTo(2);

		for (String token : List.of(adminToken, viewerToken)) {
			assertThat(searchGroups(token)).contains("LICENCES");
		}
		for (String token : List.of(outsiderToken, plainToken, vendorOnlyToken)) {
			assertThat(searchGroups(token)).doesNotContain("LICENCES");
		}
	}

	@Test
	void expensesKeepTheAudRateOfTheirDateAndStartInTheDefaultCurrency() throws Exception {
		LocalDate today = calendar.today();
		String before = body(as(viewerToken, get(API + "/dashboard")).andExpect(status().isOk()));
		createRate(today.minusDays(40), "0.40000000");
		createRate(today.minusDays(5), "0.50000000");

		// The default currency is a setting, chosen from INR, AUD, USD and the currencies with a rate.
		String settings = body(as(adminToken, get("/api/admin/settings")).andExpect(status().isOk()));
		List<String> options = JsonPath.read(settings, "$[?(@.key == 'finance.defaultCurrency')].options[*]");
		assertThat(options).startsWith("INR", "AUD", "USD").contains(TEST_CURRENCY);
		int settingVersion = JsonPath.<List<Integer>>read(settings, "$[?(@.key == 'finance.defaultCurrency')].version").get(0);
		as(adminToken, put("/api/admin/settings/finance.defaultCurrency").contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":%d,\"value\":\"EUR\"}".formatted(settingVersion))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_SETTING"));
		as(adminToken, put("/api/admin/settings/finance.defaultCurrency").contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":%d,\"value\":\"xts\"}".formatted(settingVersion))).andExpect(status().isOk())
			.andExpect(jsonPath("$.value").value(TEST_CURRENCY));
		as(viewerToken, get(API + "/context")).andExpect(jsonPath("$.defaultCurrency").value(TEST_CURRENCY))
			.andExpect(jsonPath("$.baseCurrency").value("AUD"));
		as(adminToken, post(API + "/licences").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Default %s\",\"billingCycle\":\"YEARLY\"}".formatted(nonce))).andExpect(status().isCreated())
			.andExpect(jsonPath("$.currency").value(TEST_CURRENCY));

		// An expense without a currency takes the default, and the rate in force on its date.
		Long vendorId = createVendor("Qantas");
		Long expenseId = id(as(adminToken, post(API + "/expenses").contentType(MediaType.APPLICATION_JSON).content("""
				{"expenseDate":"%s","category":"TRAVEL","vendorId":%d,"description":"Flights %s","amount":100,"gst":9.09,
				"departmentId":%d}
				""".formatted(today.minusDays(10), vendorId, nonce, departmentId))).andExpect(status().isCreated())
			.andExpect(jsonPath("$.currency").value(TEST_CURRENCY))
			.andExpect(jsonPath("$.audRate").value(0.4))
			.andExpect(jsonPath("$.audRateDate").value(today.minusDays(40).toString()))
			.andExpect(jsonPath("$.amountAud").value(40.0))
			.andExpect(jsonPath("$.gstAud").value(3.64))
			.andExpect(jsonPath("$.vendor.id").value(vendorId)));

		// A new rate for that date later changes nothing stored; a new amount keeps the rate; a new date takes it again.
		createRate(today.minusDays(12), "0.45000000");
		as(viewerToken, get(API + "/expenses/" + expenseId)).andExpect(jsonPath("$.audRate").value(0.4));
		String update = """
				{"expenseDate":"%s","category":"TRAVEL","vendorId":%d,"description":"Flights %s","amount":%s,
				"departmentId":%d,"version":%d}
				""";
		as(adminToken, put(API + "/expenses/" + expenseId).contentType(MediaType.APPLICATION_JSON)
			.content(update.formatted(today.minusDays(10), vendorId, nonce, "200", departmentId,
					version(API + "/expenses/" + expenseId)))).andExpect(status().isOk())
			.andExpect(jsonPath("$.audRate").value(0.4))
			.andExpect(jsonPath("$.amountAud").value(80.0));
		as(adminToken, put(API + "/expenses/" + expenseId).contentType(MediaType.APPLICATION_JSON)
			.content(update.formatted(today, vendorId, nonce, "200", departmentId, version(API + "/expenses/" + expenseId))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.audRate").value(0.5))
			.andExpect(jsonPath("$.amountAud").value(100.0));
		as(adminToken, post(API + "/expenses").contentType(MediaType.APPLICATION_JSON)
			.content("{\"expenseDate\":\"%s\",\"category\":\"OFFICE\",\"description\":\"Old %s\",\"amount\":5}"
				.formatted(today.minusDays(400), nonce))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("NO_EXCHANGE_RATE"));
		Long audExpense = id(as(adminToken, post(API + "/expenses").contentType(MediaType.APPLICATION_JSON).content("""
				{"expenseDate":"%s","category":"OFFICE","description":"Coffee %s","amount":50,"currency":"AUD",
				"departmentId":%d}
				""".formatted(today, nonce, departmentId))).andExpect(status().isCreated())
			.andExpect(jsonPath("$.audRate").value(1.0))
			.andExpect(jsonPath("$.audRateDate").doesNotExist())
			.andExpect(jsonPath("$.amountAud").value(50.0)));
		as(viewerToken, post(API + "/expenses").contentType(MediaType.APPLICATION_JSON)
			.content("{\"expenseDate\":\"%s\",\"category\":\"OFFICE\",\"description\":\"No %s\",\"amount\":5}"
				.formatted(today, nonce))).andExpect(status().isForbidden());

		// The receipt: uploaded by editors, served as an attachment.
		MockMultipartFile pdf = new MockMultipartFile("file", "flight receipt.pdf", "application/pdf",
				"%PDF-1.4\n%receipt\n".getBytes(StandardCharsets.UTF_8));
		as(adminToken, multipart(API + "/expenses/" + expenseId + "/receipt").file(pdf)).andExpect(status().isOk())
			.andExpect(jsonPath("$.receipt.fileName").value("flight receipt.pdf"));
		as(viewerToken, get(API + "/expenses/" + expenseId + "/receipt")).andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"));

		// The vendor page lists the expense to finance viewers only; the vendor stays while the expense uses it.
		as(viewerToken, get("/api/modules/vendors/vendors/" + vendorId + "/links"))
			.andExpect(jsonPath("$[?(@.kind == 'EXPENSES')].total").value(contains(1)));
		as(vendorOnlyToken, get("/api/modules/vendors/vendors/" + vendorId + "/links")).andExpect(jsonPath("$").isEmpty());
		as(adminToken, delete("/api/modules/vendors/vendors/" + vendorId)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("VENDOR_IN_USE"));

		// The month's budget against actual: 100 + 50 against 120.
		Long budgetId = id(as(adminToken, post(API + "/budgets").contentType(MediaType.APPLICATION_JSON)
			.content(budget(today, "120"))).andExpect(status().isCreated())
			.andExpect(jsonPath("$.actual").value(150.0))
			.andExpect(jsonPath("$.overBudget").value(true))
			.andExpect(jsonPath("$.overBy").value(30.0))
			.andExpect(jsonPath("$.usedPercent").value(125.0))
			.andExpect(jsonPath("$.remaining").value(0.0)));
		as(adminToken, post(API + "/budgets").contentType(MediaType.APPLICATION_JSON).content(budget(today, "500")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("BUDGET_EXISTS"));
		as(adminToken, put(API + "/budgets/" + budgetId).contentType(MediaType.APPLICATION_JSON)
			.content("{\"departmentId\":%d,\"year\":%d,\"month\":%d,\"amount\":200,\"version\":%d}".formatted(departmentId,
					today.getYear(), today.getMonthValue(), version(API + "/budgets/" + budgetId))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.remaining").value(50.0))
			.andExpect(jsonPath("$.overBudget").value(false));
		as(viewerToken, get(API + "/budgets/summary").param("year", String.valueOf(today.getYear()))
			.param("month", String.valueOf(today.getMonthValue())))
			.andExpect(jsonPath("$.lines[?(@.department.id == %d)].actual".formatted(departmentId))
				.value(contains(150.0)))
			.andExpect(jsonPath("$.lines[?(@.department.id == %d)].budget".formatted(departmentId))
				.value(contains(200.0)));

		// The dashboard counts both expenses in this month's spend and by department.
		String after = body(as(viewerToken, get(API + "/dashboard")).andExpect(status().isOk()));
		assertThat(new BigDecimal(String.valueOf(JsonPath.<Object>read(after, "$.monthSpendAud")))
			.subtract(new BigDecimal(String.valueOf(JsonPath.<Object>read(before, "$.monthSpendAud")))))
			.isEqualByComparingTo("150");
		assertThat(JsonPath.<List<Object>>read(after, "$.byDepartment[?(@.department.id == %d)].totalAud".formatted(departmentId)))
			.singleElement().satisfies(total -> assertThat(String.valueOf(total)).startsWith("150"));
		assertThat(JsonPath.<List<Object>>read(after, "$.trend")).hasSize(12);

		// In the bin, someone without FINANCE_VIEW sees neither the expense's name nor finds it by name.
		as(adminToken, delete(API + "/expenses/" + audExpense)).andExpect(status().isNoContent());
		Long item = binItem("FINANCE_EXPENSE", audExpense);
		as(binManagerToken, get("/api/recycle-bin").param("entityType", "FINANCE_EXPENSE").param("size", "100"))
			.andExpect(jsonPath("$.content[?(@.id == %d)].label".formatted(item)).value(contains(
					nullValue())))
			.andExpect(jsonPath("$.content[?(@.id == %d)].confirmationText".formatted(item))
				.value(contains("Finance expense #" + audExpense)));
		as(binManagerToken, get("/api/recycle-bin").param("search", "Coffee " + nonce))
			.andExpect(jsonPath("$.totalElements").value(0));
		as(adminToken, get("/api/recycle-bin").param("search", "Coffee " + nonce))
			.andExpect(jsonPath("$.content[0].label").value(today + " Coffee " + nonce));

		// A purged expense takes its receipt file with it.
		Long fileId = jdbc.queryForObject("SELECT receipt_file_id FROM finance_expenses WHERE id = ?", Long.class, expenseId);
		as(adminToken, delete(API + "/expenses/" + expenseId)).andExpect(status().isNoContent());
		as(adminToken, post("/api/recycle-bin/%d/purge".formatted(binItem("FINANCE_EXPENSE", expenseId)))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"confirm\":\"%s Flights %s\"}".formatted(today, nonce))).andExpect(status().isNoContent());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM files WHERE id = ?", Integer.class, fileId)).isZero();
	}

	@Test
	void exportingAllDataHasEveryFinanceTableAndReceiptOnlyForExportersWithFinanceView() throws Exception {
		LocalDate today = calendar.today();
		Long licenceId = id(as(adminToken, post(API + "/licences").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Export %s\",\"billingCycle\":\"YEARLY\",\"assignedUserIds\":[%d]}".formatted(nonce,
					viewerId))).andExpect(status().isCreated()));
		createRate(today.minusDays(3), "0.42000000");
		Long expenseId = id(as(adminToken, post(API + "/expenses").contentType(MediaType.APPLICATION_JSON).content("""
				{"expenseDate":"%s","category":"OFFICE","description":"Export %s","amount":12.5,"currency":"AUD",
				"departmentId":%d}
				""".formatted(today, nonce, departmentId))).andExpect(status().isCreated()));
		as(adminToken, post(API + "/budgets").contentType(MediaType.APPLICATION_JSON).content(budget(today, "321")))
			.andExpect(status().isCreated());
		String receiptName = "export receipt " + nonce + ".pdf";
		uploadReceipt(expenseId, receiptName);
		String key = jdbc.queryForObject("SELECT f.storage_key FROM files f JOIN finance_expenses e ON e.receipt_file_id = f.id "
				+ "WHERE e.id = ?", String.class, expenseId);
		String[] financeTables = { "tables/finance_licences.csv", "tables/finance_licence_assignments.csv",
				"tables/finance_exchange_rates.csv", "tables/finance_expenses.csv", "tables/finance_budgets.csv" };

		// Without FINANCE_VIEW: no finance tables and no receipt (its file, its files row, its name in audit entries).
		Map<String, String> hidden = unzip(as(exporterToken, get("/api/admin/data-export")).andExpect(status().isOk())
			.andReturn().getResponse().getContentAsByteArray());
		assertThat(hidden).doesNotContainKeys(financeTables).doesNotContainKey("uploads/" + key);
		assertThat(hidden).containsKeys("tables/vendors.csv", "tables/audit_logs.csv", "tables/files.csv");
		assertThat(hidden.get("tables/files.csv")).doesNotContain(receiptName);
		assertThat(hidden.get("tables/audit_logs.csv")).doesNotContain("Export " + nonce).doesNotContain(receiptName)
			.contains("[finance]");
		assertThat(hidden.get("README.txt")).contains("Finance data is left out");

		// With FINANCE_VIEW (the Super Admin, or anyone granted it): every finance table and the receipt, unredacted.
		for (String token : List.of(adminToken, financeExporterToken)) {
			Map<String, String> full = unzip(as(token, get("/api/admin/data-export")).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsByteArray());
			assertThat(full).containsKeys(financeTables).containsKey("uploads/" + key);
			assertThat(full.get("uploads/" + key)).startsWith("%PDF-1.4");
			assertThat(full.get("tables/finance_licences.csv")).contains("Export " + nonce);
			assertThat(full.get("tables/finance_licence_assignments.csv")).contains(String.valueOf(licenceId));
			assertThat(full.get("tables/finance_exchange_rates.csv")).contains(TEST_CURRENCY, "0.42");
			assertThat(full.get("tables/finance_expenses.csv")).contains("Export " + nonce, "12.50");
			assertThat(full.get("tables/finance_budgets.csv")).contains("321.00");
			assertThat(full.get("tables/files.csv")).contains(receiptName, key);
			assertThat(full.get("tables/audit_logs.csv")).contains("Export " + nonce, receiptName)
				.doesNotContain("[finance]");
			assertThat(full.get("README.txt")).doesNotContain("Finance data is left out");
		}
	}

	@Test
	void receiptFileNamesStayHiddenWithoutFinanceViewInFileHistoryTheBinAndTheAuditLog() throws Exception {
		LocalDate today = calendar.today();
		Long expenseId = id(as(adminToken, post(API + "/expenses").contentType(MediaType.APPLICATION_JSON).content("""
				{"expenseDate":"%s","category":"OFFICE","description":"Receipt %s","amount":20,"currency":"AUD"}
				""".formatted(today, nonce))).andExpect(status().isCreated()));
		// Replacing the receipt deletes the first file and removing it the second; the third goes with the purge.
		Long firstFile = uploadReceipt(expenseId, "first receipt " + nonce + ".pdf");
		Long secondFile = uploadReceipt(expenseId, "second receipt " + nonce + ".pdf");
		as(adminToken, delete(API + "/expenses/" + expenseId + "/receipt")).andExpect(status().isOk());
		Long thirdFile = uploadReceipt(expenseId, "third receipt " + nonce + ".pdf");
		as(adminToken, delete(API + "/expenses/" + expenseId)).andExpect(status().isNoContent());

		// The recycle bin shows neither the expense's name nor its receipt's, and finds neither.
		Long item = binItem("FINANCE_EXPENSE", expenseId);
		as(binManagerToken, get("/api/recycle-bin").param("search", "receipt " + nonce))
			.andExpect(jsonPath("$.totalElements").value(0));
		assertThat(body(as(binManagerToken, get("/api/recycle-bin").param("size", "100")).andExpect(status().isOk())))
			.doesNotContain("receipt " + nonce);
		as(adminToken, post("/api/recycle-bin/%d/purge".formatted(item)).contentType(MediaType.APPLICATION_JSON)
			.content("{\"confirm\":\"%s Receipt %s\"}".formatted(today, nonce))).andExpect(status().isNoContent());

		for (Long fileId : List.of(firstFile, secondFile, thirdFile)) {
			// Each file's created and deleted entries are filed under the expense.
			assertThat(jdbc.queryForList("SELECT action FROM audit_logs WHERE entity_type = 'FILE' AND entity_id = ? "
					+ "AND parent_type = 'FINANCE_EXPENSE' AND parent_id = ?", String.class, fileId, expenseId))
				.contains("RECORD_CREATED", "RECORD_DELETED");
			// The file's history and audit entries: no name and no details without FINANCE_VIEW.
			for (String audit : List.of(
					body(as(auditorToken, get("/api/history/FILE/" + fileId)).andExpect(status().isOk())),
					body(as(auditorToken, get("/api/admin/audit-logs").param("entityType", "FILE")
						.param("entityId", String.valueOf(fileId))).andExpect(status().isOk())))) {
				assertThat(JsonPath.<List<Object>>read(audit, "$.content[*].details.hidden")).hasSizeGreaterThanOrEqualTo(2)
					.containsOnly(true);
				assertThat(JsonPath.<List<Object>>read(audit, "$.content[*].entityLabel")).containsOnlyNulls();
				assertThat(audit).doesNotContain("receipt " + nonce);
			}
		}
		// The Super Admin sees the names.
		assertThat(body(as(adminToken, get("/api/history/FILE/" + firstFile)).andExpect(status().isOk())))
			.contains("first receipt " + nonce + ".pdf");

		// Searching the audit log by the names finds nothing, and its CSV export leaves them out.
		as(auditorToken, get("/api/admin/audit-logs").param("search", "receipt " + nonce))
			.andExpect(jsonPath("$.totalElements").value(0));
		assertThat(JsonPath.<Integer>read(body(as(adminToken, get("/api/admin/audit-logs")
			.param("search", "second receipt " + nonce))), "$.totalElements")).isPositive();
		String csv = as(auditorToken, get("/api/admin/audit-logs/export").param("entityType", "FILE"))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertThat(csv).doesNotContain("receipt " + nonce);
	}

	private Long uploadReceipt(Long expenseId, String name) throws Exception {
		String body = body(as(adminToken, multipart(API + "/expenses/" + expenseId + "/receipt")
			.file(new MockMultipartFile("file", name, "application/pdf", "%PDF-1.4\n".getBytes(StandardCharsets.UTF_8))))
			.andExpect(status().isOk()));
		return ((Number) JsonPath.read(body, "$.receipt.fileId")).longValue();
	}

	// --- helpers ----------------------------------------------------------------------------------------------

	private String budget(LocalDate month, String amount) {
		return "{\"departmentId\":%d,\"year\":%d,\"month\":%d,\"amount\":%s}".formatted(departmentId, month.getYear(),
				month.getMonthValue(), amount);
	}

	/** Every entry of a ZIP: text entries as UTF-8, others as a placeholder. */
	private static Map<String, String> unzip(byte[] zip) throws IOException {
		Map<String, String> entries = new HashMap<>();
		try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
			for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
				entries.put(entry.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
			}
		}
		return entries;
	}

	private Long createVendor(String name) throws Exception {
		return id(as(adminToken, post("/api/modules/vendors/vendors").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"%s %s\",\"category\":\"SOFTWARE\"}".formatted(name, nonce))).andExpect(status().isCreated()));
	}

	private Long createLicence(String name, LocalDate renewal, String status) throws Exception {
		return id(as(adminToken, post(API + "/licences").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"%s %s\",\"billingCycle\":\"YEARLY\",\"renewalDate\":\"%s\",\"status\":\"%s\"}"
				.formatted(name, nonce, renewal, status))).andExpect(status().isCreated()));
	}

	private Long createRate(LocalDate from, String rateToAud) throws Exception {
		return id(as(adminToken, post(API + "/exchange-rates").contentType(MediaType.APPLICATION_JSON)
			.content(rate(from, rateToAud))).andExpect(status().isCreated()));
	}

	private static String rate(LocalDate from, String rateToAud) {
		return "{\"currency\":\"%s\",\"effectiveFrom\":\"%s\",\"rateToAud\":%s}".formatted(TEST_CURRENCY, from, rateToAud);
	}

	private int reminderCount(String dedupKey) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE type = 'LICENCE_RENEWAL' AND dedup_key = ?",
				Integer.class, dedupKey);
	}

	private List<String> searchGroups(String token) throws Exception {
		return JsonPath.read(body(as(token, get("/api/search").param("q", nonce)).andExpect(status().isOk())),
				"$.groups[*].group");
	}

	private int version(String path) throws Exception {
		return JsonPath.read(body(as(adminToken, get(path)).andExpect(status().isOk())), "$.version");
	}

	private Long binItem(String type, Long entityId) throws Exception {
		String body = body(as(adminToken, get("/api/recycle-bin").param("entityType", type).param("size", "100"))
			.andExpect(status().isOk()));
		List<Integer> ids = JsonPath.read(body, "$.content[?(@.entityId == %d)].id".formatted(entityId));
		assertThat(ids).as(type + " #" + entityId + " in the bin").hasSize(1);
		return ids.get(0).longValue();
	}

	private static String body(ResultActions result) throws Exception {
		return result.andReturn().getResponse().getContentAsString();
	}

	private static Long id(ResultActions result) throws Exception {
		return ((Number) JsonPath.read(body(result), "$.id")).longValue();
	}

	private ResultActions as(String token, AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
		return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private String login(Long userId) throws Exception {
		String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(users.email(userId), IntegrationUsers.PASSWORD)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.accessToken");
	}

}
