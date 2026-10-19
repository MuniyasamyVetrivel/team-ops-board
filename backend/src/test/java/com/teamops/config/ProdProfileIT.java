package com.teamops.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.teamops.department.repository.DepartmentRepository;
import com.teamops.support.IntegrationUsers;
import com.teamops.user.entity.RoleCodes;
import com.teamops.user.repository.UserRepository;
import com.teamops.user.service.UserService;

/**
 * The "prod" profile (application-prod.yml) on a real server port against MySQL: actuator health only, CORS for
 * https://ops.tritusa.com.au alone, a Secure and SameSite=Strict refresh cookie, and X-Forwarded-For trusted only from
 * the Compose "app" network, so a request from anywhere else cannot put a fake IP in the audit log.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@TestPropertySource(properties = { "app.storage.dir=target/it-uploads", "app.scheduling.enabled=false" })
class ProdProfileIT {

	private static final String ORIGIN = "https://ops.tritusa.com.au";

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

	@Value("${local.server.port}")
	private int port;

	private IntegrationUsers users;

	private String email;

	private Long userId;

	@BeforeEach
	void createUser() {
		users = new IntegrationUsers(userService, userRepository, departmentRepository);
		userId = users.create("IT", RoleCodes.EMPLOYEE);
		email = users.email(userId);
	}

	@AfterEach
	void deleteUser() {
		users.deleteAll();
	}

	@Test
	void healthIsTheOnlyActuatorEndpointAndShowsNoDetails() throws Exception {
		String body = mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andReturn().getResponse()
			.getContentAsString();
		assertThat(body).contains("\"status\"").doesNotContain("components").doesNotContain("details");
		for (String hidden : List.of("/actuator/info", "/actuator/env", "/actuator/configprops", "/actuator/beans")) {
			int code = mvc.perform(get(hidden)).andReturn().getResponse().getStatus();
			assertThat(code).as(hidden).isIn(401, 403, 404);
		}
	}

	@Test
	void corsAllowsOnlyTheProductionOrigin() throws Exception {
		mvc.perform(options("/api/auth/login").header(HttpHeaders.ORIGIN, ORIGIN)
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN));

		for (String other : List.of("http://localhost:5173", "https://evil.example", "http://ops.tritusa.com.au")) {
			mvc.perform(options("/api/auth/login").header(HttpHeaders.ORIGIN, other)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
		}
	}

	@Test
	void theRefreshCookieIsSecureHttpOnlyAndStrict() throws Exception {
		String setCookie = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content(login()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getHeader(HttpHeaders.SET_COOKIE);
		assertThat(setCookie).startsWith("tob_refresh=").contains("Secure").contains("HttpOnly")
			.contains("SameSite=Strict").contains("Path=/api/auth");
	}

	@Test
	void forwardedForFromOutsideTheProxyNetworkIsIgnored() throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/login"))
			.header("Content-Type", "application/json")
			.header("X-Forwarded-For", "203.0.113.9")
			.POST(HttpRequest.BodyPublishers.ofString(login()))
			.build();
		HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).isEqualTo(200);

		String ip = jdbc.queryForObject("""
				SELECT ip_address FROM audit_logs WHERE action = 'LOGIN' AND actor_id = ? ORDER BY id DESC LIMIT 1""",
				String.class, userId);
		assertThat(ip).isEqualTo("127.0.0.1");
	}

	@Test
	void unknownPathsAndErrorsRevealNothing() throws Exception {
		mvc.perform(get("/api/no-such-endpoint")).andExpect(status().is4xxClientError())
			.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Exception"))));
	}

	private String login() {
		return "{\"email\":\"" + email + "\",\"password\":\"" + IntegrationUsers.PASSWORD + "\"}";
	}

}
