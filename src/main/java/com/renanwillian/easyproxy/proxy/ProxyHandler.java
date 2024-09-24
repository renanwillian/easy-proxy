package com.renanwillian.easyproxy.proxy;

import com.renanwillian.easyproxy.log.LogEntry;
import com.renanwillian.easyproxy.log.LogService;
import com.renanwillian.easyproxy.utils.GzipUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProxyHandler implements HttpHandler {

    private final String targetUrl;
    private final LogService logService;

    public ProxyHandler(String targetUrl, LogService logService) {
        this.targetUrl = targetUrl;
        this.logService = logService;
    }

    @Override
    public void handle(HttpExchange exchange) {
        try {
            Instant start = Instant.now();
            LogEntry log = new LogEntry();
            log.setMethod(exchange.getRequestMethod());
            log.setPath(exchange.getRequestURI().toString());

            HttpURLConnection connection = getHttpURLConnection(exchange);

            Map<String, String> requestHeaders = getRequestHeaders(exchange);
            forwardRequestHeaders(requestHeaders, connection);
            log.setRequestHeaders(requestHeaders);

            if (hasRequestBody(exchange)) {
                byte[] requestBody = getRequestBody(exchange);
                log.setRequestBody(requestBody);
                forwardRequestBody(connection, requestBody);
            }

            Map<String, String> responseHeaders = getResponseHeaders(connection);
            forwardResponseHeaders(exchange, responseHeaders);

            byte[] responseBody = getResponseBody(connection);
            forwardResponse(exchange, connection.getResponseCode(), responseBody);

            log.setResponseHeaders(responseHeaders);
            log.setResponseBody(responseBody);
            log.setTimestamp(LocalDateTime.now());
            log.setStatusCode(connection.getResponseCode());
            log.setResponseMessage(connection.getResponseMessage());

            long duration = Instant.now().toEpochMilli() - start.toEpochMilli();
            log.setDuration(duration);
            logService.log(log);
        } catch (Exception e) {
            handleException(exchange, e);
        } finally {
            exchange.close();
        }
    }

    private static void forwardResponseHeaders(HttpExchange exchange, Map<String, String> responseHeaders) {
        responseHeaders.forEach(exchange.getResponseHeaders()::add);
    }

    private byte[] getResponseBody(HttpURLConnection connection) throws IOException {
        InputStream responseStream = connection.getResponseCode() >= 400
                ? connection.getErrorStream()
                : connection.getInputStream();
        if (responseStream != null) {
            return readBodyFromInputStream(responseStream);
        }
        return null;
    }

    private HttpURLConnection getHttpURLConnection(HttpExchange exchange) throws IOException {
        String fullTargetUrl = targetUrl + exchange.getRequestURI().toString();
        URL url = new URL(fullTargetUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(exchange.getRequestMethod());
        return connection;
    }

    private Map<String, String> getRequestHeaders(HttpExchange exchange) {
        Map<String, String> headers = new HashMap<>();
        exchange.getRequestHeaders().forEach((k, v) -> headers.put(k, String.join(",", v)));
        return headers;
    }

    private void forwardRequestHeaders(Map<String, String> requestHeaders, HttpURLConnection connection) {
        requestHeaders.forEach(connection::setRequestProperty);
    }

    private static boolean hasRequestBody(HttpExchange exchange) {
        if (exchange.getRequestBody() == null) return false;
        String method = exchange.getRequestMethod().toUpperCase();
        return method.equals("POST") || method.equals("PUT") || method.equals("PATCH");
    }

    private byte[] getRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream requestBody = exchange.getRequestBody()) {
            return readBodyFromInputStream(requestBody);
        }
    }

    private void forwardRequestBody(HttpURLConnection connection, byte[] requestBody) throws IOException {
        connection.setDoOutput(true);
        try (OutputStream connectionOut = connection.getOutputStream()) {
            connectionOut.write(requestBody);
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

    private Map<String, String> getResponseHeaders(HttpURLConnection connection) {
        Map<String, List<String>> headers = connection.getHeaderFields();
        if (headers == null) return Map.of();
        Map<String, String> responseHeaders = new HashMap<>();
        headers.forEach((key, value) -> {
            if (key != null && value != null) {
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

    private static void logResponseBody(HttpURLConnection connection, byte[] responseData) throws IOException {
        String contentEncoding = connection.getHeaderField("Content-Encoding");
        if ("gzip".equalsIgnoreCase(contentEncoding)) {
            System.out.println("Response: " + new String(GzipUtils.uncompress(responseData)));
        } else {
            System.out.println("Response: " + new String(responseData));
        }
    }

    private static void handleException(HttpExchange exchange, Exception e) {
        e.printStackTrace();
        try {
            exchange.sendResponseHeaders(500, -1);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
