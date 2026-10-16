package com.teamops.user.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import com.teamops.common.exception.ApiException;

/**
 * Turns an uploaded profile photo into the stored one (Phase X4): the upload must be a JPG, PNG or WebP image (by
 * its content, not its name) of at most 2 MB; it is cropped to a centred square (the browser already crops, this only
 * guards against other clients), resized to at most {@value #OUTPUT_SIZE} pixels and re-encoded as JPEG. Re-encoding
 * drops everything but the pixels: EXIF data such as GPS position never reaches storage, and nothing but a plain
 * image is ever served back. The pixel count is checked before decoding, so a small file that would expand into a
 * huge image is refused.
 */
public final class ProfilePhotoImages {

	public static final long MAX_UPLOAD_BYTES = 2L * 1024 * 1024;

	public static final int OUTPUT_SIZE = 512;

	public static final int MIN_SIZE = 64;

	static final long MAX_PIXELS = 40_000_000L;

	private static final float JPEG_QUALITY = 0.88f;

	static {
		// Registers the WebP reader also inside the packaged app, and decodes in memory instead of temp files.
		ImageIO.scanForPlugins();
		ImageIO.setUseCache(false);
	}

	enum Format {

		JPEG("jpeg"), PNG("png"), WEBP("webp");

		final String readerName;

		Format(String readerName) {
			this.readerName = readerName;
		}

	}

	private ProfilePhotoImages() {
	}

	/** The stored photo: a square JPEG of {@code min(shorter side, 512)} pixels. 400 with a stable code otherwise. */
	public static byte[] toSquareJpeg(byte[] upload) {
		if (upload == null || upload.length == 0) {
			throw ApiException.badRequest("PHOTO_EMPTY", "The photo is empty");
		}
		if (upload.length > MAX_UPLOAD_BYTES) {
			throw ApiException.badRequest("PHOTO_TOO_LARGE", "The photo can be at most 2 MB");
		}
		Format format = detect(upload);
		if (format == null) {
			throw ApiException.badRequest("PHOTO_TYPE_NOT_ALLOWED", "The photo must be a JPG, PNG or WebP image");
		}
		BufferedImage image = read(upload, format);
		return writeJpeg(square(image));
	}

	/** By the file's first bytes: JPEG {@code FF D8 FF}, PNG signature, WebP {@code RIFF....WEBP}. */
	static Format detect(byte[] b) {
		if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
			return Format.JPEG;
		}
		byte[] png = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };
		if (b.length >= png.length && Arrays.equals(Arrays.copyOf(b, png.length), png)) {
			return Format.PNG;
		}
		if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E'
				&& b[10] == 'B' && b[11] == 'P') {
			return Format.WEBP;
		}
		return null;
	}

	private static BufferedImage read(byte[] upload, Format format) {
		try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(upload))) {
			Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format.readerName);
			if (in == null || !readers.hasNext()) {
				throw unreadable();
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(in, true, true);
				long width = reader.getWidth(0);
				long height = reader.getHeight(0);
				if (width * height > MAX_PIXELS) {
					throw ApiException.badRequest("PHOTO_TOO_MANY_PIXELS",
							"The photo is too large (" + width + " × " + height + " pixels); use a smaller image");
				}
				if (Math.min(width, height) < MIN_SIZE) {
					throw ApiException.badRequest("PHOTO_TOO_SMALL",
							"The photo must be at least " + MIN_SIZE + " × " + MIN_SIZE + " pixels");
				}
				BufferedImage image = reader.read(0);
				if (image == null) {
					throw unreadable();
				}
				return image;
			}
			finally {
				reader.dispose();
			}
		}
		catch (IOException | RuntimeException ex) {
			if (ex instanceof ApiException api) {
				throw api;
			}
			throw unreadable();
		}
	}

	/** Centred square crop, scaled down (never up) to {@link #OUTPUT_SIZE}, on white (transparent PNGs). */
	static BufferedImage square(BufferedImage image) {
		int side = Math.min(image.getWidth(), image.getHeight());
		BufferedImage current = image.getSubimage((image.getWidth() - side) / 2, (image.getHeight() - side) / 2, side,
				side);
		int target = Math.min(side, OUTPUT_SIZE);
		int size = side;
		// Halve in steps first: one big bicubic step would look jagged.
		while (size / 2 >= target) {
			size /= 2;
			current = draw(current, size);
		}
		return draw(current, target);
	}

	private static BufferedImage draw(BufferedImage source, int size) {
		BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = out.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(Color.WHITE);
			g.fillRect(0, 0, size, size);
			g.drawImage(source, 0, 0, size, size, null);
		}
		finally {
			g.dispose();
		}
		return out;
	}

	private static byte[] writeJpeg(BufferedImage image) {
		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
			ImageWriteParam param = writer.getDefaultWriteParam();
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(JPEG_QUALITY);
			writer.setOutput(out);
			writer.write(null, new IIOImage(image, null, null), param);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Could not encode the photo", ex);
		}
		finally {
			writer.dispose();
		}
		return bytes.toByteArray();
	}

	private static ApiException unreadable() {
		return ApiException.badRequest("PHOTO_UNREADABLE", "The photo could not be read; use a JPG, PNG or WebP image");
	}

}
