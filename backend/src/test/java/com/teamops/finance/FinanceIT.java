package com.teamops.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

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
		// Reads the audit log but not finance data.
		auditorToken = login(users.create(departmentId, RoleCodes.EMPLOYEE, "AUDIT_VIEW"));
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
			.andExpect(jsonPath("$[?(@.currency == 'XTS')].id").value(org.hamcrest.Matchers.contains(newer.intValue())));
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
				.value(org.hamcrest.Matchers.contains("XTS rate from " + today.minusDays(5))));
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

	// --- helpers ----------------------------------------------------------------------------------------------

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
