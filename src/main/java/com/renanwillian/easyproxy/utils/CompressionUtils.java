package com.renanwillian.easyproxy.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipException;

public class CompressionUtils {

    private CompressionUtils() {}

    /** Whether {@link #decompress} can handle the given Content-Encoding value. */
    public static boolean isSupported(String contentEncoding) {
        return switch (normalize(contentEncoding)) {
            case "", "identity", "gzip", "x-gzip", "deflate" -> true;
            default -> false;
        };
    }

    /**
     * Decodes a body according to its Content-Encoding.
     *
     * @throws IOException if the data is corrupt or the encoding is not supported.
     */
    public static byte[] decompress(byte[] data, String contentEncoding) throws IOException {
        return decompress(data, contentEncoding, false);
    }

    /**
     * @param truncated whether {@code data} is only the beginning of the body; if so, whatever could be decoded
     *                  before the data ended is returned instead of failing.
     */
    public static byte[] decompress(byte[] data, String contentEncoding, boolean truncated) throws IOException {
        return switch (normalize(contentEncoding)) {
            case "", "identity" -> data;
            case "gzip", "x-gzip" -> readAll(new GZIPInputStream(new ByteArrayInputStream(data)), truncated);
            case "deflate" -> inflate(data, truncated);
            default -> throw new IOException("Unsupported content encoding: " + contentEncoding);
        };
    }

    // "deflate" should be zlib-wrapped, but some servers send raw deflate data.
    private static byte[] inflate(byte[] data, boolean truncated) throws IOException {
        try {
            return readAll(new InflaterInputStream(new ByteArrayInputStream(data)), truncated);
        } catch (ZipException e) {
            return readAll(new InflaterInputStream(new ByteArrayInputStream(data), new Inflater(true)), truncated);
        }
    }

    private static byte[] readAll(InputStream in, boolean truncated) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (in) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } catch (EOFException e) {
            if (!truncated) throw e;
        }
        return out.toByteArray();
    }

    private static String normalize(String contentEncoding) {
        return contentEncoding == null ? "" : contentEncoding.strip().toLowerCase(Locale.ROOT);
    }
}
