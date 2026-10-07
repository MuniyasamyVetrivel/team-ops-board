package com.teamops.common.storage;

import java.util.Locale;
import java.util.Map;

import com.teamops.common.exception.ApiException;

/**
 * Upload validation (brief section 64): file type by extension allow-list, size limit, and a sanitised filename.
 * The stored content type comes from the extension, so a client cannot label an HTML file as a PDF.
 */
public final class UploadPolicy {

	/** Allowed extensions and the content type served for each. */
	static final Map<String, String> ALLOWED = Map.ofEntries(Map.entry("pdf", "application/pdf"),
			Map.entry("doc", "application/msword"),
			Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
			Map.entry("xls", "application/vnd.ms-excel"),
			Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
			Map.entry("ppt", "application/vnd.ms-powerpoint"),
			Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
			Map.entry("csv", "text/csv"), Map.entry("txt", "text/plain"), Map.entry("md", "text/markdown"),
			Map.entry("png", "image/png"), Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"),
			Map.entry("gif", "image/gif"), Map.entry("webp", "image/webp"), Map.entry("zip", "application/zip"));

	static final int MAX_NAME_LENGTH = 200;

	private UploadPolicy() {
	}

	/** A validated upload: the safe display name, its extension and content type. */
	public record Checked(String fileName, String extension, String contentType) {
	}

	public static Checked check(String originalName, long size, long maxBytes) {
		if (size <= 0) {
			throw ApiException.badRequest("EMPTY_FILE", "The file is empty");
		}
		if (size > maxBytes) {
			throw ApiException.badRequest("FILE_TOO_LARGE",
					"Files can be at most " + (maxBytes / (1024 * 1024)) + " MB");
		}
		String name = sanitise(originalName);
		int dot = name.lastIndexOf('.');
		String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
		String contentType = ALLOWED.get(extension);
		if (dot <= 0 || contentType == null) {
			throw ApiException.badRequest("FILE_TYPE_NOT_ALLOWED",
					"Allowed file types: " + String.join(", ", ALLOWED.keySet().stream().sorted().toList()));
		}
		return new Checked(name, extension, contentType);
	}

	/** Keeps only the last path segment, strips control and reserved characters, and limits the length. */
	static String sanitise(String originalName) {
		String name = originalName == null ? "" : originalName;
		name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
		name = name.replaceAll("[\\p{Cntrl}<>:\"|?*]", "_").strip();
		while (name.startsWith(".")) {
			name = name.substring(1);
		}
		if (name.length() > MAX_NAME_LENGTH) {
			int dot = name.lastIndexOf('.');
			String extension = dot > 0 ? name.substring(dot) : "";
			name = name.substring(0, MAX_NAME_LENGTH - extension.length()) + extension;
		}
		if (name.isBlank()) {
			throw ApiException.badRequest("INVALID_FILE_NAME", "The file name is not valid");
		}
		return name;
	}

}
