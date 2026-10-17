package com.renanwillian.easyproxy;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MockServer {

    private HttpServer server;
    private final int port;
    private final List<MockEndpoint> endpoints = new ArrayList<>();
    private volatile boolean isRunning = false;
    private volatile Headers lastRequestHeaders;
    private volatile byte[] lastRequestBody;

    public MockServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        for (MockEndpoint endpoint : endpoints) {
            server.createContext(endpoint.path(), new GenericHandler(endpoint, this));
        }
        server.start();
        isRunning = true;
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            isRunning = false;
        }
    }

    /** The port the server is bound to; useful when created with port 0. */
    public int getPort() {
        return server.getAddress().getPort();
    }

    public void addEndpoint(String path, String method, int statusCode, String responseBody) {
        addEndpoint(path, method, statusCode, responseBody.getBytes(StandardCharsets.UTF_8), Map.of());
    }

    public void addEndpoint(String path, String method, int statusCode, byte[] responseBody,
                            Map<String, List<String>> responseHeaders) {
        endpoints.add(new MockEndpoint(path, method, statusCode, responseBody, responseHeaders));
    }

    public boolean isRunning() {
        return isRunning;
    }

    public Headers getLastRequestHeaders() {
        return lastRequestHeaders;
    }

    public byte[] getLastRequestBody() {
        return lastRequestBody;
    }

    private record MockEndpoint(String path, String method, int statusCode, byte[] responseBody,
                                Map<String, List<String>> responseHeaders) {}

    private record GenericHandler(MockEndpoint endpoint, MockServer mockServer) implements HttpHandler {

        public static final int METHOD_NOT_ALLOWED = 405;

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            mockServer.lastRequestHeaders = exchange.getRequestHeaders();
            try (InputStream in = exchange.getRequestBody()) {
                mockServer.lastRequestBody = in.readAllBytes();
            }

            if (!Objects.equals(exchange.getRequestMethod(), endpoint.method())) {
                exchange.sendResponseHeaders(METHOD_NOT_ALLOWED, -1);
                return;
            }

            endpoint.responseHeaders().forEach((name, values) -> values.forEach(v -> exchange.getResponseHeaders().add(name, v)));

            byte[] responseBytes = endpoint.responseBody();
            exchange.sendResponseHeaders(endpoint.statusCode(), responseBytes.length == 0 ? -1 : responseBytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }
}
