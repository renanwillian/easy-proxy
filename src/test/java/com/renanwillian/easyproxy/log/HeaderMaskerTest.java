package com.renanwillian.easyproxy.log;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeaderMaskerTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Authorization       | Bearer abc.def.ghi              | Bearer ****",
            "authorization       | Basic dXNlcjpwYXNz              | Basic ****",
            "Authorization       | rawtoken                        | ****",
            "Proxy-Authorization | Basic dXNlcjpwYXNz              | Basic ****",
            "X-Api-Key           | secret                          | ****",
            "Cookie              | session=abc; theme=dark         | session=****; theme=****",
            "Set-Cookie          | id=abc; Path=/; HttpOnly        | id=****; Path=/; HttpOnly",
            "Set-Cookie          | id=abc                          | id=****",
            "Content-Type        | application/json                | application/json",
    })
    void shouldMaskSensitiveHeaders(String name, String value, String expected) {
        assertEquals(expected, HeaderMasker.mask(name, value));
    }
}
