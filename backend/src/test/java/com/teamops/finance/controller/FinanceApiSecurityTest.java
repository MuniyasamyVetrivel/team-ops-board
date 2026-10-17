package com.teamops.finance.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import com.teamops.common.csv.CsvImportService;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.security.JwtTokenService;
import com.teamops.common.security.UserPrincipalService;
import com.teamops.common.web.PageResponse;
import com.teamops.finance.dto.FinanceDtos.Conversion;
import com.teamops.finance.service.ExchangeRateService;
import com.teamops.finance.service.LicenceService;
import com.teamops.module.service.ModuleAccessService;
import com.teamops.support.SecuritySliceTest;
import com.teamops.support.SliceAuth;

/**
 * The Finance Tracker is FINANCE_VIEW to read and FINANCE_EDIT to change (Super Admin only by default), on top of the
 * module switch that guards every request under /api/modules/finance/. Other modules' editors get nothing.
 */
@WebMvcTest(controllers = { LicenceController.class, ExchangeRateController.class, FinanceImportController.class })
@SecuritySliceTest
class FinanceApiSecurityTest {

	/** Read-only access, as a grant to an accountant would give. */
	private static final AuthenticatedUser VIEWER = new AuthenticatedUser(15L, "accounts@teamops.local", "Accounts", 5L,
			Set.of("EMPLOYEE"), SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "FINANCE_VIEW"));

	/** Edits vendors, but holds no finance permission. */
	private static final AuthenticatedUser VENDOR_EDITOR = new AuthenticatedUser(16L, "buyer@teamops.local", "Buyer", 5L,
			Set.of("EMPLOYEE"), SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "VENDOR_VIEW", "VENDOR_EDIT"));

	private static final String LICENCE_BODY = "{\"name\":\"Jira Cloud\",\"billingCycle\":\"YEARLY\",\"cost\":9600,"
			+ "\"currency\":\"AUD\",\"seatsPaid\":20,\"paymentMethod\":\"Corporate Amex\",\"cardLast4\":\"1234\"}";

	private static final String RATE_BODY = "{\"currency\":\"INR\",\"effectiveFrom\":\"2026-10-01\",\"rateToAud\":0.0182}";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private UserPrincipalService userPrincipalService;

	@MockitoBean
	private ModuleAccessService moduleAccess;

	@MockitoBean
	private LicenceService licences;

	@MockitoBean
	private ExchangeRateService rates;

	@MockitoBean
	private CsvImportService imports;

	private String bearer(AuthenticatedUser user) {
		return SliceAuth.bearer(jwtTokenService, userPrincipalService, user);
	}

	@Test
	void nobodyWithoutFinancePermissionsGetsIn() throws Exception {
		mvc.perform(get("/api/modules/finance/licences")).andExpect(status().isUnauthorized());
		for (AuthenticatedUser user : List.of(SliceAuth.EMPLOYEE, SliceAuth.USER_ADMIN, SliceAuth.NOBODY, VENDOR_EDITOR)) {
			String token = bearer(user);
			for (AbstractMockHttpServletRequestBuilder<?> request : List.<AbstractMockHttpServletRequestBuilder<?>>of(
					get("/api/modules/finance/licences"), get("/api/modules/finance/licences/3"),
					get("/api/modules/finance/licences/export"), get("/api/modules/finance/exchange-rates"),
					get("/api/modules/finance/exchange-rates/current"),
					get("/api/modules/finance/exchange-rates/convert").param("amount", "10").param("currency", "INR"),
					get("/api/modules/finance/imports"),
					post("/api/modules/finance/licences").contentType(MediaType.APPLICATION_JSON).content(LICENCE_BODY),
					delete("/api/modules/finance/exchange-rates/3"))) {
				mvc.perform(request.header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
			}
		}
		verifyNoInteractions(licences, rates, imports);
	}

	@Test
	void financeViewReadsAndExportsButOnlyFinanceEditChangesOrImports() throws Exception {
		when(licences.search(any(), any())).thenReturn(new PageResponse<>(List.of(), 0, 25, 0, 0));
		when(licences.export(any())).thenReturn("name\r\n".getBytes());
		when(rates.convert(any(), any(), any())).thenReturn(new Conversion(new BigDecimal("42000.00"), "INR",
				LocalDate.of(2026, 10, 17), new BigDecimal("0.0182"), LocalDate.of(2026, 10, 1), new BigDecimal("764.40")));
		String token = bearer(VIEWER);

		mvc.perform(get("/api/modules/finance/licences").param("status", "TRIAL").param("renewalWithinDays", "30")
			.header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk());
		mvc.perform(get("/api/modules/finance/licences/export").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"licences.csv\""))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"));
		mvc.perform(get("/api/modules/finance/exchange-rates/convert").param("amount", "42000").param("currency", "INR")
			.header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk()).andExpect(jsonPath("$.aud").value(764.40));
		mvc.perform(get("/api/modules/finance/exchange-rates/convert").param("amount", "-1").param("currency", "INR")
			.header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isBadRequest());
		verify(moduleAccess, atLeastOnce()).requireAccess(VIEWER, "finance", false);

		for (AbstractMockHttpServletRequestBuilder<?> request : List.<AbstractMockHttpServletRequestBuilder<?>>of(
				post("/api/modules/finance/licences").contentType(MediaType.APPLICATION_JSON).content(LICENCE_BODY),
				put("/api/modules/finance/licences/3").contentType(MediaType.APPLICATION_JSON).content(LICENCE_BODY),
				delete("/api/modules/finance/licences/3"),
				post("/api/modules/finance/exchange-rates").contentType(MediaType.APPLICATION_JSON).content(RATE_BODY),
				delete("/api/modules/finance/exchange-rates/3"), get("/api/modules/finance/imports"),
				get("/api/modules/finance/imports/licences/template"),
				multipart("/api/modules/finance/imports/licences/preview")
					.file(new MockMultipartFile("file", "licences.csv", "text/csv", "name\n".getBytes())))) {
			mvc.perform(request.header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
		}
		verifyNoInteractions(imports);
	}

	@Test
	void theSuperAdminChangesWithValidatedInput() throws Exception {
		String token = bearer(SliceAuth.SUPER_ADMIN);

		mvc.perform(post("/api/modules/finance/licences").header(HttpHeaders.AUTHORIZATION, token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(LICENCE_BODY)).andExpect(status().isCreated());
		verify(licences).create(any(), eq(SliceAuth.SUPER_ADMIN), any());
		for (String body : List.of("{\"name\":\" \",\"billingCycle\":\"YEARLY\"}", "{\"name\":\"Jira\"}",
				"{\"name\":\"Jira\",\"billingCycle\":\"YEARLY\",\"cardLast4\":\"12345\"}",
				"{\"name\":\"Jira\",\"billingCycle\":\"YEARLY\",\"seatsPaid\":-1}",
				"{\"name\":\"Jira\",\"billingCycle\":\"YEARLY\",\"cost\":-5}",
				"{\"name\":\"Jira\",\"billingCycle\":\"WEEKLY\"}")) {
			mvc.perform(post("/api/modules/finance/licences").header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body)).andExpect(status().isBadRequest());
		}
		for (String body : List.of("{\"currency\":\"INR\",\"effectiveFrom\":\"2026-10-01\",\"rateToAud\":0}",
				"{\"currency\":\"RUPEE\",\"effectiveFrom\":\"2026-10-01\",\"rateToAud\":0.0182}",
				"{\"currency\":\"INR\",\"rateToAud\":0.0182}",
				"{\"currency\":\"INR\",\"effectiveFrom\":\"2026-10-01\",\"rateToAud\":0.123456789}")) {
			mvc.perform(post("/api/modules/finance/exchange-rates").header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body)).andExpect(status().isBadRequest());
		}
		mvc.perform(get("/api/modules/finance/licences").param("sort", "salary,desc").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isBadRequest());
		verify(moduleAccess, atLeastOnce()).requireAccess(eq(SliceAuth.SUPER_ADMIN), eq("finance"), eq(true));
	}

	@Test
	void aModuleSwitchedOffNeverReachesTheServices() throws Exception {
		when(moduleAccess.requireAccess(any(), eq("finance"), anyBoolean()))
			.thenThrow(ApiException.forbidden("MODULE_DISABLED", "The Finance Tracker is not switched on for your department"));
		String token = bearer(SliceAuth.SUPER_ADMIN);

		mvc.perform(get("/api/modules/finance/licences").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("MODULE_DISABLED"));
		mvc.perform(post("/api/modules/finance/exchange-rates").header(HttpHeaders.AUTHORIZATION, token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(RATE_BODY)).andExpect(status().isForbidden());
		verifyNoInteractions(licences, rates, imports);
	}

}
