package com.renanwillian.easyproxy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class ProxyHandler implements HttpHandler {

    private final String targetUrl;

    public ProxyHandler(String targetUrl) {
        this.targetUrl = targetUrl;
    }

    @Override
    public void handle(HttpExchange exchange) {
        try {
            HttpURLConnection connection = getHttpURLConnection(exchange);
            forwardRequestHeaders(exchange, connection);
            if (hasBody(exchange)) forwardRequestBody(exchange, connection);

            int responseCode = connection.getResponseCode();
            InputStream responseStream = responseCode >= 400
                    ? connection.getErrorStream()
                    : connection.getInputStream();

            setResponseHeaders(exchange, connection);
            handleResponse(exchange, connection, responseCode, responseStream);
        } catch (Exception e) {
            handleException(exchange, e);
        } finally {
            exchange.close();
        }
    }

    private HttpURLConnection getHttpURLConnection(HttpExchange exchange) throws IOException {
        String fullTargetUrl = targetUrl + exchange.getRequestURI().toString();
        URL url = new URL(fullTargetUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(exchange.getRequestMethod());

        logRequest(exchange, fullTargetUrl);
        return connection;
    }

    private void logRequest(HttpExchange exchange, String fullTargetUrl) {
        System.out.println("Request " + exchange.getRequestMethod() + ": " + fullTargetUrl);
//        exchange.getRequestHeaders().forEach((k, v) -> System.out.println("Header: " + k + " = " + String.join(",", v)));
    }

    private static void forwardRequestHeaders(HttpExchange exchange, HttpURLConnection connection) {
        exchange.getRequestHeaders().forEach((k, v) -> connection.setRequestProperty(k, String.join(",", v)));
    }

    private static boolean hasBody(HttpExchange exchange) {
        if (exchange.getRequestBody() == null) return false;
        String method = exchange.getRequestMethod().toUpperCase();
        return method.equals("POST") || method.equals("PUT") || method.equals("PATCH");
    }

    private void forwardRequestBody(HttpExchange exchange, HttpURLConnection connection) throws IOException {
        connection.setDoOutput(true);
        try (InputStream requestBody = exchange.getRequestBody();
             OutputStream connectionOut = connection.getOutputStream()) {
            byte[] bodyData = readBodyFromInputStream(requestBody);
            System.out.println("Body: " + new String(bodyData));
            connectionOut.write(bodyData);
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

    private static void setResponseHeaders(HttpExchange exchange, HttpURLConnection connection) {
        Map<String, List<String>> headers = connection.getHeaderFields();
        if (headers != null) {
            headers.forEach((key, value) -> {
                if (key != null && value != null) {
                    exchange.getResponseHeaders().add(key, String.join(",", value));
                }
            });
        }
    }

    private void handleResponse(HttpExchange exchange, HttpURLConnection connection, int responseCode,
                                InputStream responseStream) throws IOException {
        if (responseStream != null) {
            exchange.sendResponseHeaders(responseCode, 0);
            logResponse(responseCode, exchange.getResponseHeaders());
            try (OutputStream responseBody = exchange.getResponseBody()) {
                byte[] responseData = readBodyFromInputStream(responseStream);
                logResponseBody(connection, responseData);
                responseBody.write(responseData);
            }
        } else {
            exchange.sendResponseHeaders(responseCode, -1);
        }
    }

    private static void logResponseBody(HttpURLConnection connection, byte[] responseData) throws IOException {
        String contentEncoding = connection.getHeaderField("Content-Encoding");
        if ("gzip".equalsIgnoreCase(contentEncoding)) {
            byte[] buffer = new byte[2048];
            int bytesRead;

            try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(responseData);
                 GZIPInputStream gzipInputStream = new GZIPInputStream(byteArrayInputStream);
                 ByteArrayOutputStream uncompressedOutput = new ByteArrayOutputStream()) {

                while ((bytesRead = gzipInputStream.read(buffer)) != -1) {
                    uncompressedOutput.write(buffer, 0, bytesRead);
                }
                System.out.println("Unzipped Response: " + new String(uncompressedOutput.toByteArray()));
            }
        } else {
            System.out.println("Response: " + new String(responseData));
        }
    }

    private void logResponse(int responseCode, Map<String, List<String>> headers) {
        System.out.println("Response Code: " + responseCode);
//        headers.forEach((k, v) -> logger.info("Response Header: " + k + " = " + String.join(",", v)));
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
