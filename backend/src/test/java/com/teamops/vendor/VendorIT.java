package com.teamops.vendor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.not;
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

import java.math.BigDecimal;
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
import com.teamops.support.IntegrationUsers;
import com.teamops.user.entity.RoleCodes;
import com.teamops.user.repository.UserRepository;
import com.teamops.user.service.UserService;
import com.teamops.vendor.service.VendorReminderService;

/**
 * Vendor Management (Phase X7) end to end against MySQL, in a throwaway department with the module switched on:
 * vendors and contacts, contracts with documents, permissions and the module switch, audit history (contract values
 * hidden without FINANCE_VIEW), the recycle bin, CSV import and export, renewal reminders and global search.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = { "app.dev-seed.enabled=false", "app.storage.dir=target/it-uploads" })
class VendorIT {

	private static final String API = "/api/modules/vendors";

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
	private VendorReminderService reminders;

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

	private String editorToken;

	@BeforeEach
	void setUp() throws Exception {
		users = new IntegrationUsers(userService, userRepository, departmentRepository);
		Long adminId = users.create("IT", RoleCodes.SUPER_ADMIN);
		adminToken = login(adminId);
		nonce = Long.toHexString(System.nanoTime()).toUpperCase();
		String code = "VEND_" + nonce;
		departmentId = id(as(adminToken, post("/api/departments").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Vendors %s\",\"code\":\"%s\"}".formatted(code, code))).andExpect(status().isCreated()));
		as(adminToken, put("/api/admin/modules/vendors/departments/" + departmentId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"enabled\":true}")).andExpect(status().isOk());

		// Reads vendors and record history, but cannot see finance amounts.
		viewerId = users.create(departmentId, RoleCodes.EMPLOYEE, "VENDOR_VIEW", "HISTORY_VIEW");
		viewerToken = login(viewerId);
		// Edits vendors and contracts, but cannot see finance amounts either.
		editorToken = login(users.create(departmentId, RoleCodes.EMPLOYEE, "VENDOR_VIEW", "VENDOR_EDIT"));
		plainId = users.create(departmentId, RoleCodes.EMPLOYEE);
		plainToken = login(plainId);
		// Holds both permissions, but the module is not switched on for the IT department.
		outsiderId = users.create("IT", RoleCodes.EMPLOYEE, "VENDOR_VIEW", "VENDOR_EDIT");
		outsiderToken = login(outsiderId);
	}

	@AfterEach
	void cleanUp() {
		// Deleted rows are invisible to the repositories, so clean up in SQL. Contacts cascade from vendors, documents
		// from their files.
		String vendors = "SELECT id FROM vendors WHERE name LIKE '%" + nonce + "%'";
		String contracts = "SELECT id FROM vendor_contracts WHERE vendor_id IN (" + vendors + ")";
		jdbc.update("DELETE FROM notifications WHERE entity_type = 'VENDOR_CONTRACT' AND entity_id IN (" + contracts + ")");
		jdbc.update("DELETE FROM recycle_bin_items WHERE entity_type = 'VENDOR_CONTRACT' AND entity_id IN (" + contracts
				+ ")");
		jdbc.update("DELETE FROM recycle_bin_items WHERE entity_type = 'VENDOR' AND entity_id IN (" + vendors + ")");
		jdbc.update("DELETE FROM files WHERE id IN (SELECT file_id FROM (SELECT d.file_id FROM vendor_contract_documents d "
				+ "WHERE d.contract_id IN (" + contracts + ")) f)");
		jdbc.update("DELETE FROM vendor_contracts WHERE vendor_id IN (SELECT id FROM (" + vendors + ") v)");
		jdbc.update("DELETE FROM vendors WHERE name LIKE ?", "%" + nonce + "%");
		users.deleteAll();
		departmentRepository.deleteById(departmentId);
	}

	@Test
	void vendorsKeepNamesUniqueAuditTheirContactsAndGoThroughTheRecycleBin() throws Exception {
		Long vendorId = id(as(adminToken, post(API + "/vendors").contentType(MediaType.APPLICATION_JSON).content("""
				{"name":"Atlassian %s","taxId":"53 102 443 916","category":"SOFTWARE","country":"Australia",
				"website":"https://www.atlassian.com","paymentTerms":"Net 30","rating":4,
				"contacts":[{"name":"Priya Shah","role":"Account Executive","email":"Priya@Atlassian.example"}]}
				""".formatted(nonce))).andExpect(status().isCreated())
			.andExpect(jsonPath("$.category").value("SOFTWARE"))
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.contacts[0].email").value("priya@atlassian.example"))
			.andExpect(jsonPath("$.contractCount").value(0)));
		as(adminToken, post(API + "/vendors").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"ATLASSIAN %s\"}".formatted(nonce))).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("VENDOR_NAME_TAKEN"));
		as(adminToken, post(API + "/vendors").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Canva %s\",\"website\":\"canva.com\"}".formatted(nonce))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_WEBSITE"));

		// VENDOR_VIEW reads; changing needs VENDOR_EDIT; no permission or no switch for the department means no access.
		as(viewerToken, get(API + "/vendors").param("search", "priya@atlassian")).andExpect(status().isOk())
			.andExpect(jsonPath("$.content[?(@.id == %d)]".formatted(vendorId)).isNotEmpty());
		as(viewerToken, get(API + "/vendors").param("search", nonce).param("category", "HARDWARE"))
			.andExpect(jsonPath("$.totalElements").value(0));
		as(viewerToken, get(API + "/vendors/" + vendorId + "/links")).andExpect(status().isOk())
			.andExpect(jsonPath("$").isEmpty());
		as(viewerToken, delete(API + "/vendors/" + vendorId)).andExpect(status().isForbidden());
		as(plainToken, get(API + "/vendors")).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		as(outsiderToken, get(API + "/vendors")).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("MODULE_DISABLED"));

		// Edits need the current version; contacts are matched by id.
		Long contactId = ((Number) JsonPath.read(body(as(adminToken, get(API + "/vendors/" + vendorId))),
				"$.contacts[0].id")).longValue();
		String update = """
				{"name":"Atlassian %s","category":"SOFTWARE","status":"INACTIVE","rating":3,"version":%d,
				"contacts":[{"name":"Meera Iyer","role":"Billing"},{"id":%d,"name":"Priya Shah","role":"Renewals"}]}
				""";
		as(adminToken, put(API + "/vendors/" + vendorId).contentType(MediaType.APPLICATION_JSON)
			.content(update.formatted(nonce, 9, contactId))).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STALE_UPDATE"));
		as(adminToken, put(API + "/vendors/" + vendorId).contentType(MediaType.APPLICATION_JSON)
			.content(update.formatted(nonce, version(API + "/vendors/" + vendorId), contactId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("INACTIVE"))
			.andExpect(jsonPath("$.contacts[0].name").value("Meera Iyer"))
			.andExpect(jsonPath("$.contacts[1].id").value(contactId))
			.andExpect(jsonPath("$.contacts[1].role").value("Renewals"));
		assertThat(jdbc.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(details, '$.changes.status.to')) FROM audit_logs "
				+ "WHERE action = 'VENDOR_UPDATED' AND entity_id = ?", String.class, vendorId)).isEqualTo("INACTIVE");
		List<String> actions = JsonPath.read(body(as(viewerToken, get("/api/history/VENDOR/" + vendorId).param("size", "100"))
			.andExpect(status().isOk())), "$.content[*].action");
		assertThat(actions).contains("RECORD_CREATED", "RECORD_UPDATED", "VENDOR_CREATED", "VENDOR_UPDATED");

		// A vendor with contracts stays; without them it goes to the bin and its name stays taken until purged.
		Long contractId = createContract(vendorId, "Jira Cloud", "\"2026-01-01\"", "null", "null");
		as(adminToken, delete(API + "/vendors/" + vendorId)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("VENDOR_HAS_CONTRACTS"));
		as(adminToken, delete(API + "/contracts/" + contractId)).andExpect(status().isNoContent());
		as(adminToken, delete(API + "/vendors/" + vendorId)).andExpect(status().isNoContent());
		as(viewerToken, get(API + "/vendors/" + vendorId)).andExpect(status().isNotFound());
		as(adminToken, post(API + "/vendors").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"atlassian %s\"}".formatted(nonce))).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("IN_RECYCLE_BIN"));

		// A contract comes back only with its vendor; the vendor comes back with its contacts.
		Long contractItem = binItem("VENDOR_CONTRACT", contractId);
		as(adminToken, post("/api/recycle-bin/%d/restore".formatted(contractItem))).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESTORE_CONFLICT"));
		as(adminToken, get("/api/recycle-bin").param("entityType", "VENDOR").param("search", nonce))
			.andExpect(jsonPath("$.content[0].label").value("Atlassian " + nonce))
			.andExpect(jsonPath("$.content[0].module").value("VENDORS"));
		as(adminToken, post("/api/recycle-bin/%d/restore".formatted(binItem("VENDOR", vendorId)))).andExpect(status().isOk());
		as(adminToken, post("/api/recycle-bin/%d/restore".formatted(contractItem))).andExpect(status().isOk());
		as(viewerToken, get(API + "/vendors/" + vendorId)).andExpect(status().isOk())
			.andExpect(jsonPath("$.contacts.length()").value(2))
			.andExpect(jsonPath("$.contractCount").value(1));
	}

	@Test
	void contractsComputeTheirRenewalKeepDocumentsAndHideTheirValueFromNonFinanceHistory() throws Exception {
		LocalDate today = calendar.today();
		Long vendorId = createVendor("Microsoft");
		Long contractId = id(as(adminToken, post(API + "/contracts").contentType(MediaType.APPLICATION_JSON).content("""
				{"vendorId":%d,"title":"Microsoft 365 E3","startDate":"2026-01-01","endDate":"%s","value":42000,
				"currency":"aud","renewalDate":"%s","autoRenew":true,"noticePeriodDays":30}
				""".formatted(vendorId, today.plusDays(40), today.plusDays(20)))).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.currency").value("AUD"))
			.andExpect(jsonPath("$.value").value(42000.0))
			.andExpect(jsonPath("$.renewsOn").value(today.plusDays(20).toString()))
			.andExpect(jsonPath("$.daysUntilRenewal").value(20))
			.andExpect(jsonPath("$.noticeBy").value(today.minusDays(10).toString())));
		as(adminToken, post(API + "/contracts").contentType(MediaType.APPLICATION_JSON)
			.content(contract(vendorId, "microsoft 365 e3", "\"2026-01-01\"", "null", "null")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONTRACT_TITLE_TAKEN"));
		as(adminToken, post(API + "/contracts").contentType(MediaType.APPLICATION_JSON)
			.content(contract(vendorId, "Backwards", "\"2026-02-01\"", "\"2026-01-01\"", "null")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_DATES"));
		Long expired = createContract(vendorId, "Old support", "\"2020-01-01\"", "\"2020-12-31\"", "null");

		// The value is finance data: hidden on the pages, in exports and from sorting without FINANCE_VIEW.
		as(viewerToken, get(API + "/contracts/" + contractId)).andExpect(status().isOk())
			.andExpect(jsonPath("$.value").doesNotExist())
			.andExpect(jsonPath("$.valueHidden").value(true))
			.andExpect(jsonPath("$.currency").value("AUD"));
		as(adminToken, get(API + "/contracts/" + contractId)).andExpect(jsonPath("$.value").value(42000.0))
			.andExpect(jsonPath("$.valueHidden").value(false));
		as(viewerToken, get(API + "/contracts").param("vendorId", vendorId.toString()))
			.andExpect(jsonPath("$.content[*].value").value(everyItem(nullValue())));
		as(viewerToken, get(API + "/contracts").param("sort", "value,desc")).andExpect(status().isBadRequest());
		as(adminToken, get(API + "/contracts").param("sort", "value,desc")).andExpect(status().isOk());
		as(viewerToken, get(API + "/contracts/export").param("vendorId", vendorId.toString()))
			.andExpect(content().string(containsString("Microsoft 365 E3,2026-01-01," + today.plusDays(40) + ",Hidden,AUD")))
			.andExpect(content().string(not(containsString("42000"))));
		as(adminToken, get(API + "/contracts/export").param("vendorId", vendorId.toString()))
			.andExpect(content().string(containsString(",42000.00,AUD,")));
		assertThat(body(as(viewerToken, get("/api/search").param("q", nonce))).contains("42000")).isFalse();

		// An editor who cannot see the value keeps it when saving the contract.
		as(editorToken, put(API + "/contracts/" + contractId).contentType(MediaType.APPLICATION_JSON).content("""
				{"vendorId":%d,"title":"Microsoft 365 E3","startDate":"2026-01-01","endDate":"%s","value":null,
				"currency":"AUD","renewalDate":"%s","autoRenew":true,"noticePeriodDays":45,"version":%d}
				""".formatted(vendorId, today.plusDays(40), today.plusDays(20), version(API + "/contracts/" + contractId))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.valueHidden").value(true))
			.andExpect(jsonPath("$.noticePeriodDays").value(45));
		assertThat(jdbc.queryForObject("SELECT value FROM vendor_contracts WHERE id = ?", BigDecimal.class,
				contractId)).isEqualByComparingTo("42000");
		as(viewerToken, get(API + "/contracts").param("vendorId", vendorId.toString()).param("status", "EXPIRED"))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].id").value(expired))
			.andExpect(jsonPath("$.content[0].daysUntilRenewal").doesNotExist());
		as(viewerToken, get(API + "/contracts").param("vendorId", vendorId.toString()).param("renewalWithinDays", "30"))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].id").value(contractId));
		as(viewerToken, get(API + "/vendors/" + vendorId)).andExpect(jsonPath("$.contractCount").value(2))
			.andExpect(jsonPath("$.nextRenewal").value(today.plusDays(20).toString()));

		// A blacklisted vendor takes no new contracts.
		as(adminToken, put(API + "/vendors/" + vendorId).contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Microsoft %s\",\"status\":\"BLACKLISTED\",\"version\":%d}".formatted(nonce,
					version(API + "/vendors/" + vendorId))))
			.andExpect(status().isOk());
		as(adminToken, post(API + "/contracts").contentType(MediaType.APPLICATION_JSON)
			.content(contract(vendorId, "Azure", "\"2026-01-01\"", "null", "null"))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VENDOR_BLACKLISTED"));

		// Documents: uploaded by editors, downloaded by viewers as attachments, audited on the contract.
		MockMultipartFile pdf = new MockMultipartFile("file", "signed contract.pdf", "application/pdf",
				"%PDF-1.4\n%vendor contract\n".getBytes(StandardCharsets.UTF_8));
		as(viewerToken, multipart(API + "/contracts/" + contractId + "/documents").file(pdf))
			.andExpect(status().isForbidden());
		String documents = body(as(adminToken, multipart(API + "/contracts/" + contractId + "/documents").file(pdf))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].fileName").value("signed contract.pdf")));
		Long fileId = ((Number) JsonPath.read(documents, "$[0].fileId")).longValue();
		as(adminToken, multipart(API + "/contracts/" + contractId + "/documents").file(new MockMultipartFile("file",
				"run.exe", "application/octet-stream", new byte[] { 'M', 'Z' }))).andExpect(status().isBadRequest());
		as(viewerToken, get(API + "/contracts/" + contractId + "/documents/" + fileId)).andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"))
			.andExpect(content().string(containsString("%PDF-1.4")));
		as(viewerToken, get(API + "/contracts/" + contractId)).andExpect(jsonPath("$.documents[0].fileId").value(fileId));

		// History: the value is there for finance viewers (the Super Admin) and hidden from the viewer.
		String forViewer = body(as(viewerToken, get("/api/history/VENDOR_CONTRACT/" + contractId).param("size", "100"))
			.andExpect(status().isOk()));
		assertThat(JsonPath.<List<String>>read(forViewer, "$.content[*].action")).contains("RECORD_CREATED",
				"VENDOR_CONTRACT_CREATED", "VENDOR_CONTRACT_DOCUMENT_ADDED");
		assertThat(JsonPath.<List<Object>>read(forViewer,
				"$.content[?(@.action == 'VENDOR_CONTRACT_CREATED')].details.hiddenFields[*]")).containsExactly("value");
		assertThat(JsonPath.<List<Object>>read(forViewer,
				"$.content[?(@.action == 'VENDOR_CONTRACT_CREATED')].details.value")).containsOnlyNulls();
		String forAdmin = body(as(adminToken, get("/api/history/VENDOR_CONTRACT/" + contractId).param("size", "100")));
		assertThat(JsonPath.<List<Object>>read(forAdmin,
				"$.content[?(@.action == 'VENDOR_CONTRACT_CREATED')].details.value")).singleElement()
			.satisfies(value -> assertThat(String.valueOf(value)).startsWith("42000"));

		// Removing a document deletes the file; a purged contract takes its documents and files with it.
		as(adminToken, delete(API + "/contracts/" + contractId + "/documents/" + fileId)).andExpect(status().isOk())
			.andExpect(jsonPath("$").isEmpty());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM files WHERE id = ?", Integer.class, fileId)).isZero();
		Long secondFile = ((Number) JsonPath.read(body(as(adminToken,
				multipart(API + "/contracts/" + contractId + "/documents").file(pdf))), "$[0].fileId")).longValue();
		as(adminToken, delete(API + "/contracts/" + contractId)).andExpect(status().isNoContent());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM files WHERE id = ?", Integer.class, secondFile)).isOne();
		Long item = binItem("VENDOR_CONTRACT", contractId);
		as(adminToken, post("/api/recycle-bin/%d/purge".formatted(item)).contentType(MediaType.APPLICATION_JSON)
			.content("{\"confirm\":\"Microsoft %s – Microsoft 365 E3\"}".formatted(nonce))).andExpect(status().isNoContent());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM files WHERE id = ?", Integer.class, secondFile)).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vendor_contracts WHERE id = ?", Integer.class, contractId))
			.isZero();
	}

	@Test
	void vendorsAndContractsImportFromCsvAfterAPreviewAndExportInTheSameColumns() throws Exception {
		createVendor("Atlassian");
		as(adminToken, get(API + "/imports")).andExpect(status().isOk())
			.andExpect(jsonPath("$[*].type").value(containsInAnyOrder("vendors", "vendor-contracts")));
		as(adminToken, get(API + "/imports/vendors/template")).andExpect(status().isOk())
			.andExpect(content().string(containsString("name,tax_id,category")));

		MockMultipartFile vendorsFile = new MockMultipartFile("file", "vendors.csv", "text/csv", """
				name,category,country,rating,contact_name,contact_email
				Canva %1$s,software,Australia,5,Meera Iyer,meera@canva.example
				Atlassian %1$s,SOFTWARE,,,,
				Hays %1$s,recruitment agency,India,7,,
				""".formatted(nonce).getBytes(StandardCharsets.UTF_8));
		as(viewerToken, multipart(API + "/imports/vendors/preview").file(vendorsFile)).andExpect(status().isForbidden());
		as(outsiderToken, multipart(API + "/imports/vendors/preview").file(vendorsFile)).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("MODULE_DISABLED"));
		String preview = body(as(adminToken, multipart(API + "/imports/vendors/preview").file(vendorsFile))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.validCount").value(1))
			.andExpect(jsonPath("$.invalidCount").value(2))
			.andExpect(jsonPath("$.invalidRows[0].errors[0].message").value("A vendor with this name already exists"))
			.andExpect(jsonPath("$.invalidRows[1].errors[0].column").value("rating")));
		as(adminToken, multipart(API + "/imports/vendors/commit").file(vendorsFile)
			.param("checksum", JsonPath.<String>read(preview, "$.checksum"))
			.param("skipInvalid", "true")).andExpect(status().isOk()).andExpect(jsonPath("$.imported").value(1));

		MockMultipartFile contractsFile = new MockMultipartFile("file", "contracts.csv", "text/csv", """
				vendor,title,start_date,end_date,value,currency,renewal_date,auto_renew,notice_period_days
				canva %1$s,Canva Teams,2026-01-01,2026-12-31,"1,200.50",aud,,yes,30
				Nobody %1$s,Mystery,2026-01-01,,,,,,
				""".formatted(nonce).getBytes(StandardCharsets.UTF_8));
		String contractPreview = body(as(adminToken, multipart(API + "/imports/vendor-contracts/preview").file(contractsFile))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.validCount").value(1))
			.andExpect(jsonPath("$.invalidRows[0].errors[0].message").value("No vendor has this name")));
		as(adminToken, multipart(API + "/imports/vendor-contracts/commit").file(contractsFile)
			.param("checksum", JsonPath.<String>read(contractPreview, "$.checksum"))
			.param("skipInvalid", "true")).andExpect(status().isOk()).andExpect(jsonPath("$.imported").value(1));

		as(viewerToken, get(API + "/vendors").param("search", "Canva " + nonce))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].contacts[0].email").value("meera@canva.example"))
			.andExpect(jsonPath("$.content[0].contractCount").value(1));
		as(viewerToken, get(API + "/vendors/export").param("search", nonce)).andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("vendors.csv")))
			.andExpect(content().string(containsString("Canva " + nonce + ",,SOFTWARE,Australia,,,,ACTIVE,5,,Meera Iyer,,"
					+ "meera@canva.example,")));
		as(adminToken, get(API + "/contracts/export").param("search", nonce)).andExpect(status().isOk())
			.andExpect(content().string(containsString("Canva " + nonce + ",Canva Teams,2026-01-01,2026-12-31,1200.50,AUD,,"
					+ "yes,30,")));
		as(viewerToken, get(API + "/contracts/export").param("search", nonce)).andExpect(status().isOk())
			.andExpect(content().string(containsString("Canva " + nonce + ",Canva Teams,2026-01-01,2026-12-31,Hidden,AUD,,"
					+ "yes,30,")));
	}

	@Test
	void renewalRemindersReachThoseWhoMayOpenTheModuleAndSearchShowsVendorsToThemOnly() throws Exception {
		LocalDate today = calendar.today();
		Long vendorId = createVendor("Adobe");
		Long renewing = createContract(vendorId, "Creative Cloud", "\"2026-01-01\"", "null",
				"\"" + today.plusDays(5) + "\"");
		Long later = createContract(vendorId, "Acrobat", "\"2026-01-01\"", "\"" + today.plusDays(25) + "\"", "null");
		createContract(vendorId, "Stock", "\"2026-01-01\"", "null", "\"" + today.plusDays(60) + "\"");

		reminders.sendReminders();
		reminders.sendReminders();
		String sevenDay = "CONTRACT_RENEWAL:" + renewing + ":" + today.plusDays(5) + ":7:%d";
		String thirtyDay = "CONTRACT_RENEWAL:" + later + ":" + today.plusDays(25) + ":30:%d";
		assertThat(reminderCount(sevenDay.formatted(viewerId))).isEqualTo(1);
		assertThat(reminderCount(thirtyDay.formatted(viewerId))).isEqualTo(1);
		assertThat(reminderCount(sevenDay.formatted(plainId))).isZero();
		assertThat(reminderCount(sevenDay.formatted(outsiderId))).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE type = 'CONTRACT_RENEWAL' AND user_id = ? "
				+ "AND entity_id IN (SELECT id FROM vendor_contracts WHERE vendor_id = ?)", Integer.class, viewerId, vendorId))
			.isEqualTo(2);

		for (String token : List.of(adminToken, viewerToken)) {
			assertThat(searchGroups(token)).contains("VENDORS", "VENDOR_CONTRACTS");
		}
		for (String token : List.of(outsiderToken, plainToken)) {
			assertThat(searchGroups(token)).doesNotContain("VENDORS", "VENDOR_CONTRACTS");
		}
	}

	// --- helpers ----------------------------------------------------------------------------------------------

	private Long createVendor(String name) throws Exception {
		return id(as(adminToken, post(API + "/vendors").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"%s %s\",\"category\":\"SOFTWARE\"}".formatted(name, nonce))).andExpect(status().isCreated()));
	}

	private Long createContract(Long vendorId, String title, String start, String end, String renewal) throws Exception {
		return id(as(adminToken, post(API + "/contracts").contentType(MediaType.APPLICATION_JSON)
			.content(contract(vendorId, title, start, end, renewal))).andExpect(status().isCreated()));
	}

	private static String contract(Long vendorId, String title, String start, String end, String renewal) {
		return "{\"vendorId\":%d,\"title\":\"%s\",\"startDate\":%s,\"endDate\":%s,\"renewalDate\":%s}".formatted(vendorId,
				title, start, end, renewal);
	}

	private int reminderCount(String dedupKey) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE type = 'CONTRACT_RENEWAL' AND dedup_key = ?",
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
