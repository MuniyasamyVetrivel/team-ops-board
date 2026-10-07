package com.teamops.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.teamops.user.entity.RoleCodes;
import com.teamops.user.entity.UserStatus;
import com.teamops.user.repository.UserRepository;
import com.teamops.user.service.NewUser;
import com.teamops.user.service.UserProvisioningService;

import jakarta.servlet.http.Cookie;

/**
 * Full login -> me -> refresh -> logout flow against MySQL. Run with: mvnw verify -Pit. Uses a throwaway user that is
 * deleted afterwards (not @Transactional, because audit entries are written in their own transactions).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.dev-seed.enabled=false")
class AuthFlowIT {

	private static final String PASSWORD = "Integration@Test1";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UserProvisioningService userProvisioningService;

	@Autowired
	private UserRepository userRepository;

	private String email;

	private Long userId;

	@BeforeEach
	void createUser() {
		email = "it-" + UUID.randomUUID() + "@teamops.local";
		userId = userProvisioningService.createUser(new NewUser(email, PASSWORD, "Integration", "Test", "QA", "IT",
				Set.of(RoleCodes.EMPLOYEE), Set.of("MARKETING_VIEW")), null).getId();
	}

	@AfterEach
	void deleteUser() {
		userRepository.deleteById(userId);
	}

	@Test
	void fullSessionLifecycle() throws Exception {
		MvcResult login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email.toUpperCase() + "\",\"password\":\"" + PASSWORD + "\"}"))
			.andExpect(status().isOk())
			.andReturn();
		String accessToken = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
		Cookie refreshCookie = login.getResponse().getCookie("tob_refresh");
		assertThat(refreshCookie).isNotNull();
		assertThat(refreshCookie.isHttpOnly()).isTrue();

		mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(email))
			.andExpect(jsonPath("$.roles[0]").value("EMPLOYEE"))
			.andExpect(jsonPath("$.department.code").value("IT"));

		MvcResult refresh = mvc.perform(post("/api/auth/refresh").cookie(refreshCookie))
			.andExpect(status().isOk())
			.andReturn();
		Cookie rotated = refresh.getResponse().getCookie("tob_refresh");
		assertThat(rotated.getValue()).isNotEqualTo(refreshCookie.getValue());

		mvc.perform(post("/api/auth/logout").cookie(rotated)).andExpect(status().isNoContent());
		mvc.perform(post("/api/auth/refresh").cookie(rotated)).andExpect(status().isUnauthorized());
	}

	@Test
	void wrongPasswordIsRejected() throws Exception {
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\",\"password\":\"wrong-password\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	void disablingUserInvalidatesExistingAccessToken() throws Exception {
		MvcResult login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
			.andExpect(status().isOk())
			.andReturn();
		String accessToken = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");

		userRepository.findById(userId).ifPresent(user -> {
			user.setStatus(UserStatus.DISABLED);
			userRepository.save(user);
		});

		mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
			.andExpect(status().isUnauthorized());
	}

}
