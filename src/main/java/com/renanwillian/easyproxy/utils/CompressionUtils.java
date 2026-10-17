package com.renanwillian.easyproxy.utils;

import java.io.ByteArrayInputStream;
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
        return switch (normalize(contentEncoding)) {
            case "", "identity" -> data;
            case "gzip", "x-gzip" -> readAll(new GZIPInputStream(new ByteArrayInputStream(data)));
            case "deflate" -> inflate(data);
            default -> throw new IOException("Unsupported content encoding: " + contentEncoding);
        };
    }

    // "deflate" should be zlib-wrapped, but some servers send raw deflate data.
    private static byte[] inflate(byte[] data) throws IOException {
        try {
            return readAll(new InflaterInputStream(new ByteArrayInputStream(data)));
        } catch (ZipException e) {
            return readAll(new InflaterInputStream(new ByteArrayInputStream(data), new Inflater(true)));
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try (in) {
            return in.readAllBytes();
        }
    }

    private static String normalize(String contentEncoding) {
        return contentEncoding == null ? "" : contentEncoding.strip().toLowerCase(Locale.ROOT);
    }
}
