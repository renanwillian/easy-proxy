package com.renanwillian.easyproxy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MockServer {

    private HttpServer server;
    private final int port;
    private final List<MockEndpoint> endpoints = new ArrayList<>();
    private boolean isRunning = false;

    public MockServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        for (MockEndpoint endpoint : endpoints) {
            server.createContext(endpoint.path(), new GenericHandler(endpoint));
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

    public void addEndpoint(String path, String method, int statusCode, String responseBody) {
        endpoints.add(new MockEndpoint(path, method, statusCode, responseBody));
    }

    public boolean isRunning() {
        return isRunning;
    }

    private record MockEndpoint(String path, String method, int statusCode, String responseBody) {}

    private record GenericHandler(MockEndpoint endpoint) implements HttpHandler {

        public static final int METHOD_NOT_ALLOWED = 405;

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!Objects.equals(exchange.getRequestMethod(), endpoint.method())) {
                exchange.sendResponseHeaders(METHOD_NOT_ALLOWED, -1);
                return;
            }

            byte[] responseBytes = endpoint.responseBody().getBytes();
            exchange.sendResponseHeaders(endpoint.statusCode(), responseBytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }
}