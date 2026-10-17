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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MockServer {

    private HttpServer server;
    private final int port;
    private final List<MockEndpoint> endpoints = new ArrayList<>();
    private final List<StreamingEndpoint> streamingEndpoints = new ArrayList<>();
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
        for (StreamingEndpoint endpoint : streamingEndpoints) {
            server.createContext(endpoint.path(), endpoint);
        }
        server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
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
        endpoints.add(new MockEndpoint(path, method, statusCode, responseBody, responseHeaders, Duration.ZERO));
    }

    /** Adds an endpoint that waits {@code delay} before sending the response headers. */
    public void addSlowEndpoint(String path, String method, Duration delay) {
        endpoints.add(new MockEndpoint(path, method, 200, "slow".getBytes(StandardCharsets.UTF_8), Map.of(), delay));
    }

    /**
     * Adds an endpoint that sends {@code chunks} one at a time with chunked encoding, flushing each one and
     * waiting {@code interval} between them, like a server-sent events stream.
     */
    public void addStreamingEndpoint(String path, List<String> chunks, Duration interval) {
        streamingEndpoints.add(new StreamingEndpoint(path, chunks, interval));
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
                                Map<String, List<String>> responseHeaders, Duration delay) {}

    private record StreamingEndpoint(String path, List<String> chunks, Duration interval) implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream os = exchange.getResponseBody()) {
                for (int i = 0; i < chunks.size(); i++) {
                    if (i > 0) Thread.sleep(interval);
                    os.write(chunks.get(i).getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

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

            if (!endpoint.delay().isZero()) {
                try {
                    Thread.sleep(endpoint.delay());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
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
