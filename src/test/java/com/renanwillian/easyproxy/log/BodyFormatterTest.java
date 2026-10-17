package com.renanwillian.easyproxy.log;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BodyFormatterTest {

    private final BodyFormatter formatter = new BodyFormatter(10);
    private final BodyFormatter unlimited = new BodyFormatter(0);

    private static Map<String, List<String>> headers(String... nameValuePairs) {
        Map<String, List<String>> map = new java.util.HashMap<>();
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            map.put(nameValuePairs[i], List.of(nameValuePairs[i + 1]));
        }
        return map;
    }

    @Test
    void shouldMarkPartiallyCapturedBodiesAsTruncated() {
        String output = unlimited.format("partial".getBytes(), 1000, headers("Content-Type", "text/plain"));

        assertEquals("partial... (truncated, 1000 bytes total)", output);
    }

    @Test
    void shouldDecompressPartiallyCapturedGzipBodies() throws IOException {
        String text = java.util.stream.IntStream.range(0, 5000).mapToObj(Integer::toString)
                                                .collect(java.util.stream.Collectors.joining(","));
        byte[] compressed = LogServiceTest.gzip(text);
        byte[] prefix = java.util.Arrays.copyOf(compressed, compressed.length / 2);

        String output = unlimited.format(prefix, compressed.length, headers("Content-Encoding", "gzip"));

        assertTrue(output.startsWith("0,1,2,3,4,5,6,7,8,9,10"), output);
        assertTrue(output.endsWith("(truncated, " + compressed.length + " bytes total)"), output);
    }

    @Test
    void shouldReportBodiesThatWereNotCaptured() {
        assertEquals("(500 bytes, not captured)", formatter.format(new byte[0], 500, Map.of()));
    }

    @Test
    void shouldPrintEmptyMarkerForEmptyBody() {
        assertEquals("(empty)", formatter.format(new byte[0], Map.of()));
        assertEquals("(empty)", formatter.format(null, Map.of()));
    }

    @Test
    void shouldTruncateLongBodies() {
        String output = formatter.format("0123456789ABCDEF".getBytes(), headers("Content-Type", "text/plain"));

        assertEquals("0123456789... (truncated, 16 bytes total)", output);
    }

    @Test
    void shouldNotTruncateWhenLimitIsZero() {
        String body = "x".repeat(10_000);
        assertEquals(body, unlimited.format(body.getBytes(), headers("Content-Type", "text/plain")));
    }

    @Test
    void shouldSummarizeBinaryContentTypes() {
        String output = formatter.format(new byte[]{(byte) 0x89, 'P', 'N', 'G'}, headers("Content-Type", "image/png"));

        assertEquals("(binary body, 4 bytes, image/png)", output);
    }

    @Test
    void shouldDetectBinaryBodyWithoutContentType() {
        String output = unlimited.format(new byte[]{1, 0, 2, 3}, Map.of());

        assertEquals("(binary body, 4 bytes)", output);
    }

    @Test
    void shouldTreatJsonAndFormsAsText() {
        assertEquals("{\"a\":1}", unlimited.format("{\"a\":1}".getBytes(), headers("Content-Type", "application/problem+json")));
        assertEquals("a=1&b=2", unlimited.format("a=1&b=2".getBytes(), headers("content-type", "application/x-www-form-urlencoded")));
    }

    @Test
    void shouldDecodeUsingCharsetFromContentType() {
        byte[] latin1 = "café".getBytes(StandardCharsets.ISO_8859_1);

        assertEquals("café", unlimited.format(latin1, headers("Content-Type", "text/plain; charset=ISO-8859-1")));
    }

    @Test
    void shouldFallBackToUtf8ForUnknownCharset() {
        byte[] utf8 = "café".getBytes(StandardCharsets.UTF_8);

        assertEquals("café", unlimited.format(utf8, headers("Content-Type", "text/plain; charset=not-a-charset")));
    }

    @Test
    void shouldDecompressDeflateBodies() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflate = new DeflaterOutputStream(out)) {
            deflate.write("hello".getBytes());
        }

        assertEquals("hello", unlimited.format(out.toByteArray(), headers("Content-Encoding", "deflate")));
    }

    @Test
    void shouldSummarizeUnsupportedEncodings() {
        assertEquals("(br-encoded body, 3 bytes)", formatter.format(new byte[]{1, 2, 3}, headers("Content-Encoding", "br")));
    }

    @Test
    void shouldNotDumpGarbageWhenDecompressionFails() {
        String output = formatter.format("not gzip".getBytes(), headers("Content-Encoding", "gzip"));

        assertTrue(output.startsWith("(error decompressing gzip body"), output);
    }
}
