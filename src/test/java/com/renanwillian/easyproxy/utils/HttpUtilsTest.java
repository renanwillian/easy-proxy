package com.renanwillian.easyproxy.utils;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HttpUtilsTest {

    @Test
    void shouldFindFirstHeaderValueIgnoringCase() {
        Map<String, List<String>> headers = Map.of("content-type", List.of("text/plain", "ignored"));

        assertEquals("text/plain", HttpUtils.firstHeaderValue(headers, "Content-Type"));
    }

    @Test
    void shouldReturnNullForMissingOrEmptyHeaders() {
        assertNull(HttpUtils.firstHeaderValue(null, "Content-Type"));
        assertNull(HttpUtils.firstHeaderValue(Map.of(), "Content-Type"));
        assertNull(HttpUtils.firstHeaderValue(Map.of("Content-Type", List.of()), "Content-Type"));
    }

    @Test
    void shouldReturnStatusTexts() {
        assertEquals("OK", HttpUtils.getStatusText(200));
        assertEquals("Bad Gateway", HttpUtils.getStatusText(502));
        assertEquals("", HttpUtils.getStatusText(599));
    }
}
