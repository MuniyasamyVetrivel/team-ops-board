package com.teamops.marketing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
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
import com.teamops.common.csv.CsvColumn;
import com.teamops.common.csv.CsvImporter;
import com.teamops.common.csv.RowReader;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.support.IntegrationUsers;
import com.teamops.user.dto.CreateUserRequest;
import com.teamops.user.entity.RoleCodes;
import com.teamops.user.repository.UserRepository;
import com.teamops.user.service.UserService;

/**
 * Phase 9 end-to-end against MySQL: the MARKETING_VIEW route guard, the shared marketing context, data sources, the
 * CSV import flow (through a test-only importer, until the module importers arrive) and the marketing permission
 * rules in user administration. Run with: mvnw verify -Pit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = { "app.dev-seed.enabled=false", "app.scheduling.enabled=false" })
class MarketingFlowIT {

	/** Rows committed by the test importer. */
	static final List<String> IMPORTED = new CopyOnWriteArrayList<>();

	@TestConfiguration
	static class SampleImporterConfig {

		@Bean
		CsvImporter<String> sampleImporter() {
			return new CsvImporter<>() {

				@Override
				public String type() {
					return "it-sample";
				}

				@Override
				public String label() {
					return "IT sample";
				}

				@Override
				public String description() {
					return "Test-only importer";
				}

				@Override
				public String permission() {
					return "MARKETING_EDIT";
				}

				@Override
				public List<CsvColumn> columns() {
					return List.of(CsvColumn.required("name", "A name", "Alpha"),
							CsvColumn.optional("score", "0-100", "42"));
				}

				@Override
				public ImportSession<String> open(AuthenticatedUser actor) {
					return new ImportSession<>() {

						@Override
						public String parse(RowReader row) {
							String name = row.text("name", 20);
							row.integer("score", 0, 100);
							return name;
						}

						@Override
						public String key(String record) {
							return record.toLowerCase();
						}

						@Override
						public int commit(List<String> records) {
							IMPORTED.addAll(records);
							return records.size();
						}

					};
				}

			};
		}

	}

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DepartmentRepository departmentRepository;

	@Autowired
	private BusinessCalendar calendar;

	@Autowired
	private JdbcTemplate jdbc;

	private IntegrationUsers users;

	private Long marketerId;

	private Long viewerId;

	private Long employeeId;

	private String marketerToken;

	private String viewerToken;

	private String employeeToken;

	@BeforeEach
	void setUp() throws Exception {
		IMPORTED.clear();
		users = new IntegrationUsers(userService, userRepository, departmentRepository);
		marketerId = users.create("DM", RoleCodes.EMPLOYEE, "MARKETING_VIEW", "MARKETING_EDIT");
		viewerId = users.create("DM", RoleCodes.EMPLOYEE, "MARKETING_VIEW");
		employeeId = users.create("IT", RoleCodes.EMPLOYEE);
		marketerToken = login(users.email(marketerId));
		viewerToken = login(users.email(viewerId));
		employeeToken = login(users.email(employeeId));
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("DELETE FROM audit_logs WHERE action = 'CSV_IMPORTED' AND actor_id IN (?, ?)", marketerId, viewerId);
		users.deleteAll();
	}

	@Test
	void theMarketingContextIsForMarketingUsersOnly() throws Exception {
		String body = as(viewerToken, get("/api/marketing/context")).andExpect(status().isOk())
			.andExpect(jsonPath("$.today").value(calendar.today().toString()))
			.andExpect(jsonPath("$.currentPeriod.month").value(calendar.today().getMonthValue()))
			.andExpect(jsonPath("$.currentPeriod.year").value(calendar.today().getYear()))
			.andExpect(jsonPath("$.years[0]").value(calendar.today().getYear() + 1))
			.andExpect(jsonPath("$.behindThresholdPct").value(60))
			.andReturn()
			.getResponse()
			.getContentAsString();
		List<Integer> owners = JsonPath.read(body, "$.owners[*].id");
		assertThat(owners).contains(marketerId.intValue(), viewerId.intValue())
			.doesNotContain(employeeId.intValue());

		as(employeeToken, get("/api/marketing/context")).andExpect(status().isForbidden());
		as(employeeToken, get("/api/marketing/integrations")).andExpect(status().isForbidden());
	}

	@Test
	void everyDataSourceIsManualForNow() throws Exception {
		as(viewerToken, get("/api/marketing/integrations")).andExpect(status().isOk())
			.andExpect(jsonPath("$.providers.length()").value(5))
			.andExpect(jsonPath("$.providers[0].category").value("SEO_RANKINGS"))
			.andExpect(jsonPath("$.providers[0].code").value("MANUAL"))
			.andExpect(jsonPath("$.providers[0].automated").value(false))
			.andExpect(jsonPath("$.providers[0].planned[0]").value("Semrush"));
	}

	@Test
	void csvImportsArePreviewedThenCommittedWithoutBadRows() throws Exception {
		// Only users with the importer's own permission see it.
		as(viewerToken, get("/api/marketing/imports")).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(0));
		as(marketerToken, get("/api/marketing/imports")).andExpect(jsonPath("$[0].type").value("it-sample"))
			.andExpect(jsonPath("$[0].columns[0].required").value(true));
		as(marketerToken, get("/api/marketing/imports/it-sample/template")).andExpect(status().isOk())
			.andExpect(content().string("﻿name,score\r\nAlpha,42\r\n"));

		MockMultipartFile file = csv("name,score\nAlpha,10\nBeta,500\nalpha,3\nGamma,\n");
		as(viewerToken, multipart("/api/marketing/imports/it-sample/preview").file(file))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("IMPORT_NOT_ALLOWED"));
		String preview = as(marketerToken, multipart("/api/marketing/imports/it-sample/preview").file(file))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalRows").value(4))
			.andExpect(jsonPath("$.validCount").value(2))
			.andExpect(jsonPath("$.invalidCount").value(2))
			.andExpect(jsonPath("$.invalidRows[0].line").value(3))
			.andExpect(jsonPath("$.invalidRows[0].errors[0].column").value("score"))
			.andExpect(jsonPath("$.invalidRows[1].errors[0].message").value("Duplicate of row 2"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(IMPORTED).isEmpty();
		String checksum = JsonPath.read(preview, "$.checksum");

		as(marketerToken, multipart("/api/marketing/imports/it-sample/commit").file(file).param("checksum", checksum))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("IMPORT_HAS_ERRORS"));
		as(marketerToken, multipart("/api/marketing/imports/it-sample/commit").file(csv("name\nOther\n"))
			.param("checksum", checksum)
			.param("skipInvalid", "true")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("IMPORT_FILE_CHANGED"));
		assertThat(IMPORTED).isEmpty();

		as(marketerToken, multipart("/api/marketing/imports/it-sample/commit").file(file)
			.param("checksum", checksum)
			.param("skipInvalid", "true")).andExpect(status().isOk())
			.andExpect(jsonPath("$.imported").value(2))
			.andExpect(jsonPath("$.skipped").value(2));
		assertThat(IMPORTED).containsExactly("Alpha", "Gamma");
		assertThat(jdbc.queryForObject(
				"SELECT COUNT(*) FROM audit_logs WHERE action = 'CSV_IMPORTED' AND actor_id = ?", Integer.class,
				marketerId))
			.isEqualTo(1);

		// Unknown import types are a 404 for marketing users, and the whole group stays closed to others.
		as(marketerToken, get("/api/marketing/imports/nope/template")).andExpect(status().isNotFound());
		as(employeeToken, multipart("/api/marketing/imports/it-sample/preview").file(file))
			.andExpect(status().isForbidden());
	}

	@Test
	void userAdministrationRejectsInconsistentMarketingGrants() throws Exception {
		Long departmentId = departmentRepository.findByCode("DM").orElseThrow().getId();
		CreateUserRequest seoWithoutModule = new CreateUserRequest("it-seo-" + System.nanoTime() + "@teamops.local",
				IntegrationUsers.PASSWORD, "Integration", "Seo", null, null, null, null, departmentId, null, null,
				Set.of(RoleCodes.EMPLOYEE), Set.of("SEO_VIEW", "SEO_EDIT"));
		assertThatThrownBy(() -> userService.create(seoWithoutModule, null, ClientInfo.unknown()))
			.isInstanceOf(ApiException.class)
			.extracting("code")
			.isEqualTo("MARKETING_VIEW_REQUIRED");

		Long admin = users.create("IT", RoleCodes.SUPER_ADMIN);
		as(login(users.email(admin)), put("/api/users/" + viewerId + "/access").contentType(MediaType.APPLICATION_JSON)
			.content("{\"roles\":[\"EMPLOYEE\"],\"permissions\":[\"MARKETING_VIEW\",\"LEAD_EDIT\"]}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VIEW_PERMISSION_REQUIRED"));
	}

	private static MockMultipartFile csv(String text) {
		return new MockMultipartFile("file", "sample.csv", "text/csv", text.getBytes(StandardCharsets.UTF_8));
	}

	private ResultActions as(String token, AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
		return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private String login(String email) throws Exception {
		String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, IntegrationUsers.PASSWORD)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.accessToken");
	}

}
