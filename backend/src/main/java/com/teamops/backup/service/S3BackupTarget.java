package com.teamops.backup.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

/**
 * Amazon S3 or an S3-compatible object store (Lightsail object storage), switched on with BACKUP_TARGET=S3 and the
 * BACKUP_S3_* settings. Objects are uploaded with server-side encryption on top of the backup's own encryption.
 */
public class S3BackupTarget implements BackupTarget, AutoCloseable {

	private final S3Client client;

	private final String bucket;

	private final String prefix;

	private final boolean serverSideEncryption;

	public S3BackupTarget(BackupProperties.S3 settings) {
		if (BackupProperties.blank(settings.bucket()) || BackupProperties.blank(settings.region())) {
			throw new IllegalStateException("BACKUP_TARGET=S3 needs BACKUP_S3_BUCKET and BACKUP_S3_REGION");
		}
		var builder = S3Client.builder()
			.region(Region.of(settings.region().trim()))
			.credentialsProvider(credentials(settings))
			.httpClientBuilder(UrlConnectionHttpClient.builder())
			.forcePathStyle(settings.pathStyle());
		boolean customEndpoint = !BackupProperties.blank(settings.endpoint());
		if (customEndpoint) {
			builder.endpointOverride(URI.create(settings.endpoint().trim()));
		}
		this.client = builder.build();
		this.bucket = settings.bucket().trim();
		this.prefix = normalisePrefix(settings.prefix());
		// Some S3-compatible stores reject the SSE header; AWS S3 always accepts it.
		this.serverSideEncryption = !customEndpoint;
	}

	@Override
	public String describe() {
		return "S3 " + bucket + "/" + prefix;
	}

	@Override
	public void store(Path file, String name) throws IOException {
		try {
			client.putObject(b -> {
				b.bucket(bucket).key(prefix + name);
				if (serverSideEncryption) {
					b.serverSideEncryption(ServerSideEncryption.AES256);
				}
			}, RequestBody.fromFile(file));
		}
		catch (SdkException ex) {
			throw new IOException("Upload to S3 failed: " + ex.getMessage(), ex);
		}
	}

	@Override
	public InputStream open(String name) throws IOException {
		try {
			return client.getObject(b -> b.bucket(bucket).key(prefix + name));
		}
		catch (SdkException ex) {
			throw new IOException("Download from S3 failed: " + ex.getMessage(), ex);
		}
	}

	@Override
	public void delete(String name) throws IOException {
		try {
			client.deleteObject(b -> b.bucket(bucket).key(prefix + name));
		}
		catch (NoSuchKeyException ex) {
			// already gone
		}
		catch (SdkException ex) {
			throw new IOException("Delete from S3 failed: " + ex.getMessage(), ex);
		}
	}

	@Override
	public void close() {
		client.close();
	}

	private static AwsCredentialsProvider credentials(BackupProperties.S3 settings) {
		if (BackupProperties.blank(settings.accessKey())) {
			return DefaultCredentialsProvider.builder().build();
		}
		return StaticCredentialsProvider
			.create(AwsBasicCredentials.create(settings.accessKey().trim(), settings.secretKey().trim()));
	}

	static String normalisePrefix(String prefix) {
		if (prefix == null || prefix.isBlank()) {
			return "";
		}
		String trimmed = prefix.trim().replaceAll("^/+|/+$", "");
		return trimmed.isEmpty() ? "" : trimmed + "/";
	}

}
