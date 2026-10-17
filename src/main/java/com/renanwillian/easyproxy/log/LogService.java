package com.renanwillian.easyproxy.log;

import com.renanwillian.easyproxy.utils.AnsiUtils;
import com.renanwillian.easyproxy.utils.GzipUtils;
import com.renanwillian.easyproxy.utils.TerminalUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class LogService {

    public static final String BREAK = "\n";

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

        if (showDetails) {
            sb.append(AnsiUtils.colorize("\n-------------------------------------------------------------\n", AnsiUtils.WHITE));

            sb.append(AnsiUtils.colorize("Timestamp: ", AnsiUtils.WHITE_BOLD));
            sb.append(AnsiUtils.colorize(logEntry.getTimestamp().toString(), AnsiUtils.WHITE));
            sb.append(BREAK);
            if (showHeaders && logEntry.getRequestHeaders() != null) {
                sb.append(AnsiUtils.colorize("Headers: ", AnsiUtils.WHITE_BOLD));
                logEntry.getRequestHeaders().forEach((k, values) -> values.forEach(v -> sb.append(AnsiUtils.colorize("\n  " + k + ": " + v, AnsiUtils.WHITE))));
                sb.append(BREAK);
            }

            if (logEntry.getRequestBody() != null) {
                sb.append(AnsiUtils.colorize("Request Body: ", AnsiUtils.WHITE_BOLD));
                sb.append(AnsiUtils.colorize(getBodyAsString(logEntry.getRequestBody(), logEntry.getRequestHeaders()), AnsiUtils.WHITE));
                sb.append(BREAK);
            }
            if (logEntry.getResponseBody() != null) {
                sb.append(AnsiUtils.colorize("Response Body: ", AnsiUtils.WHITE_BOLD));
                sb.append(AnsiUtils.colorize(getBodyAsString(logEntry.getResponseBody(), logEntry.getResponseHeaders()), AnsiUtils.WHITE));
                sb.append(BREAK);
            }
            sb.append(AnsiUtils.colorize("-------------------------------------------------------------\n", AnsiUtils.WHITE));
        }

        return sb.toString();
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
