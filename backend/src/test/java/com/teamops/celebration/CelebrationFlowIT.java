package com.teamops.celebration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
import com.teamops.celebration.service.CelebrationReminderService;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.support.IntegrationUsers;
import com.teamops.user.entity.RoleCodes;
import com.teamops.user.repository.UserRepository;
import com.teamops.user.service.UserService;

/**
 * Calendar celebrations end-to-end against MySQL: leave is gone (V22), employees with private birth years, important
 * days, events and scheduled email campaigns on the calendar and in "Coming up", LinkedIn post tracking, idempotent
 * reminders with the days-before setting, CSV import and export, and audit entries. Everything is created with a
 * unique nonce and removed afterwards, so seeded data never changes the checks. Run with: mvnw verify -Pit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = { "app.dev-seed.enabled=false", "app.scheduling.enabled=false" })
class CelebrationFlowIT {

	private static final String DAYS_BEFORE = "celebrations.reminderDaysBefore";

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
	private CelebrationReminderService reminderService;

	@Autowired
	private JdbcTemplate jdbc;

	private IntegrationUsers users;

	private Long editorId;

	private Long viewerId;

	private Long adminId;

	private String editorToken;

	private String viewerToken;

	private String adminToken;

	private String nonce;

	private LocalDate today;

	private String daysBeforeSetting;

	private final List<Long> employees = new ArrayList<>();

	private final List<Long> days = new ArrayList<>();

	private final List<Long> events = new ArrayList<>();

	@BeforeEach
	void setUp() throws Exception {
		users = new IntegrationUsers(userService, userRepository, departmentRepository);
		editorId = users.create("DM", RoleCodes.EMPLOYEE, "MARKETING_VIEW", "CELEBRATIONS_EDIT");
		viewerId = users.create("IT", RoleCodes.EMPLOYEE, "CALENDAR_VIEW");
		adminId = users.create("IT", RoleCodes.SUPER_ADMIN);
		editorToken = login(users.email(editorId));
		viewerToken = login(users.email(viewerId));
		adminToken = login(users.email(adminId));
		nonce = "it" + Long.toHexString(System.nanoTime());
		today = calendar.today();
		daysBeforeSetting = jdbc.queryForObject("SELECT setting_value FROM app_settings WHERE setting_key = ?",
				String.class, DAYS_BEFORE);
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("UPDATE app_settings SET setting_value = ? WHERE setting_key = ?", daysBeforeSetting, DAYS_BEFORE);
		jdbc.query("SELECT id FROM employees WHERE employee_code LIKE ?", (rs, i) -> rs.getLong(1), "%" + nonce + "%")
			.forEach(employees::add);
		jdbc.query("SELECT id FROM important_days WHERE name LIKE ?", (rs, i) -> rs.getLong(1), "%" + nonce + "%")
			.forEach(days::add);
		for (Long id : employees) {
			deleteOccasionRows("BIRTHDAY", id);
			deleteOccasionRows("WORK_ANNIVERSARY", id);
			jdbc.update("DELETE FROM employees WHERE id = ?", id);
		}
		for (Long id : days) {
			deleteOccasionRows("IMPORTANT_DAY", id);
			jdbc.update("DELETE FROM important_days WHERE id = ?", id);
		}
		for (Long id : events) {
			deleteOccasionRows("EVENT", id);
			jdbc.update("DELETE FROM marketing_events WHERE id = ?", id);
		}
		jdbc.update("DELETE FROM marketing_events WHERE owner_id IN (?, ?, ?)", editorId, viewerId, adminId);
		jdbc.update("DELETE FROM email_campaigns WHERE name LIKE ?", "%" + nonce + "%");
		jdbc.update("DELETE FROM files WHERE uploaded_by IN (?, ?, ?)", editorId, viewerId, adminId);
		jdbc.update("DELETE FROM audit_logs WHERE actor_id IN (?, ?, ?)", editorId, viewerId, adminId);
		users.deleteAll();
	}

	@Test
	void leaveIsGoneAndThePermissionAndSettingExist() throws Exception {
		assertThat(jdbc.queryForObject("""
				SELECT COUNT(*) FROM information_schema.columns
				WHERE table_schema = DATABASE() AND table_name = 'calendar_events' AND column_name = 'user_id'
				""", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM calendar_events WHERE event_type = 'LEAVE'", Integer.class))
			.isZero();
		assertThat(jdbc.queryForObject("SELECT module FROM permissions WHERE code = 'CELEBRATIONS_EDIT'", String.class))
			.isEqualTo("MARKETING");
		as(adminToken, post("/api/calendar/events").contentType(MediaType.APPLICATION_JSON).content("""
				{"title":"Away","eventType":"LEAVE","allDay":true,"startDate":"%s","endDate":"%s"}
				""".formatted(today, today))).andExpect(status().isBadRequest());
		// The days-before setting is editable in Admin Settings.
		as(adminToken, get("/api/admin/settings")).andExpect(status().isOk())
			.andExpect(jsonPath("$[*].key", hasItem(DAYS_BEFORE)));
	}

	@Test
	void celebrationsShowOnTheCalendarWithPrivateBirthYears() throws Exception {
		LocalDate birthday = today.plusDays(3);
		LocalDate anniversary = today.plusDays(5);
		Long priya = id(createEmployee(code(1), "Priya", birthday.minusYears(34), anniversary.minusYears(5))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.dateOfBirth").value(birthday.minusYears(34).toString())));
		employees.add(priya);

		// Viewers see the day and month, never the year.
		as(viewerToken, get("/api/celebrations/employees/" + priya)).andExpect(status().isOk())
			.andExpect(jsonPath("$.dateOfBirth").doesNotExist())
			.andExpect(jsonPath("$.birthMonth").value(birthday.getMonthValue()))
			.andExpect(jsonPath("$.birthDay").value(birthday.getDayOfMonth()))
			.andExpect(jsonPath("$.nextAnniversaryYears").value(5))
			.andExpect(jsonPath("$.canEdit").value(false));
		createEmployee(code(1), "Again", birthday.minusYears(20), null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EMPLOYEE_CODE_TAKEN"));
		as(viewerToken, post("/api/celebrations/employees").contentType(MediaType.APPLICATION_JSON)
			.content(employeeJson(code(2), "Nope", birthday, null))).andExpect(status().isForbidden());

		String birthdayKey = "BIRTHDAY-" + priya + "-" + birthday;
		String anniversaryKey = "WORK_ANNIVERSARY-" + priya + "-" + anniversary;
		as(viewerToken, get("/api/calendar").param("from", today.toString()).param("to", today.plusDays(10).toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[?(@.key == '%s')].occasion.category".formatted(birthdayKey)).value("BIRTHDAY"))
			.andExpect(jsonPath("$.items[?(@.key == '%s')].title".formatted(birthdayKey)).value("Priya Tester's birthday"))
			.andExpect(jsonPath("$.items[?(@.key == '%s')].occasion.detail".formatted(anniversaryKey))
				.value("5 years at Tritusa"))
			.andExpect(jsonPath("$.items[?(@.key == '%s')].occasion.linkedIn.needed".formatted(birthdayKey)).value(false));
		as(viewerToken, get("/api/celebrations/upcoming").param("days", "14")).andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].key", hasItem(birthdayKey)));

		// People who left stop appearing.
		Integer version = JsonPath.read(as(editorToken, get("/api/celebrations/employees/" + priya)).andReturn()
			.getResponse()
			.getContentAsString(), "$.version");
		as(editorToken, put("/api/celebrations/employees/" + priya).contentType(MediaType.APPLICATION_JSON)
			.content(employeeJson(code(1), "Priya", birthday.minusYears(34), anniversary.minusYears(5))
				.replace("}", ",\"status\":\"LEFT\",\"version\":" + version + "}")))
			.andExpect(status().isOk());
		as(viewerToken, get("/api/calendar").param("from", today.toString()).param("to", today.plusDays(10).toString()))
			.andExpect(jsonPath("$.items[*].key", not(hasItem(birthdayKey))));
		as(viewerToken, get("/api/celebrations/employees/" + priya)).andExpect(status().isNotFound());

		assertThat(jdbc.queryForObject("""
				SELECT COUNT(*) FROM audit_logs WHERE actor_id = ? AND entity_id = ?
				  AND action IN ('EMPLOYEE_CREATED', 'EMPLOYEE_UPDATED')
				""", Integer.class, editorId, priya)).isEqualTo(2);
	}

	@Test
	void importantDaysEventsAndScheduledCampaignsWithLinkedInTracking() throws Exception {
		LocalDate dayDate = today.plusDays(4);
		Long diwali = id(as(editorToken, post("/api/celebrations/important-days").contentType(MediaType.APPLICATION_JSON)
			.content(dayJson("Diwali " + nonce, dayDate, "YEARLY"))).andExpect(status().isCreated())
			.andExpect(jsonPath("$.nextOccurrence").value(dayDate.toString()))
			.andExpect(jsonPath("$.linkedIn.needed").value(true))
			.andExpect(jsonPath("$.linkedIn.status").value("NOT_STARTED")));
		days.add(diwali);
		as(editorToken, post("/api/celebrations/important-days").contentType(MediaType.APPLICATION_JSON)
			.content(dayJson("Diwali " + nonce, dayDate, "YEARLY"))).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("IMPORTANT_DAY_EXISTS"));

		Long campaign = scheduledCampaign(today.plusDays(6));
		String start = today.plusDays(5).atTime(LocalTime.of(10, 0)).atZone(calendar.zone()).toInstant().toString();
		as(editorToken, post("/api/celebrations/events").contentType(MediaType.APPLICATION_JSON)
			.content(eventJson("Bad link", start, "\"registrationLink\":\"javascript:alert(1)\"")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_LINK"));
		Long webinar = id(as(editorToken, post("/api/celebrations/events").contentType(MediaType.APPLICATION_JSON)
			.content(eventJson("SAP webinar " + nonce, start, "\"registrationLink\":\"https://example.com/register\"")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.category").value("WEBINAR")));
		events.add(webinar);

		String range = "/api/calendar?from=%s&to=%s".formatted(today, today.plusDays(10));
		String dayKey = "IMPORTANT_DAY-" + diwali + "-" + dayDate;
		String eventKey = "EVENT-" + webinar + "-" + today.plusDays(5);
		as(viewerToken, get(range)).andExpect(status().isOk())
			.andExpect(jsonPath("$.items[?(@.key == '%s')].occasion.region".formatted(dayKey)).value("INDIA"))
			.andExpect(jsonPath("$.items[?(@.key == '%s')].kind".formatted(eventKey)).value("MARKETING_EVENT"))
			.andExpect(jsonPath("$.items[?(@.key == '%s')].occasion.category".formatted(eventKey)).value("WEBINAR"))
			.andExpect(jsonPath("$.items[*].key", not(hasItem("CAMPAIGN-" + campaign))));
		// Scheduled email campaigns: Email marketing, for Digital Marketing campaign viewers.
		as(adminToken, get(range))
			.andExpect(jsonPath("$.items[?(@.key == 'CAMPAIGN-%d')].occasion.category".formatted(campaign))
				.value("EMAIL_MARKETING"));

		// LinkedIn post tracking per occurrence.
		as(editorToken, put("/api/celebrations/linkedin-posts").contentType(MediaType.APPLICATION_JSON)
			.content(postJson(diwali, dayDate.plusDays(1), "DRAFTED", null))).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("OCCURRENCE_NOT_FOUND"));
		as(editorToken, put("/api/celebrations/linkedin-posts").contentType(MediaType.APPLICATION_JSON)
			.content(postJson(diwali, dayDate, "DRAFTED", null))).andExpect(status().isOk())
			.andExpect(jsonPath("$.saved").value(true))
			.andExpect(jsonPath("$.version").value(0));
		as(editorToken, put("/api/celebrations/linkedin-posts").contentType(MediaType.APPLICATION_JSON)
			.content(postJson(diwali, dayDate, "SCHEDULED", null))).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STALE_UPDATE"));
		as(editorToken, put("/api/celebrations/linkedin-posts").contentType(MediaType.APPLICATION_JSON)
			.content(postJson(diwali, dayDate, "SCHEDULED", 0))).andExpect(status().isOk())
			.andExpect(jsonPath("$.version").value(1));
		as(viewerToken, get("/api/celebrations/linkedin-posts").param("from", today.toString())
			.param("to", today.plusDays(10).toString())
			.param("size", "100")).andExpect(status().isOk())
			.andExpect(jsonPath("$.content[?(@.key == '%s')].linkedIn.status".formatted(dayKey)).value("SCHEDULED"));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE actor_id = ? AND action = 'LINKEDIN_POST_UPDATED'",
				Integer.class, editorId)).isEqualTo(2);

		// A date change moves the upcoming post with it.
		as(editorToken, put("/api/celebrations/important-days/" + diwali).contentType(MediaType.APPLICATION_JSON)
			.content(dayJson("Diwali " + nonce, dayDate.plusDays(1), "YEARLY").replace("}", ",\"version\":0}")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.linkedIn.status").value("SCHEDULED"));

		// An event that links the campaign takes its place on the calendar.
		as(editorToken, put("/api/celebrations/events/" + webinar).contentType(MediaType.APPLICATION_JSON)
			.content(eventJson("SAP webinar " + nonce, start, "\"emailCampaignId\":" + campaign + ",\"version\":0")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.emailCampaignId").value(campaign))
			.andExpect(jsonPath("$.emailCampaign").doesNotExist());
		as(adminToken, get(range)).andExpect(jsonPath("$.items[*].key", not(hasItem("CAMPAIGN-" + campaign))));
	}

	@Test
	void remindersGoToEditorsOnceWithTheDaysBeforeSetting() throws Exception {
		jdbc.update("UPDATE app_settings SET setting_value = '3' WHERE setting_key = ?", DAYS_BEFORE);
		LocalDate sydneyToday = calendar.today(ZoneId.of("Australia/Sydney"));
		LocalDate birthday = sydneyToday.plusDays(3);
		Long arun = id(createEmployee(code(3), "Arun", birthday.minusYears(28), null).andExpect(status().isCreated()));
		employees.add(arun);

		reminderService.sendReminders();
		reminderService.sendReminders();

		String key = "OCCASION:BIRTHDAY:%d:%s:3:".formatted(arun, birthday);
		assertThat(jdbc.queryForList("SELECT title FROM notifications WHERE user_id = ? AND dedup_key = ?", String.class,
				adminId, key + adminId))
			.as("sent once, however often the job runs")
			.containsExactly("Reminder: Arun Tester's birthday is in 3 days (%d %s)".formatted(birthday.getDayOfMonth(),
					birthday.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH)));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE dedup_key = ?", Integer.class,
				key + editorId)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id = ? AND dedup_key LIKE 'OCCASION:%'",
				Integer.class, viewerId)).as("only editors and Super Admins").isZero();
	}

	@Test
	void employeesAndImportantDaysImportAndExport() throws Exception {
		LocalDate born = LocalDate.of(1994, 2, 28);
		createEmployee(code(4), "Existing", born, null).andExpect(status().isCreated());
		MockMultipartFile file = csv("""
				employee_id,first_name,last_name,date_of_birth,designation,reporting_manager,date_of_joining,department_code
				%1$s,Meera,Iyer,1996-02-29,Content Strategist,Priya Menon,2022-03-01,DM
				%2$s,Dup,Licate,1990-01-01,QA,Rakesh,,
				%3$s,Bad,Dept,1990-01-01,QA,Rakesh,,NOPE
				""".formatted(code(5), code(4), code(6)));
		String preview = as(editorToken, multipart("/api/marketing/imports/employees/preview").file(file))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.validCount").value(1))
			.andExpect(jsonPath("$.invalidCount").value(2))
			.andExpect(jsonPath("$.invalidRows[0].errors[0].message").value("An employee with this ID already exists"))
			.andExpect(jsonPath("$.invalidRows[1].errors[0].column").value("department_code"))
			.andReturn().getResponse().getContentAsString();
		as(editorToken, multipart("/api/marketing/imports/employees/commit").file(file)
			.param("checksum", (String) JsonPath.read(preview, "$.checksum"))).andExpect(status().isBadRequest());
		as(editorToken, multipart("/api/marketing/imports/employees/commit").file(file)
			.param("checksum", (String) JsonPath.read(preview, "$.checksum"))
			.param("skipInvalid", "true")).andExpect(status().isOk()).andExpect(jsonPath("$.imported").value(1));

		String export = as(editorToken, get("/api/celebrations/employees/export").param("search", nonce))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		// A UTF-8 BOM (for Excel), then the import's columns.
		assertThat(export).startsWith("\uFEFFemployee_id,first_name,last_name,date_of_birth")
			.contains(code(5) + ",Meera,Iyer,1996-02-29,Content Strategist,Priya Menon,2022-03-01,DM,,,ACTIVE,");
		as(viewerToken, get("/api/celebrations/employees/export")).andExpect(status().isForbidden());
		// Viewers cannot import even with the file in hand.
		as(viewerToken, multipart("/api/marketing/imports/employees/preview").file(file)).andExpect(status().isForbidden());

		MockMultipartFile dayFile = csv("""
				name,date,category,region,repeat
				Australia Day %1$s,2027-01-26,NATIONAL_DAY,AUSTRALIA,YEARLY
				Broken %1$s,2027-13-40,FESTIVAL,MARS,
				""".formatted(nonce));
		String dayPreview = as(editorToken, multipart("/api/marketing/imports/important-days/preview").file(dayFile))
			.andExpect(jsonPath("$.validCount").value(1))
			.andExpect(jsonPath("$.invalidCount").value(1))
			.andReturn().getResponse().getContentAsString();
		as(editorToken, multipart("/api/marketing/imports/important-days/commit").file(dayFile)
			.param("checksum", (String) JsonPath.read(dayPreview, "$.checksum"))
			.param("skipInvalid", "true")).andExpect(jsonPath("$.imported").value(1));
		String days = as(viewerToken, get("/api/celebrations/important-days/export").param("search", nonce))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertThat(days).startsWith("\uFEFFname,date,category,region,repeat")
			.contains("Australia Day " + nonce + ",2027-01-26,NATIONAL_DAY,AUSTRALIA,YEARLY");
	}

	// --- helpers ----------------------------------------------------------------------------------------------

	private String code(int n) {
		return "IT-" + nonce + "-" + n;
	}

	private ResultActions createEmployee(String code, String firstName, LocalDate born, LocalDate joined) throws Exception {
		return as(editorToken, post("/api/celebrations/employees").contentType(MediaType.APPLICATION_JSON)
			.content(employeeJson(code, firstName, born, joined)));
	}

	private static String employeeJson(String code, String firstName, LocalDate born, LocalDate joined) {
		return """
				{"employeeCode":"%s","firstName":"%s","lastName":"Tester","dateOfBirth":"%s","dateOfJoining":%s,
				 "designation":"QA Engineer","reportingManager":"Rakesh"}
				""".formatted(code, firstName, born, joined == null ? "null" : "\"" + joined + "\"");
	}

	private static String dayJson(String name, LocalDate date, String recurrence) {
		return """
				{"name":"%s","date":"%s","category":"FESTIVAL","region":"INDIA","recurrence":"%s"}
				""".formatted(name, date, recurrence);
	}

	private String eventJson(String title, String startAt, String extra) {
		return """
				{"title":"%s","eventType":"WEBINAR","startAt":"%s","ownerId":%d,%s}
				""".formatted(title, startAt, editorId, extra);
	}

	private static String postJson(Long dayId, LocalDate date, String status, Integer version) {
		return """
				{"subjectType":"IMPORTANT_DAY","subjectId":%d,"occurrenceDate":"%s","postNeeded":true,"status":"%s",
				 "postLink":"https://www.linkedin.com/posts/tritusa","version":%s}
				""".formatted(dayId, date, status, version);
	}

	private Long scheduledCampaign(LocalDate date) {
		jdbc.update("""
				INSERT INTO email_campaigns (name, campaign_type, campaign_date, status, owner_id)
				VALUES (?, 'NEWSLETTER', ?, 'SCHEDULED', ?)
				""", "Newsletter " + nonce, date, editorId);
		return jdbc.queryForObject("SELECT id FROM email_campaigns WHERE name = ?", Long.class, "Newsletter " + nonce);
	}

	private void deleteOccasionRows(String kind, Long id) {
		jdbc.update("DELETE FROM linkedin_posts WHERE subject_type = ? AND subject_id = ?", kind, id);
		jdbc.update("DELETE FROM occasion_attachments WHERE subject_type = ? AND subject_id = ?", kind, id);
		jdbc.update("DELETE FROM notifications WHERE dedup_key LIKE ?", "OCCASION:" + kind + ":" + id + ":%");
	}

	private static MockMultipartFile csv(String text) {
		return new MockMultipartFile("file", "import.csv", "text/csv", text.getBytes(StandardCharsets.UTF_8));
	}

	private static Long id(ResultActions result) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
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
