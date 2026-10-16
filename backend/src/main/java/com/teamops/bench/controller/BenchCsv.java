package com.teamops.bench.controller;

import java.nio.charset.StandardCharsets;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** CSV downloads: always attachments with {@code nosniff}. */
final class BenchCsv {

	private static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

	private BenchCsv() {
	}

	static ResponseEntity<byte[]> download(String fileName, byte[] body) {
		return ResponseEntity.ok()
			.contentType(TEXT_CSV)
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
			.header("X-Content-Type-Options", "nosniff")
			.body(body);
	}

}
