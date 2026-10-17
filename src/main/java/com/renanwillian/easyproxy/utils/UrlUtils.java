package com.renanwillian.easyproxy.utils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

public class UrlUtils {

    private static final Set<String> SUPPORTED_SCHEMES = Set.of("http", "https");

    private UrlUtils() {}

    /**
     * Validates and normalizes a target URL: adds {@code http://} when no protocol is given, lowercases the
     * protocol and removes trailing slashes.
     *
     * @param url the raw target URL.
     * @return the normalized URL.
     * @throws IllegalArgumentException if the URL is malformed, uses an unsupported protocol, has no host, or
     *                                  contains a query string or fragment.
     */
    public static String sanitizeUrl(String url) {
        if (url == null || url.isBlank()) throw new IllegalArgumentException("URL must not be empty");

        String candidate = url.strip();
        if (!candidate.contains("://")) {
            candidate = "http://" + candidate;
        }

        URI uri;
        try {
            uri = new URI(candidate);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Malformed URL '" + url + "': " + e.getReason(), e);
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_SCHEMES.contains(scheme)) {
            throw new IllegalArgumentException("Unsupported protocol '" + uri.getScheme() + "' in '" + url
                    + "'. Only http and https are supported");
        }
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("Missing host in URL '" + url + "'");
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("URL '" + url + "' must not contain a query string or fragment");
        }

        String normalized = scheme + candidate.substring(uri.getScheme().length());
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
