package com.renanwillian.easyproxy.log;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogServiceTest {

    static LogEntry baseEntry() {
        LogEntry entry = new LogEntry();
        entry.setMethod("GET");
        entry.setPath("/api");
        entry.setStatusCode(200);
        entry.setResponseMessage("OK");
        entry.setDuration(12L);
        entry.setTimestamp(LocalDateTime.of(2026, 1, 1, 10, 0));
        return entry;
    }

    static byte[] gzip(String text) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Content-Encoding", "content-encoding", "Content-encoding"})
    void shouldDecompressGzipBodyRegardlessOfHeaderCase(String headerName) throws IOException {
        LogEntry entry = baseEntry();
        entry.setResponseHeaders(Map.of(headerName, List.of("gzip")));
        entry.setResponseBody(gzip("{\"hello\":\"world\"}"));

        String output = new LogService(false, true).format(entry);

        assertTrue(output.contains("{\"hello\":\"world\"}"), output);
    }

    @Test
    void shouldIncludeMethodPathStatusAndDuration() {
        String output = new LogService(false, false).format(baseEntry());

        assertTrue(output.contains("GET"), output);
        assertTrue(output.contains("/api"), output);
        assertTrue(output.contains("200 OK"), output);
        assertTrue(output.contains("12ms"), output);
    }

    @Test
    void shouldShowRequestAndResponseHeadersWithoutDetails() {
        LogEntry entry = baseEntry();
        entry.setRequestHeaders(Map.of("Accept", List.of("application/json")));
        entry.setResponseHeaders(Map.of("Set-Cookie", List.of("a=1", "b=2")));
        entry.setResponseBody("secret-body".getBytes());

        String output = new LogService(true, false).format(entry);

        assertTrue(output.contains("Accept: application/json"), output);
        assertTrue(output.contains("Set-Cookie: a=****"), output);
        assertTrue(output.contains("Set-Cookie: b=****"), output);
        assertFalse(output.contains("secret-body"), "Body should only be shown with --details");
    }

    @Test
    void shouldNotShowHeadersWithDetailsOnly() {
        LogEntry entry = baseEntry();
        entry.setRequestHeaders(Map.of("Accept", List.of("application/json")));
        entry.setResponseBody("the-body".getBytes());

        String output = new LogService(false, true).format(entry);

        assertFalse(output.contains("Accept: application/json"), output);
        assertTrue(output.contains("the-body"), output);
    }

    @Test
    void shouldMaskSecretsByDefault() {
        LogEntry entry = baseEntry();
        entry.setRequestHeaders(Map.of("Authorization", List.of("Bearer top-secret")));

        String output = new LogService(true, false).format(entry);

        assertTrue(output.contains("Authorization: Bearer ****"), output);
        assertFalse(output.contains("top-secret"), output);
    }

    @Test
    void shouldShowSecretsWhenEnabled() {
        LogEntry entry = baseEntry();
        entry.setRequestHeaders(Map.of("Authorization", List.of("Bearer top-secret")));

        String output = new LogService(true, false, LogService.DEFAULT_MAX_BODY_SIZE, true).format(entry);

        assertTrue(output.contains("Authorization: Bearer top-secret"), output);
    }
}
