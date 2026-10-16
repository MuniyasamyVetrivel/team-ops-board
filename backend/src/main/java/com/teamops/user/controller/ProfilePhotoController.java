package com.teamops.user.controller;

import java.time.Duration;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.user.dto.UserSummary;
import com.teamops.user.service.ProfilePhotoService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * Profile photos (Phase X4). Any signed-in user can see photos and change their own ({@code /me}); changing someone
 * else's needs the Super Admin (403 otherwise, checked by the service). Uploads are multipart {@code file}: JPG, PNG
 * or WebP, up to 2 MB. The photo is served as an attachment with {@code nosniff} like every download; clients fetch
 * it with their token, and may cache it by {@code photoVersion} (in every {@link UserSummary}).
 */
@RestController
@PreAuthorize("isAuthenticated()")
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class ProfilePhotoController {

	private final ProfilePhotoService photos;

	@PutMapping(path = "/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public UserSummary setMine(@RequestParam("file") MultipartFile file, @AuthenticationPrincipal AuthenticatedUser actor,
			HttpServletRequest request) {
		return photos.setPhoto(actor.id(), file, actor, ClientInfo.from(request));
	}

	@DeleteMapping("/me/photo")
	public UserSummary removeMine(@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
		return photos.removePhoto(actor.id(), actor, ClientInfo.from(request));
	}

	@PutMapping(path = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public UserSummary set(@PathVariable Long id, @RequestParam("file") MultipartFile file,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
		return photos.setPhoto(id, file, actor, ClientInfo.from(request));
	}

	@DeleteMapping("/{id}/photo")
	public UserSummary remove(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor,
			HttpServletRequest request) {
		return photos.removePhoto(id, actor, ClientInfo.from(request));
	}

	/** A new photo has a new version (and URL query), so a cached copy can be kept for a day. */
	@GetMapping("/{id}/photo")
	public ResponseEntity<Resource> get(@PathVariable Long id) {
		ProfilePhotoService.Photo photo = photos.photo(id);
		return ResponseEntity.ok()
			.contentType(MediaType.IMAGE_JPEG)
			.contentLength(photo.sizeBytes())
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename("photo-" + id + "-" + photo.version() + ".jpg").build().toString())
			.header("X-Content-Type-Options", "nosniff")
			.cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
			.body(photo.content());
	}

}
