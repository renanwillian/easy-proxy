package com.renanwillian.easyproxy.log;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogServiceTest {

    static LogEntry.Builder baseEntry() {
        LogEntry.Builder entry = LogEntry.builder();
        entry.method("GET");
        entry.path("/api");
        entry.statusCode(200);
        entry.responseMessage("OK");
        entry.duration(Duration.ofMillis(12));
        entry.timestamp(LocalDateTime.of(2026, 1, 1, 10, 0));
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
        LogEntry.Builder entry = baseEntry();
        entry.responseHeaders(Map.of(headerName, List.of("gzip")));
        entry.responseBody(gzip("{\"hello\":\"world\"}"));

        String output = new LogService(false, true).format(entry.build());

        assertTrue(output.contains("{\"hello\":\"world\"}"), output);
    }

    @Test
    void shouldIncludeMethodPathStatusAndDuration() {
        String output = new LogService(false, false).format(baseEntry().build());

        assertTrue(output.contains("GET"), output);
        assertTrue(output.contains("/api"), output);
        assertTrue(output.contains("200 OK"), output);
        assertTrue(output.contains("12ms"), output);
    }

    @Test
    void shouldShowRequestAndResponseHeadersWithoutDetails() {
        LogEntry.Builder entry = baseEntry();
        entry.requestHeaders(Map.of("Accept", List.of("application/json")));
        entry.responseHeaders(Map.of("Set-Cookie", List.of("a=1", "b=2")));
        entry.responseBody("secret-body".getBytes());

        String output = new LogService(true, false).format(entry.build());

        assertTrue(output.contains("Accept: application/json"), output);
        assertTrue(output.contains("Set-Cookie: a=****"), output);
        assertTrue(output.contains("Set-Cookie: b=****"), output);
        assertFalse(output.contains("secret-body"), "Body should only be shown with --details");
    }

    @Test
    void shouldNotShowHeadersWithDetailsOnly() {
        LogEntry.Builder entry = baseEntry();
        entry.requestHeaders(Map.of("Accept", List.of("application/json")));
        entry.responseBody("the-body".getBytes());

        String output = new LogService(false, true).format(entry.build());

        assertFalse(output.contains("Accept: application/json"), output);
        assertTrue(output.contains("the-body"), output);
    }

    @Test
    void shouldShowErrorCauseEvenWithoutDetails() {
        LogEntry.Builder entry = baseEntry();
        entry.statusCode(502);
        entry.responseMessage("Bad Gateway");
        entry.error("ConnectException: Connection refused");

        String output = new LogService(false, false).format(entry.build());

        assertTrue(output.contains("Cause: ConnectException: Connection refused"), output);
    }

    @Test
    void shouldMaskSecretsByDefault() {
        LogEntry.Builder entry = baseEntry();
        entry.requestHeaders(Map.of("Authorization", List.of("Bearer top-secret")));

        String output = new LogService(true, false).format(entry.build());

        assertTrue(output.contains("Authorization: Bearer ****"), output);
        assertFalse(output.contains("top-secret"), output);
    }

    @Test
    void shouldShowSecretsWhenEnabled() {
        LogEntry.Builder entry = baseEntry();
        entry.requestHeaders(Map.of("Authorization", List.of("Bearer top-secret")));

        String output = new LogService(true, false, LogService.DEFAULT_MAX_BODY_SIZE, true).format(entry.build());

        assertTrue(output.contains("Authorization: Bearer top-secret"), output);
    }
}
