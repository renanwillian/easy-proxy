package com.renanwillian.easyproxy.log;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class LogEntry {

    private String method;
    private String path;
    private int statusCode;
    private String responseMessage;
    private Long duration;
    private LocalDateTime timestamp;
    private Map<String, List<String>> requestHeaders;
    private byte[] requestBody;
    private long requestBodySize;
    private Map<String, List<String>> responseHeaders;
    private byte[] responseBody;
    private long responseBodySize;

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public String getResponseMessage() {
        return responseMessage;
    }

    public void setResponseMessage(String responseMessage) {
        this.responseMessage = responseMessage;
    }

    public Long getDuration() {
        return duration;
    }

    public void setDuration(Long duration) {
        this.duration = duration;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, List<String>> getRequestHeaders() {
        return requestHeaders;
    }

    public void setRequestHeaders(Map<String, List<String>> requestHeaders) {
        this.requestHeaders = requestHeaders;
    }

    public byte[] getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(byte[] requestBody) {
        this.requestBody = requestBody;
    }

    /** Total size of the request body, which may be larger than the captured {@link #getRequestBody()}. */
    public long getRequestBodySize() {
        return requestBodySize;
    }

    public void setRequestBodySize(long requestBodySize) {
        this.requestBodySize = requestBodySize;
    }

    /** Total size of the response body, which may be larger than the captured {@link #getResponseBody()}. */
    public long getResponseBodySize() {
        return responseBodySize;
    }

    public void setResponseBodySize(long responseBodySize) {
        this.responseBodySize = responseBodySize;
    }

    public Map<String, List<String>> getResponseHeaders() {
        return responseHeaders;
    }

    public void setResponseHeaders(Map<String, List<String>> responseHeaders) {
        this.responseHeaders = responseHeaders;
    }

    public byte[] getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(byte[] responseBody) {
        this.responseBody = responseBody;
    }
}
