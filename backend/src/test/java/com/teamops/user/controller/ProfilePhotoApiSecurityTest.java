package com.teamops.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.security.JwtTokenService;
import com.teamops.common.security.UserPrincipalService;
import com.teamops.support.SecuritySliceTest;
import com.teamops.support.SliceAuth;
import com.teamops.user.dto.UserSummary;
import com.teamops.user.entity.UserStatus;
import com.teamops.user.service.ProfilePhotoService;

/**
 * Profile photo API: open to every signed-in user (even one without any permission), who can change their own;
 * the service refuses someone else's photo unless the actor is the Super Admin. Photos are served as attachments.
 */
@WebMvcTest(controllers = ProfilePhotoController.class)
@SecuritySliceTest
class ProfilePhotoApiSecurityTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private UserPrincipalService userPrincipalService;

	@MockitoBean
	private ProfilePhotoService photos;

	private final MockMultipartFile file = new MockMultipartFile("file", "me.png", "image/png", new byte[] { 1 });

	private String bearer(AuthenticatedUser user) {
		return SliceAuth.bearer(jwtTokenService, userPrincipalService, user);
	}

	@Test
	void signedOutCallsAreRejected() throws Exception {
		mvc.perform(get("/api/users/4/photo")).andExpect(status().isUnauthorized());
		mvc.perform(multipart(HttpMethod.PUT, "/api/users/me/photo").file(file)).andExpect(status().isUnauthorized());
		verifyNoInteractions(photos);
	}

	@Test
	void anyoneSignedInChangesTheirOwnPhoto() throws Exception {
		when(photos.setPhoto(eq(9L), any(), any(), any()))
			.thenReturn(new UserSummary(9L, "Nobody", "nobody@teamops.local", null, UserStatus.ACTIVE, "E-9", 77L));
		mvc.perform(multipart(HttpMethod.PUT, "/api/users/me/photo").file(file)
			.header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.NOBODY)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.photoVersion").value(77))
			.andExpect(jsonPath("$.employeeId").value("E-9"));
		mvc.perform(delete("/api/users/me/photo").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.NOBODY)))
			.andExpect(status().isOk());
		verify(photos).removePhoto(eq(9L), any(), any());
	}

	@Test
	void someoneElsesPhotoIsRefusedByTheService() throws Exception {
		when(photos.setPhoto(eq(1L), any(), any(), any()))
			.thenThrow(ApiException.forbidden("PHOTO_FORBIDDEN", "Only a Super Admin can change someone else's photo"));
		mvc.perform(multipart(HttpMethod.PUT, "/api/users/1/photo").file(file)
			.header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("PHOTO_FORBIDDEN"));
	}

	@Test
	void photosAreServedAsAttachments() throws Exception {
		when(photos.photo(4L)).thenReturn(new ProfilePhotoService.Photo(77L, 3, new ByteArrayResource(new byte[] { 1, 2, 3 })));
		mvc.perform(get("/api/users/4/photo").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.NOBODY)))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"))
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"photo-4-77.jpg\""));
	}

}
