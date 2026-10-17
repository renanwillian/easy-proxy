package com.renanwillian.easyproxy.log;

import com.renanwillian.easyproxy.utils.AnsiUtils;
import com.renanwillian.easyproxy.utils.GzipUtils;
import com.renanwillian.easyproxy.utils.TerminalUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class LogService {

    public static final String BREAK = "\n";
    private static final String SEPARATOR = "-------------------------------------------------------------";

    private final boolean showHeaders;
    private final boolean showDetails;

    public LogService(boolean showHeaders, boolean showDetails) {
        this.showHeaders = showHeaders;
        this.showDetails = showDetails;
    }

    public void log(LogEntry logEntry) {
        TerminalUtils.println(format(logEntry));
    }

    String format(LogEntry logEntry) {
        StringBuilder sb = new StringBuilder();
        sb.append(AnsiUtils.colorize(logEntry.getMethod(), AnsiUtils.CYAN));
        sb.append(" ").append(logEntry.getPath());

        if (logEntry.getStatusCode() == 0) {
            sb.append(AnsiUtils.colorize(" -> " + logEntry.getResponseMessage(), AnsiUtils.RED_BOLD));
        } else {
            String color = logEntry.getStatusCode() >= 400 ? AnsiUtils.RED_BOLD : AnsiUtils.GREEN_BOLD;
            sb.append(AnsiUtils.colorize(" -> " + logEntry.getStatusCode() + " " + logEntry.getResponseMessage(), color));
        }

        sb.append(AnsiUtils.colorize(" (" + logEntry.getDuration() + "ms)", AnsiUtils.PURPLE_BOLD));

        if (showDetails || showHeaders) {
            sb.append(AnsiUtils.colorize("\n" + SEPARATOR + "\n", AnsiUtils.WHITE));

            if (showDetails) {
                appendLabel(sb, "Timestamp: ");
                sb.append(AnsiUtils.colorize(String.valueOf(logEntry.getTimestamp()), AnsiUtils.WHITE));
                sb.append(BREAK);
            }
            if (showHeaders) {
                appendHeaders(sb, "Request Headers: ", logEntry.getRequestHeaders());
                appendHeaders(sb, "Response Headers: ", logEntry.getResponseHeaders());
            }
            if (showDetails) {
                appendBody(sb, "Request Body: ", logEntry.getRequestBody(), logEntry.getRequestHeaders());
                appendBody(sb, "Response Body: ", logEntry.getResponseBody(), logEntry.getResponseHeaders());
            }

            sb.append(AnsiUtils.colorize(SEPARATOR + "\n", AnsiUtils.WHITE));
        }

        return sb.toString();
    }

    private static void appendLabel(StringBuilder sb, String label) {
        sb.append(AnsiUtils.colorize(label, AnsiUtils.WHITE_BOLD));
    }

    private static void appendHeaders(StringBuilder sb, String label, Map<String, List<String>> headers) {
        if (headers == null || headers.isEmpty()) return;
        appendLabel(sb, label);
        headers.forEach((name, values) -> values.forEach(value ->
                sb.append(AnsiUtils.colorize("\n  " + name + ": " + value, AnsiUtils.WHITE))));
        sb.append(BREAK);
    }

    private void appendBody(StringBuilder sb, String label, byte[] body, Map<String, List<String>> headers) {
        if (body == null) return;
        appendLabel(sb, label);
        sb.append(AnsiUtils.colorize(getBodyAsString(body, headers), AnsiUtils.WHITE));
        sb.append(BREAK);
    }

    private String getBodyAsString(byte[] body, Map<String, List<String>> headers) {
        if (body == null) return "(empty)";
        String contentEncoding = firstHeaderValue(headers, "Content-Encoding");
        try {
            return new String(GzipUtils.uncompressIfGzipped(body, contentEncoding));
        } catch (IOException e) {
            return "(error decompressing body) " + new String(body);
        }
    }

    private static String firstHeaderValue(Map<String, List<String>> headers, String name) {
        if (headers == null) return null;
        return headers.entrySet().stream()
                      .filter(e -> name.equalsIgnoreCase(e.getKey()) && e.getValue() != null && !e.getValue().isEmpty())
                      .map(e -> e.getValue().get(0))
                      .findFirst()
                      .orElse(null);
    }
}
