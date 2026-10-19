package com.teamops.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.teamops.department.repository.DepartmentRepository;
import com.teamops.support.IntegrationUsers;
import com.teamops.user.entity.RoleCodes;
import com.teamops.user.repository.UserRepository;
import com.teamops.user.service.UserService;

/**
 * The other half of {@link ProdProfileIT}: with the "prod" profile the forwarded headers are honoured from a trusted
 * proxy, so the audit log records the visitor's IP that Nginx passes on, not Nginx's own. Here the test client
 * (127.0.0.1) stands in for Nginx on the Compose "app" network.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("prod")
@TestPropertySource(properties = { "app.storage.dir=target/it-uploads", "app.scheduling.enabled=false",
		"server.tomcat.remoteip.internal-proxies=127\\.0\\.0\\.1" })
class ProdProfileTrustedProxyIT {

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

	private Long userId;

	@BeforeEach
	void createUser() {
		users = new IntegrationUsers(userService, userRepository, departmentRepository);
		userId = users.create("IT", RoleCodes.EMPLOYEE);
	}

	@AfterEach
	void deleteUser() {
		users.deleteAll();
	}

	@Test
	void theVisitorsIpFromTheProxyIsRecorded() throws Exception {
		String login = "{\"email\":\"" + users.email(userId) + "\",\"password\":\"" + IntegrationUsers.PASSWORD + "\"}";
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/login"))
			.header("Content-Type", "application/json")
			.header("X-Forwarded-For", "203.0.113.9")
			.header("X-Forwarded-Proto", "https")
			.POST(HttpRequest.BodyPublishers.ofString(login))
			.build();
		HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).isEqualTo(200);

		String ip = jdbc.queryForObject("""
				SELECT ip_address FROM audit_logs WHERE action = 'LOGIN' AND actor_id = ? ORDER BY id DESC LIMIT 1""",
				String.class, userId);
		assertThat(ip).isEqualTo("203.0.113.9");
	}

}
