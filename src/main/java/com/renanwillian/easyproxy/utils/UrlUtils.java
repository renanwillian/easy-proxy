package com.renanwillian.easyproxy.utils;

public class UrlUtils {

    private static final String HTTP_PROTOCOL = "http://";
    private static final String HTTPS_PROTOCOL = "https://";

    private UrlUtils() {}

    /**
     * Ensures the URL has a valid protocol (http or https) and removes the trailing slash if present.
     *
     * @param url the raw target URL.
     * @return the sanitized URL with proper protocol and no trailing slash.
     */
    public static String sanitizeUrl(String url) {
        if (!url.startsWith(HTTP_PROTOCOL) && !url.startsWith(HTTPS_PROTOCOL)) {
            url = HTTP_PROTOCOL + url;
        }

        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        return url;
    }
}
