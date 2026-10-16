package com.teamops.vendor.controller;

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

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.core.io.ByteArrayResource;
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
import com.teamops.module.service.ModuleAccessService;
import com.teamops.support.SecuritySliceTest;
import com.teamops.support.SliceAuth;
import com.teamops.vendor.dto.VendorDtos.VendorDto;
import com.teamops.vendor.entity.VendorCategory;
import com.teamops.vendor.entity.VendorStatus;
import com.teamops.vendor.service.ContractDocumentService.Download;
import com.teamops.vendor.service.VendorContractService;
import com.teamops.vendor.service.VendorService;

/**
 * Vendors is VENDOR_VIEW to read and VENDOR_EDIT to change (Super Admin only by default), on top of the module switch
 * that guards every request under /api/modules/vendors/.
 */
@WebMvcTest(controllers = { VendorController.class, VendorContractController.class, VendorImportController.class })
@SecuritySliceTest
class VendorApiSecurityTest {

	/** Read-only access, as a grant to Finance or Procurement would give. */
	private static final AuthenticatedUser VIEWER = new AuthenticatedUser(14L, "hr@teamops.local", "HR Viewer", 5L,
			Set.of("EMPLOYEE"), SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "VENDOR_VIEW"));

	private static final String VENDOR_BODY = "{\"name\":\"Atlassian\",\"category\":\"SOFTWARE\","
			+ "\"contacts\":[{\"name\":\"Priya\",\"email\":\"priya@atlassian.example\"}]}";

	private static final String CONTRACT_BODY = "{\"vendorId\":5,\"title\":\"Jira Cloud\",\"startDate\":\"2026-03-01\","
			+ "\"value\":18500.00,\"currency\":\"AUD\",\"renewalDate\":\"2027-03-01\",\"autoRenew\":true}";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private UserPrincipalService userPrincipalService;

	@MockitoBean
	private ModuleAccessService moduleAccess;

	@MockitoBean
	private VendorService vendors;

	@MockitoBean
	private VendorContractService contracts;

	@MockitoBean
	private CsvImportService imports;

	private String bearer(AuthenticatedUser user) {
		return SliceAuth.bearer(jwtTokenService, userPrincipalService, user);
	}

	private static MockMultipartFile pdf() {
		return new MockMultipartFile("file", "contract.pdf", "application/pdf", "%PDF-1.4".getBytes());
	}

	@Test
	void nobodyWithoutVendorPermissionsGetsIn() throws Exception {
		mvc.perform(get("/api/modules/vendors/vendors")).andExpect(status().isUnauthorized());
		for (AuthenticatedUser user : List.of(SliceAuth.EMPLOYEE, SliceAuth.USER_ADMIN, SliceAuth.NOBODY)) {
			String token = bearer(user);
			for (AbstractMockHttpServletRequestBuilder<?> request : List.<AbstractMockHttpServletRequestBuilder<?>>of(get("/api/modules/vendors/vendors"),
					get("/api/modules/vendors/vendors/3"), get("/api/modules/vendors/vendors/3/links"),
					get("/api/modules/vendors/vendors/export"), get("/api/modules/vendors/contracts"),
					get("/api/modules/vendors/contracts/3/documents"), get("/api/modules/vendors/contracts/3/documents/9"),
					get("/api/modules/vendors/contracts/export"), get("/api/modules/vendors/imports"),
					post("/api/modules/vendors/vendors").contentType(MediaType.APPLICATION_JSON).content(VENDOR_BODY),
					delete("/api/modules/vendors/contracts/3"))) {
				mvc.perform(request.header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
			}
		}
		verifyNoInteractions(vendors, contracts, imports);
	}

	@Test
	void vendorViewReadsAndDownloadsButOnlyVendorEditChangesUploadsOrImports() throws Exception {
		when(vendors.search(any(), any())).thenReturn(new PageResponse<>(List.of(vendor()), 0, 25, 1, 1));
		when(vendors.export(any())).thenReturn("name\r\n".getBytes());
		when(contracts.download(3L, 9L))
			.thenReturn(new Download("signed contract.pdf", "application/pdf", 8, new ByteArrayResource("%PDF-1.4".getBytes())));
		String token = bearer(VIEWER);

		mvc.perform(get("/api/modules/vendors/vendors").param("status", "ACTIVE").param("category", "SOFTWARE")
			.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].name").value("Atlassian"))
			.andExpect(jsonPath("$.content[0].contractCount").value(2));
		mvc.perform(get("/api/modules/vendors/vendors/export").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"vendors.csv\""))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"));
		mvc.perform(get("/api/modules/vendors/contracts/3/documents/9").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
					"attachment; filename=\"signed contract.pdf\"; filename*=UTF-8''signed%20contract.pdf"))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"));
		mvc.perform(get("/api/modules/vendors/contracts").param("status", "ACTIVE").param("renewalWithinDays", "30")
			.header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk());
		verify(moduleAccess, atLeastOnce()).requireAccess(VIEWER, "vendors", false);

		for (AbstractMockHttpServletRequestBuilder<?> request : List.<AbstractMockHttpServletRequestBuilder<?>>of(
				post("/api/modules/vendors/vendors").contentType(MediaType.APPLICATION_JSON).content(VENDOR_BODY),
				put("/api/modules/vendors/vendors/3").contentType(MediaType.APPLICATION_JSON).content(VENDOR_BODY),
				delete("/api/modules/vendors/vendors/3"),
				post("/api/modules/vendors/contracts").contentType(MediaType.APPLICATION_JSON).content(CONTRACT_BODY),
				put("/api/modules/vendors/contracts/3").contentType(MediaType.APPLICATION_JSON).content(CONTRACT_BODY),
				delete("/api/modules/vendors/contracts/3"), multipart("/api/modules/vendors/contracts/3/documents").file(pdf()),
				delete("/api/modules/vendors/contracts/3/documents/9"), get("/api/modules/vendors/imports"),
				get("/api/modules/vendors/imports/vendors/template"),
				multipart("/api/modules/vendors/imports/vendors/preview").file(pdf()))) {
			mvc.perform(request.header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
		}
		verifyNoInteractions(imports);
	}

	@Test
	void theSuperAdminChangesWithValidatedInput() throws Exception {
		when(vendors.create(any(), any(), any())).thenReturn(vendor());
		String token = bearer(SliceAuth.SUPER_ADMIN);

		mvc.perform(post("/api/modules/vendors/vendors").header(HttpHeaders.AUTHORIZATION, token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(VENDOR_BODY)).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(5));
		for (String body : List.of("{\"name\":\" \"}", "{\"name\":\"Canva\",\"rating\":6}",
				"{\"name\":\"Canva\",\"contacts\":[{\"name\":\"Meera\",\"email\":\"not-an-email\"}]}",
				"{\"name\":\"Canva\",\"contacts\":[{\"email\":\"meera@canva.example\"}]}")) {
			mvc.perform(post("/api/modules/vendors/vendors").header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body)).andExpect(status().isBadRequest());
		}
		for (String body : List.of("{\"vendorId\":5,\"title\":\"Jira\",\"startDate\":\"2026-03-01\",\"value\":-1}",
				"{\"vendorId\":5,\"title\":\"Jira\",\"startDate\":\"2026-03-01\",\"currency\":\"RUPEES\"}",
				"{\"vendorId\":5,\"title\":\"Jira\",\"startDate\":\"2026-03-01\",\"noticePeriodDays\":731}",
				"{\"vendorId\":5,\"title\":\"Jira\"}")) {
			mvc.perform(post("/api/modules/vendors/contracts").header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body)).andExpect(status().isBadRequest());
		}
		mvc.perform(post("/api/modules/vendors/contracts").header(HttpHeaders.AUTHORIZATION, token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(CONTRACT_BODY)).andExpect(status().isCreated());
		verify(contracts).create(any(), eq(SliceAuth.SUPER_ADMIN), any());
		mvc.perform(get("/api/modules/vendors/vendors").param("sort", "spend,desc").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isBadRequest());
		mvc.perform(get("/api/modules/vendors/contracts").param("status", "SOMEDAY").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isBadRequest());
		mvc.perform(multipart("/api/modules/vendors/contracts/3/documents").file(pdf()).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk());
		verify(contracts).addDocument(eq(3L), any(), eq(SliceAuth.SUPER_ADMIN), any());
		verify(moduleAccess, atLeastOnce()).requireAccess(eq(SliceAuth.SUPER_ADMIN), eq("vendors"), eq(true));
	}

	@Test
	void aModuleSwitchedOffNeverReachesTheServices() throws Exception {
		when(moduleAccess.requireAccess(any(), eq("vendors"), anyBoolean()))
			.thenThrow(ApiException.forbidden("MODULE_DISABLED", "Vendors is not switched on for your department"));
		String token = bearer(SliceAuth.SUPER_ADMIN);

		mvc.perform(get("/api/modules/vendors/vendors").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("MODULE_DISABLED"));
		mvc.perform(post("/api/modules/vendors/contracts").header(HttpHeaders.AUTHORIZATION, token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(CONTRACT_BODY)).andExpect(status().isForbidden());
		mvc.perform(get("/api/modules/vendors/contracts/3/documents/9").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isForbidden());
		verifyNoInteractions(vendors, contracts, imports);
	}

	private static VendorDto vendor() {
		return new VendorDto(5L, "Atlassian", null, VendorCategory.SOFTWARE, "Australia", null, null, null,
				VendorStatus.ACTIVE, 4, null, List.of(), 2, LocalDate.of(2027, 3, 1), 0, null, null);
	}

}
