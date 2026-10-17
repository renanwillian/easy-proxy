package com.renanwillian.easyproxy.log;

import com.renanwillian.easyproxy.utils.AnsiUtils;
import com.renanwillian.easyproxy.utils.TerminalUtils;

import java.util.List;
import java.util.Map;

public class LogService {

    public static final String BREAK = "\n";
    private static final String SEPARATOR = "-------------------------------------------------------------";

    public static final int DEFAULT_MAX_BODY_SIZE = 4096;

    private final boolean showHeaders;
    private final boolean showDetails;
    private final boolean showSecrets;
    private final int maxBodySize;
    private final BodyFormatter bodyFormatter;

    public LogService(boolean showHeaders, boolean showDetails) {
        this(showHeaders, showDetails, DEFAULT_MAX_BODY_SIZE, false);
    }

    /**
     * @param maxBodySize maximum number of body bytes to print; 0 means no limit.
     * @param showSecrets print credentials (Authorization, cookies, API keys) instead of masking them.
     */
    public LogService(boolean showHeaders, boolean showDetails, int maxBodySize, boolean showSecrets) {
        this.showHeaders = showHeaders;
        this.showDetails = showDetails;
        this.showSecrets = showSecrets;
        this.maxBodySize = maxBodySize;
        this.bodyFormatter = new BodyFormatter(maxBodySize);
    }

    /**
     * How many bytes of each body the proxy should keep for this service to print. Bodies are streamed, so only
     * this prefix is held in memory.
     */
    public int bodyCaptureLimit() {
        if (!showDetails) return 0;
        return maxBodySize == 0 ? Integer.MAX_VALUE : maxBodySize;
    }

    public void log(LogEntry logEntry) {
        TerminalUtils.println(format(logEntry));
    }

    String format(LogEntry logEntry) {
        StringBuilder sb = new StringBuilder();
        sb.append(AnsiUtils.colorize(logEntry.method(), AnsiUtils.CYAN));
        sb.append(" ").append(logEntry.path());

        String color = logEntry.statusCode() >= 400 ? AnsiUtils.RED_BOLD : AnsiUtils.GREEN_BOLD;
        sb.append(AnsiUtils.colorize(" -> " + logEntry.statusCode() + " " + logEntry.responseMessage(), color));

        sb.append(AnsiUtils.colorize(" (" + logEntry.duration().toMillis() + "ms)", AnsiUtils.PURPLE_BOLD));

        if (logEntry.error() != null) {
            sb.append(AnsiUtils.colorize("\n  Cause: " + logEntry.error(), AnsiUtils.RED_BOLD));
        }

        if (showDetails || showHeaders) {
            sb.append(AnsiUtils.colorize("\n" + SEPARATOR + "\n", AnsiUtils.WHITE));

            if (showDetails) {
                appendLabel(sb, "Timestamp: ");
                sb.append(AnsiUtils.colorize(String.valueOf(logEntry.timestamp()), AnsiUtils.WHITE));
                sb.append(BREAK);
            }
            if (showHeaders) {
                appendHeaders(sb, "Request Headers: ", logEntry.requestHeaders());
                appendHeaders(sb, "Response Headers: ", logEntry.responseHeaders());
            }
            if (showDetails) {
                appendBody(sb, "Request Body: ", logEntry.requestBody(), logEntry.requestBodySize(), logEntry.requestHeaders());
                appendBody(sb, "Response Body: ", logEntry.responseBody(), logEntry.responseBodySize(), logEntry.responseHeaders());
            }

            sb.append(AnsiUtils.colorize(SEPARATOR + "\n", AnsiUtils.WHITE));
        }

        return sb.toString();
    }

    private static void appendLabel(StringBuilder sb, String label) {
        sb.append(AnsiUtils.colorize(label, AnsiUtils.WHITE_BOLD));
    }

    private void appendHeaders(StringBuilder sb, String label, Map<String, List<String>> headers) {
        if (headers == null || headers.isEmpty()) return;
        appendLabel(sb, label);
        headers.forEach((name, values) -> values.forEach(value -> {
            String shown = showSecrets ? value : HeaderMasker.mask(name, value);
            sb.append(AnsiUtils.colorize("\n  " + name + ": " + shown, AnsiUtils.WHITE));
        }));
        sb.append(BREAK);
    }

    private void appendBody(StringBuilder sb, String label, byte[] body, long totalSize, Map<String, List<String>> headers) {
        if (body == null) return;
        appendLabel(sb, label);
        sb.append(AnsiUtils.colorize(bodyFormatter.format(body, totalSize, headers), AnsiUtils.WHITE));
        sb.append(BREAK);
    }
}
