package com.renanwillian.easyproxy.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompressionUtilsTest {

    private static final byte[] TEXT = "The quick brown fox jumps over the lazy dog".repeat(50).getBytes();

    private static byte[] gzip(byte[] data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(data);
        }
        return out.toByteArray();
    }

    private static byte[] deflate(byte[] data, boolean raw) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflate = new DeflaterOutputStream(out, new Deflater(Deflater.DEFAULT_COMPRESSION, raw))) {
            deflate.write(data);
        }
        return out.toByteArray();
    }

    @ParameterizedTest
    @ValueSource(strings = {"gzip", "GZIP", " x-gzip "})
    void shouldDecompressGzip(String encoding) throws IOException {
        assertArrayEquals(TEXT, CompressionUtils.decompress(gzip(TEXT), encoding));
    }

    @Test
    void shouldDecompressZlibAndRawDeflate() throws IOException {
        assertArrayEquals(TEXT, CompressionUtils.decompress(deflate(TEXT, false), "deflate"));
        assertArrayEquals(TEXT, CompressionUtils.decompress(deflate(TEXT, true), "deflate"));
    }

    @Test
    void shouldReturnDataUnchangedWithoutEncoding() throws IOException {
        assertArrayEquals(TEXT, CompressionUtils.decompress(TEXT, null));
        assertArrayEquals(TEXT, CompressionUtils.decompress(TEXT, "identity"));
    }

    @Test
    void shouldReportSupportedEncodings() {
        assertTrue(CompressionUtils.isSupported(null));
        assertTrue(CompressionUtils.isSupported("gzip"));
        assertTrue(CompressionUtils.isSupported("deflate"));
        assertFalse(CompressionUtils.isSupported("br"));
        assertFalse(CompressionUtils.isSupported("zstd"));
    }

    @Test
    void shouldFailOnUnsupportedEncoding() {
        assertThrows(IOException.class, () -> CompressionUtils.decompress(TEXT, "br"));
    }

    @Test
    void shouldFailOnTruncatedDataUnlessAllowed() throws IOException {
        byte[] compressed = gzip(TEXT);
        byte[] prefix = Arrays.copyOf(compressed, compressed.length - 10);

        assertThrows(EOFException.class, () -> CompressionUtils.decompress(prefix, "gzip"));

        byte[] partial = CompressionUtils.decompress(prefix, "gzip", true);
        assertTrue(partial.length > 0);
        assertArrayEquals(Arrays.copyOf(TEXT, partial.length), partial);
    }

    @Test
    void shouldHandleEmptyIdentityBody() throws IOException {
        assertEquals(0, CompressionUtils.decompress(new byte[0], null).length);
    }
}
