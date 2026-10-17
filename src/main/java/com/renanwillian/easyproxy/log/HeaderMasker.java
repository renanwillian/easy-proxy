package com.renanwillian.easyproxy.log;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Hides credentials in header values while keeping enough context to debug (scheme, cookie names). */
final class HeaderMasker {

    static final String MASK = "****";

    private static final Set<String> AUTH_HEADERS = Set.of("authorization", "proxy-authorization");
    private static final Set<String> TOKEN_HEADERS = Set.of("x-api-key", "api-key", "x-auth-token", "x-access-token");

    private HeaderMasker() {}

    static String mask(String name, String value) {
        if (value == null) return null;
        String header = name.toLowerCase(Locale.ROOT);

        if (AUTH_HEADERS.contains(header)) return maskAuthorization(value);
        if (TOKEN_HEADERS.contains(header)) return MASK;
        if (header.equals("cookie")) return maskCookies(value);
        if (header.equals("set-cookie")) return maskSetCookie(value);
        return value;
    }

    // "Bearer abc" -> "Bearer ****"
    private static String maskAuthorization(String value) {
        int space = value.indexOf(' ');
        return space > 0 ? value.substring(0, space) + " " + MASK : MASK;
    }

    // "a=1; b=2" -> "a=****; b=****"
    private static String maskCookies(String value) {
        return Arrays.stream(value.split(";"))
                     .map(String::strip)
                     .map(HeaderMasker::maskNameValuePair)
                     .collect(Collectors.joining("; "));
    }

    // "id=abc; Path=/; HttpOnly" -> "id=****; Path=/; HttpOnly"
    private static String maskSetCookie(String value) {
        int semicolon = value.indexOf(';');
        if (semicolon < 0) return maskNameValuePair(value);
        return maskNameValuePair(value.substring(0, semicolon)) + value.substring(semicolon);
    }

    private static String maskNameValuePair(String pair) {
        int equals = pair.indexOf('=');
        return equals >= 0 ? pair.substring(0, equals + 1) + MASK : MASK;
    }
}
