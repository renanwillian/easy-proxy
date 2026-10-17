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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

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

            Map<String, List<String>> responseHeaders = getResponseHeaders(response);
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

        exchange.getRequestHeaders().forEach((name, values) -> {
            if (!RESTRICTED_HEADERS.contains(name.toLowerCase())) {
                values.forEach(value -> builder.header(name, value));
            }
        });

        HttpRequest.BodyPublisher bodyPublisher = requestBody != null
                ? HttpRequest.BodyPublishers.ofByteArray(requestBody)
                : HttpRequest.BodyPublishers.noBody();

        return builder.method(exchange.getRequestMethod(), bodyPublisher).build();
    }

    private static void forwardResponseHeaders(HttpExchange exchange, Map<String, List<String>> responseHeaders) {
        responseHeaders.forEach((name, values) -> values.forEach(value -> exchange.getResponseHeaders().add(name, value)));
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

    private Map<String, List<String>> getRequestHeaders(HttpExchange exchange) {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        exchange.getRequestHeaders().forEach((k, v) -> headers.put(k, List.copyOf(v)));
        return headers;
    }

    private Map<String, List<String>> getResponseHeaders(HttpResponse<byte[]> response) {
        Map<String, List<String>> responseHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        response.headers().map().forEach((key, value) -> {
            if (key != null && value != null && !RESPONSE_FRAMING_HEADERS.contains(key.toLowerCase())) {
                responseHeaders.put(key, List.copyOf(value));
            }
        });
        return responseHeaders;
    }

    private void forwardResponse(HttpExchange exchange, int responseCode, byte[] responseData) throws IOException {
        // For HttpServer, a length of 0 means "chunked"; -1 means "no body".
        if (responseData != null && responseData.length > 0) {
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
