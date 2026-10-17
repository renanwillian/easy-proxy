package com.renanwillian.easyproxy.log;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * One proxied request, as shown in the log.
 *
 * @param requestBody      the captured beginning of the request body, or null if there was none.
 * @param requestBodySize  the full size of the request body, which may be larger than {@code requestBody}.
 * @param responseBody     the captured beginning of the response body, or null if there was none.
 * @param responseBodySize the full size of the response body, which may be larger than {@code responseBody}.
 * @param error            why the request failed, if it did.
 */
public record LogEntry(String method,
                       String path,
                       int statusCode,
                       String responseMessage,
                       Duration duration,
                       LocalDateTime timestamp,
                       Map<String, List<String>> requestHeaders,
                       byte[] requestBody,
                       long requestBodySize,
                       Map<String, List<String>> responseHeaders,
                       byte[] responseBody,
                       long responseBodySize,
                       String error) {

    public static Builder builder() {
        return new Builder();
    }

    /** Collects the entry's fields while the request is being proxied. */
    public static final class Builder {
        private String method;
        private String path;
        private int statusCode;
        private String responseMessage;
        private Duration duration = Duration.ZERO;
        private LocalDateTime timestamp;
        private Map<String, List<String>> requestHeaders;
        private byte[] requestBody;
        private long requestBodySize;
        private Map<String, List<String>> responseHeaders;
        private byte[] responseBody;
        private long responseBodySize;
        private String error;

        private Builder() {}

        public Builder method(String method) { this.method = method; return this; }
        public Builder path(String path) { this.path = path; return this; }
        public Builder statusCode(int statusCode) { this.statusCode = statusCode; return this; }
        public Builder responseMessage(String responseMessage) { this.responseMessage = responseMessage; return this; }
        public Builder duration(Duration duration) { this.duration = duration; return this; }
        public Builder timestamp(LocalDateTime timestamp) { this.timestamp = timestamp; return this; }
        public Builder requestHeaders(Map<String, List<String>> headers) { this.requestHeaders = headers; return this; }
        public Builder requestBody(byte[] requestBody) { this.requestBody = requestBody; return this; }
        public Builder requestBodySize(long size) { this.requestBodySize = size; return this; }
        public Builder responseHeaders(Map<String, List<String>> headers) { this.responseHeaders = headers; return this; }
        public Builder responseBody(byte[] responseBody) { this.responseBody = responseBody; return this; }
        public Builder responseBodySize(long size) { this.responseBodySize = size; return this; }
        public Builder error(String error) { this.error = error; return this; }

        public LogEntry build() {
            return new LogEntry(method, path, statusCode, responseMessage, duration, timestamp, requestHeaders,
                    requestBody, requestBodySize, responseHeaders, responseBody, responseBodySize, error);
        }
    }
}
