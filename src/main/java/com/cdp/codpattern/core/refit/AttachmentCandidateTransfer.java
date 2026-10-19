package com.cdp.codpattern.core.refit;

import java.io.ByteArrayOutputStream;

/** Ordered, bounded fragments of one candidate snapshot; the directory itself has no size cap. */
public final class AttachmentCandidateTransfer {
    public static final int MAX_CHUNK_BYTES = 64 * 1024;
    private ByteArrayOutputStream incoming;
    private int expectedBytes;

    /** Returns a complete snapshot, or null while more fragments are required. */
    public byte[] accept(int offset, int totalBytes, boolean complete, byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_CHUNK_BYTES || offset < 0
                || totalBytes <= 0 || (long) offset + bytes.length > totalBytes
                || complete != ((long) offset + bytes.length == totalBytes)) {
            reset();
            throw new IllegalArgumentException("Invalid attachment candidate fragment");
        }
        if (offset == 0) {
            incoming = new ByteArrayOutputStream(Math.min(totalBytes, MAX_CHUNK_BYTES));
            expectedBytes = totalBytes;
        }
        if (incoming == null || expectedBytes != totalBytes || incoming.size() != offset) {
            reset();
            throw new IllegalArgumentException("Out-of-order attachment candidate fragment");
        }
        incoming.write(bytes, 0, bytes.length);
        if (!complete) {
            return null;
        }
        byte[] snapshot = incoming.toByteArray();
        reset();
        return snapshot;
    }

    public void reset() {
        incoming = null;
        expectedBytes = 0;
    }
}
