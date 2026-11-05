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

    @Test
    void shouldEscapeControlCharactersFromClientAndTarget() {
        LogEntry.Builder entry = baseEntry();
        entry.method("GET\u001b[2K");
        entry.path("/a\rGET /fake");
        entry.requestHeaders(Map.of("X-Evil", List.of("\u001b]52;c;cm0gLXJmIH4=\u0007")));
        entry.responseHeaders(Map.of("Content-Type", List.of("text/plain")));
        entry.responseBody("\u001b[1A\u001b[2Kfake line".getBytes(StandardCharsets.UTF_8));
        entry.error("IOException: \u001b[31mboom");

        String output = new LogService(true, true).format(entry.build());

        for (String raw : List.of("\u001b[2K", "\r", "\u001b]52", "\u0007", "\u001b[1A", "\u001b[31m")) {
            assertFalse(output.contains(raw), () -> "Raw " + raw.replace("\u001b", "ESC") + " in: " + output);
        }
        assertTrue(output.contains("GET\\x1b[2K"), output);
        assertTrue(output.contains("/a\\x0dGET /fake"), output);
        assertTrue(output.contains("X-Evil: \\x1b]52;c;cm0gLXJmIH4=\\x07"), output);
        assertTrue(output.contains("\\x1b[1A\\x1b[2Kfake line"), output);
        assertTrue(output.contains("IOException: \\x1b[31mboom"), output);
    }
}
