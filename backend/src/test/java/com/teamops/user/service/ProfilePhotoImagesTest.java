package com.teamops.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.teamops.common.exception.ApiException;

/**
 * Profile photo processing: JPG, PNG and WebP (by content) of at most 2 MB become a square JPEG of at most 512 pixels;
 * anything else is refused with a stable code.
 */
class ProfilePhotoImagesTest {

	@Test
	void aLandscapePngBecomesACentredSquareJpegOnWhite() throws IOException {
		BufferedImage source = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = source.createGraphics();
		g.setColor(Color.RED);
		g.fillRect(100, 0, 600, 600); // the centred square is red; the 100 px edges stay transparent
		g.dispose();

		BufferedImage out = decode(ProfilePhotoImages.toSquareJpeg(encode(source, "png")));

		assertThat(out.getWidth()).isEqualTo(512);
		assertThat(out.getHeight()).isEqualTo(512);
		Color centre = new Color(out.getRGB(256, 256));
		assertThat(centre.getRed()).isGreaterThan(200);
		assertThat(centre.getGreen()).isLessThan(60);
	}

	@Test
	void aSmallPhotoIsCroppedButNeverEnlarged() throws IOException {
		BufferedImage out = decode(ProfilePhotoImages.toSquareJpeg(encode(solid(120, 90), "jpg")));
		assertThat(out.getWidth()).isEqualTo(90);
		assertThat(out.getHeight()).isEqualTo(90);
	}

	@Test
	void webpIsRead() throws IOException {
		byte[] webp;
		try (InputStream in = getClass().getResourceAsStream("/photos/sample.webp")) {
			webp = in.readAllBytes();
		}
		assertThat(ProfilePhotoImages.detect(webp)).isEqualTo(ProfilePhotoImages.Format.WEBP);
		BufferedImage out = decode(ProfilePhotoImages.toSquareJpeg(webp));
		assertThat(out.getWidth()).isEqualTo(out.getHeight()).isLessThanOrEqualTo(512).isGreaterThan(300);
	}

	@Test
	void theOutputIsAPlainJpeg() throws IOException {
		byte[] jpeg = ProfilePhotoImages.toSquareJpeg(encode(solid(300, 300), "png"));
		assertThat(ProfilePhotoImages.detect(jpeg)).isEqualTo(ProfilePhotoImages.Format.JPEG);
		assertThat(new String(jpeg, StandardCharsets.ISO_8859_1)).doesNotContain("Exif");
	}

	@Test
	void otherTypesAreRefusedByTheirContentNotTheirName() throws IOException {
		assertCode(encode(solid(100, 100), "gif"), "PHOTO_TYPE_NOT_ALLOWED");
		assertCode("<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes(StandardCharsets.UTF_8), "PHOTO_TYPE_NOT_ALLOWED");
		assertCode(new byte[0], "PHOTO_EMPTY");
	}

	@Test
	void sizeAndDimensionsAreLimited() throws IOException {
		byte[] big = Arrays.copyOf(encode(solid(100, 100), "png"), (int) ProfilePhotoImages.MAX_UPLOAD_BYTES + 1);
		assertCode(big, "PHOTO_TOO_LARGE");
		assertCode(encode(solid(40, 300), "png"), "PHOTO_TOO_SMALL");
		// 7000 × 7000 compresses to a few KB but would need ~200 MB to decode: refused before decoding.
		BufferedImage huge = new BufferedImage(7000, 7000, BufferedImage.TYPE_BYTE_BINARY);
		assertCode(encode(huge, "png"), "PHOTO_TOO_MANY_PIXELS");
	}

	@Test
	void aDamagedImageIsRefused() throws IOException {
		byte[] jpeg = encode(solid(200, 200), "jpg");
		byte[] damaged = Arrays.copyOf(jpeg, 40);
		assertCode(damaged, "PHOTO_UNREADABLE");
	}

	private static void assertCode(byte[] upload, String code) {
		assertThatThrownBy(() -> ProfilePhotoImages.toSquareJpeg(upload))
			.isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo(code));
	}

	private static BufferedImage solid(int width, int height) {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setColor(Color.BLUE);
		g.fillRect(0, 0, width, height);
		g.dispose();
		return image;
	}

	private static byte[] encode(BufferedImage image, String format) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		assertThat(ImageIO.write(image, format, out)).isTrue();
		return out.toByteArray();
	}

	private static BufferedImage decode(byte[] jpeg) throws IOException {
		return ImageIO.read(new ByteArrayInputStream(jpeg));
	}

}
