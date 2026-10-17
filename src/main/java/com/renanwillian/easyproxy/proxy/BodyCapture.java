package com.renanwillian.easyproxy.proxy;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Records the first {@code limit} bytes of a streamed body for logging, and counts the total size. */
final class BodyCapture {

    private final int limit;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private long totalSize;

    BodyCapture(int limit) {
        this.limit = limit;
    }

    synchronized void record(byte[] data, int offset, int length) {
        if (length <= 0) return;
        totalSize += length;
        int room = limit - buffer.size();
        if (room > 0) buffer.write(data, offset, Math.min(room, length));
    }

    /** The captured bytes, or null when no body went through. */
    synchronized byte[] bytes() {
        return totalSize == 0 ? null : buffer.toByteArray();
    }

    synchronized long totalSize() {
        return totalSize;
    }

    /** Wraps a stream so that everything read from it is recorded. */
    InputStream wrap(InputStream in) {
        return new FilterInputStream(in) {
            @Override
            public int read() throws IOException {
                int b = super.read();
                if (b != -1) record(new byte[]{(byte) b}, 0, 1);
                return b;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                int n = super.read(b, off, len);
                record(b, off, n);
                return n;
            }
        };
    }
}
