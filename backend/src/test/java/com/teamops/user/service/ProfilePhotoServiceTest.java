package com.teamops.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditService;
import com.teamops.common.exception.ApiException;
import com.teamops.common.storage.FileService;
import com.teamops.common.storage.StoredFile;
import com.teamops.common.storage.StoredFileRepository;
import com.teamops.support.SliceAuth;
import com.teamops.user.dto.UserSummary;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;

/** Everyone changes their own photo; only a Super Admin changes someone else's. The old photo file is deleted. */
class ProfilePhotoServiceTest {

	private final UserRepository users = mock(UserRepository.class);

	private final FileService files = mock(FileService.class);

	private final StoredFileRepository storedFiles = mock(StoredFileRepository.class);

	private final AuditService audit = mock(AuditService.class);

	private final ProfilePhotoService service = new ProfilePhotoService(users, files, storedFiles, audit);

	private final User karthik = new User();

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(karthik, "id", SliceAuth.EMPLOYEE.id());
		karthik.setEmail("karthik.raj@teamops.local");
		karthik.setFirstName("Karthik");
		when(users.findById(SliceAuth.EMPLOYEE.id())).thenReturn(Optional.of(karthik));
		when(files.storeBytes(any(), any(), anyLong())).thenAnswer(inv -> {
			StoredFile file = new StoredFile();
			ReflectionTestUtils.setField(file, "id", 900L);
			return file;
		});
	}

	@Test
	void aUserReplacesTheirOwnPhotoAndTheOldFileGoes() throws IOException {
		karthik.setPhotoFileId(800L);
		StoredFile old = new StoredFile();
		when(storedFiles.findById(800L)).thenReturn(Optional.of(old));

		UserSummary result = service.setPhoto(karthik.getId(), png(), SliceAuth.EMPLOYEE, null);

		assertThat(result.photoVersion()).isEqualTo(900L);
		verify(files).storeBytes(argThat(bytes -> bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8),
				eq("profile-photo-4.jpg"), eq(4L));
		verify(files).delete(old);
		verify(audit).record(eq(AuditAction.USER_PHOTO_CHANGED), eq(4L), eq("USER"), eq(4L),
				argThat(d -> "replaced".equals(d.get("change")) && Boolean.TRUE.equals(d.get("self"))), any());
	}

	@Test
	void onlyASuperAdminChangesSomeoneElsesPhoto() throws IOException {
		assertThatThrownBy(() -> service.setPhoto(karthik.getId(), png(), SliceAuth.USER_ADMIN, null))
			.isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("PHOTO_FORBIDDEN"));
		assertThatThrownBy(() -> service.removePhoto(karthik.getId(), SliceAuth.USER_ADMIN, null))
			.isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("PHOTO_FORBIDDEN"));
		verifyNoInteractions(files, audit);

		assertThat(service.setPhoto(karthik.getId(), png(), SliceAuth.SUPER_ADMIN, null).photoVersion()).isEqualTo(900L);
		verify(audit).record(eq(AuditAction.USER_PHOTO_CHANGED), eq(1L), eq("USER"), eq(4L),
				argThat(d -> "added".equals(d.get("change")) && Boolean.FALSE.equals(d.get("self"))), any());
	}

	@Test
	void removingAMissingPhotoIsANoOp() {
		assertThat(service.removePhoto(karthik.getId(), SliceAuth.EMPLOYEE, null).photoVersion()).isNull();
		verify(files, never()).delete(any());
		verifyNoInteractions(audit);
	}

	@Test
	void aFileThatIsNotAnImageIsRefusedBeforeAnythingIsStored() {
		MockMultipartFile pdf = new MockMultipartFile("file", "me.jpg", "image/jpeg", "%PDF-1.7".getBytes());
		assertThatThrownBy(() -> service.setPhoto(karthik.getId(), pdf, SliceAuth.EMPLOYEE, null))
			.isInstanceOfSatisfying(ApiException.class,
					ex -> assertThat(ex.getCode()).isEqualTo("PHOTO_TYPE_NOT_ALLOWED"));
		verifyNoInteractions(files);
	}

	private static MockMultipartFile png() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(200, 150, BufferedImage.TYPE_INT_RGB), "png", out);
		return new MockMultipartFile("file", "me.png", "image/png", out.toByteArray());
	}

}
