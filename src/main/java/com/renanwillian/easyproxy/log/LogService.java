package com.renanwillian.easyproxy.log;

import com.renanwillian.easyproxy.utils.AnsiUtils;
import com.renanwillian.easyproxy.utils.GzipUtils;

import java.io.IOException;
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
            if (showHeaders && logEntry.getRequestHeaders() != null) {
                sb.append(AnsiUtils.colorize("Headers: ", AnsiUtils.WHITE_BOLD));
                logEntry.getRequestHeaders().forEach((k, v) -> sb.append(AnsiUtils.colorize("  " + k + ": " + v + BREAK, AnsiUtils.WHITE)));
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

        System.out.println(sb);
    }

    private String getBodyAsString(byte[] body, Map<String, String> headers) {
        if (body == null) return "(empty)";
        String contentEncoding = headers != null ? headers.getOrDefault("Content-Encoding", null) : null;
        try {
            return new String(GzipUtils.uncompressIfGzipped(body, contentEncoding));
        } catch (IOException e) {
            return "(error decompressing body) " + new String(body);
        }
    }
}
