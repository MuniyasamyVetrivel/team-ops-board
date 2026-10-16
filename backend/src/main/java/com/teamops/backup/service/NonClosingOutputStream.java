package com.teamops.backup.service;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** Lets a writer finish one ZIP entry without closing the ZIP: close only flushes. */
final class NonClosingOutputStream extends FilterOutputStream {

	NonClosingOutputStream(OutputStream out) {
		super(out);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		out.write(b, off, len);
	}

	@Override
	public void close() throws IOException {
		out.flush();
	}

}
