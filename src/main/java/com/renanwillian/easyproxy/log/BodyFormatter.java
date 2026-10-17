package com.renanwillian.easyproxy.log;

import com.renanwillian.easyproxy.utils.CompressionUtils;
import com.renanwillian.easyproxy.utils.HttpUtils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Turns a raw HTTP body into text that is safe and readable in a terminal. */
class BodyFormatter {

    private static final List<String> TEXT_SUBTYPE_MARKERS = List.of(
            "json", "xml", "javascript", "ecmascript", "x-www-form-urlencoded", "graphql", "yaml", "csv", "html");
    private static final int BINARY_SNIFF_LENGTH = 1024;

    private final int maxBodySize;

    /**
     * @param maxBodySize maximum number of bytes to print; 0 means no limit.
     */
    BodyFormatter(int maxBodySize) {
        this.maxBodySize = maxBodySize;
    }

    String format(byte[] body, Map<String, List<String>> headers) {
        if (body == null || body.length == 0) return "(empty)";

        String contentEncoding = HttpUtils.firstHeaderValue(headers, "Content-Encoding");
        if (!CompressionUtils.isSupported(contentEncoding)) {
            return "(" + contentEncoding + "-encoded body, " + body.length + " bytes)";
        }

        byte[] decoded;
        try {
            decoded = CompressionUtils.decompress(body, contentEncoding);
        } catch (IOException e) {
            return "(error decompressing " + contentEncoding + " body, " + body.length + " bytes)";
        }

        String contentType = HttpUtils.firstHeaderValue(headers, "Content-Type");
        if (!isText(contentType, decoded)) {
            return "(binary body, " + decoded.length + " bytes" + (contentType != null ? ", " + contentType : "") + ")";
        }

        Charset charset = charsetOf(contentType);
        if (maxBodySize > 0 && decoded.length > maxBodySize) {
            return new String(decoded, 0, maxBodySize, charset)
                    + "... (truncated, " + decoded.length + " bytes total)";
        }
        return new String(decoded, charset);
    }

    private static boolean isText(String contentType, byte[] body) {
        if (contentType == null) return !containsNulByte(body);

        String mediaType = contentType.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
        if (mediaType.startsWith("text/")) return true;
        return TEXT_SUBTYPE_MARKERS.stream().anyMatch(mediaType::contains);
    }

    private static boolean containsNulByte(byte[] body) {
        int limit = Math.min(body.length, BINARY_SNIFF_LENGTH);
        for (int i = 0; i < limit; i++) {
            if (body[i] == 0) return true;
        }
        return false;
    }

    private static Charset charsetOf(String contentType) {
        if (contentType == null) return StandardCharsets.UTF_8;
        for (String param : contentType.split(";")) {
            String[] pair = param.strip().split("=", 2);
            if (pair.length == 2 && pair[0].strip().equalsIgnoreCase("charset")) {
                try {
                    return Charset.forName(pair[1].strip().replace("\"", ""));
                } catch (IllegalArgumentException ignored) {
                    return StandardCharsets.UTF_8;
                }
            }
        }
        return StandardCharsets.UTF_8;
    }
}
