package com.renanwillian.easyproxy.proxy;

import com.renanwillian.easyproxy.log.LogEntry;
import com.renanwillian.easyproxy.log.LogService;
import com.renanwillian.easyproxy.utils.HttpUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProxyHandler implements HttpHandler {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
                                                            .connectTimeout(Duration.ofSeconds(5))
                                                            .build();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Set<String> RESTRICTED_HEADERS = Set
            .of("host", "content-length", "connection", "expect", "upgrade", "transfer-encoding");
    private static final Set<String> RESPONSE_FRAMING_HEADERS = Set
            .of("content-length", "transfer-encoding", "connection");

    private final String targetUrl;
    private final LogService logService;

    public ProxyHandler(String targetUrl, LogService logService) {
        this.targetUrl = targetUrl;
        this.logService = logService;
    }

    @Override
    public void handle(HttpExchange exchange) {
        Instant start = Instant.now();
        LogEntry log = new LogEntry();
        log.setTimestamp(LocalDateTime.now());
        try {
            log.setMethod(exchange.getRequestMethod());
            log.setPath(exchange.getRequestURI().toString());

            byte[] requestBody = hasRequestBody(exchange) ? getRequestBody(exchange) : null;
            log.setRequestBody(requestBody);

            HttpRequest request = buildRequest(exchange, requestBody);
            log.setRequestHeaders(getRequestHeaders(exchange));

            HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());

            log.setStatusCode(response.statusCode());
            log.setResponseMessage(HttpUtils.getStatusText(response.statusCode()));

            Map<String, String> responseHeaders = getResponseHeaders(response);
            forwardResponseHeaders(exchange, responseHeaders);

            byte[] responseBody = response.body();
            forwardResponse(exchange, response.statusCode(), responseBody);

            log.setResponseHeaders(responseHeaders);
            log.setResponseBody(responseBody);
        } catch (Exception e) {
            handleException(exchange, e, log);
        } finally {
            exchange.close();
        }
        long duration = Instant.now().toEpochMilli() - start.toEpochMilli();
        log.setDuration(duration);
        logService.log(log);
    }

    private HttpRequest buildRequest(HttpExchange exchange, byte[] requestBody) {
        String fullTargetUrl = targetUrl + exchange.getRequestURI().toString();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(fullTargetUrl))
                                                 .timeout(REQUEST_TIMEOUT);

        getRequestHeaders(exchange).forEach((name, value) -> {
            if (!RESTRICTED_HEADERS.contains(name.toLowerCase())) {
                builder.header(name, value);
            }
        });

        HttpRequest.BodyPublisher bodyPublisher = requestBody != null
                ? HttpRequest.BodyPublishers.ofByteArray(requestBody)
                : HttpRequest.BodyPublishers.noBody();

        return builder.method(exchange.getRequestMethod(), bodyPublisher).build();
    }

    private static void forwardResponseHeaders(HttpExchange exchange, Map<String, String> responseHeaders) {
        responseHeaders.forEach(exchange.getResponseHeaders()::add);
    }

    private static boolean hasRequestBody(HttpExchange exchange) {
        List<String> contentLength = exchange.getRequestHeaders().get("Content-Length");
        if (contentLength != null && !contentLength.isEmpty()) {
            try {
                return Integer.parseInt(contentLength.get(0)) > 0;
            } catch (NumberFormatException ignored) {}
        }
        List<String> transferEncoding = exchange.getRequestHeaders().get("Transfer-Encoding");
        return transferEncoding != null && transferEncoding.contains("chunked");
    }

    private byte[] getRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream requestBody = exchange.getRequestBody()) {
            return readBodyFromInputStream(requestBody);
        }
    }

    private byte[] readBodyFromInputStream(InputStream requestBody) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[2048];
        int bytesRead;
        while ((bytesRead = requestBody.read(buffer)) != -1) {
            byteArrayOutputStream.write(buffer, 0, bytesRead);
        }
        return byteArrayOutputStream.toByteArray();
    }

    private Map<String, String> getRequestHeaders(HttpExchange exchange) {
        Map<String, String> headers = new HashMap<>();
        exchange.getRequestHeaders().forEach((k, v) -> headers.put(k, String.join(",", v)));
        return headers;
    }

    private Map<String, String> getResponseHeaders(HttpResponse<byte[]> response) {
        Map<String, List<String>> headers = response.headers().map();
        if (headers == null) return Map.of();
        Map<String, String> responseHeaders = new HashMap<>();
        headers.forEach((key, value) -> {
            if (key != null && value != null && !RESPONSE_FRAMING_HEADERS.contains(key.toLowerCase())) {
                responseHeaders.put(key, String.join(",", value));
            }
        });
        return responseHeaders;
    }

    private void forwardResponse(HttpExchange exchange, int responseCode, byte[] responseData) throws IOException {
        if (responseData != null) {
            exchange.sendResponseHeaders(responseCode, responseData.length);
            try (OutputStream responseBody = exchange.getResponseBody()) {
                responseBody.write(responseData);
            }
        } else {
            exchange.sendResponseHeaders(responseCode, -1);
        }
    }

    private static void handleException(HttpExchange exchange, Exception e, LogEntry log) {
        int statusCode = 500;
        String message = "Internal Server Error";

        if (isConnectFailure(e)) {
            statusCode = 502;
            message = "Bad Gateway: Unable to connect to upstream server.";
        } else if (e instanceof HttpTimeoutException) {
            statusCode = 504;
            message = "Gateway Timeout: Upstream server took too long to respond.";
        }

        log.setStatusCode(statusCode);
        log.setResponseMessage(message);

        try {
            byte[] responseBytes = message.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
        } catch (Exception ignored) {}
    }

    private static boolean isConnectFailure(Exception e) {
        if (e instanceof HttpConnectTimeoutException) return true;
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof ConnectException) return true;
            cause = cause.getCause();
        }
        return false;
    }
}
