package com.renanwillian.easyproxy.utils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UrlUtilsTest {

    @ParameterizedTest
    @CsvSource({
            "example.com,                       http://example.com",
            "localhost:3000,                    http://localhost:3000",
            "http://example.com/,               http://example.com",
            "https://example.com//,             https://example.com",
            "HTTPS://Example.com,               https://Example.com",
            "https://api.example.com/v1/,       https://api.example.com/v1",
            "'  http://example.com  ',          http://example.com",
            "http://127.0.0.1:8080,             http://127.0.0.1:8080",
    })
    void shouldNormalizeValidUrls(String input, String expected) {
        assertEquals(expected, UrlUtils.sanitizeUrl(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ftp://example.com",
            "file:///etc/passwd",
            "http://",
            "http://exa mple.com",
            "https://example.com?x=1",
            "https://example.com/#top",
            "   ",
    })
    void shouldRejectInvalidUrls(String input) {
        assertThrows(IllegalArgumentException.class, () -> UrlUtils.sanitizeUrl(input));
    }
}
