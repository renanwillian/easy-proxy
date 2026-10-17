package com.renanwillian.easyproxy;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * A local upstream server for tests. It listens on a free loopback port, and endpoints can be added at any time,
 * also after {@link #start()}.
 */
public class MockServer {

    private static final int NOT_FOUND = 404;
    private static final int METHOD_NOT_ALLOWED = 405;

    private final List<Endpoint> endpoints = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private volatile boolean isRunning = false;
    private volatile Headers lastRequestHeaders;
    private volatile byte[] lastRequestBody;

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", this::dispatch);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        isRunning = true;
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            isRunning = false;
        }
    }

    public boolean isRunning() {
        return isRunning;
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    public String getUrl() {
        return "http://localhost:" + getPort();
    }

    public Headers getLastRequestHeaders() {
        return lastRequestHeaders;
    }

    public byte[] getLastRequestBody() {
        return lastRequestBody;
    }

    public void addEndpoint(String path, String method, int statusCode, String responseBody) {
        addEndpoint(path, method, statusCode, responseBody.getBytes(StandardCharsets.UTF_8), Map.of());
    }

    public void addEndpoint(String path, String method, int statusCode, byte[] responseBody,
                            Map<String, List<String>> responseHeaders) {
        endpoints.add(new Endpoint(path, method, new FixedResponse(statusCode, responseBody, responseHeaders, Duration.ZERO)));
    }

    /** Adds an endpoint that waits {@code delay} before sending the response headers. */
    public void addSlowEndpoint(String path, String method, Duration delay) {
        endpoints.add(new Endpoint(path, method, new FixedResponse(200, "slow".getBytes(StandardCharsets.UTF_8), Map.of(), delay)));
    }

    /**
     * Adds an endpoint that sends {@code chunks} one at a time with chunked encoding, flushing each one and
     * waiting {@code interval} between them, like a server-sent events stream.
     */
    public void addStreamingEndpoint(String path, List<String> chunks, Duration interval) {
        endpoints.add(new Endpoint(path, "GET", new StreamingResponse(chunks, interval)));
    }

    private void dispatch(HttpExchange exchange) throws IOException {
        try (exchange) {
            lastRequestHeaders = exchange.getRequestHeaders();
            try (InputStream in = exchange.getRequestBody()) {
                lastRequestBody = in.readAllBytes();
            }

            // Same matching rule as HttpServer contexts: the longest path prefix wins.
            String requestPath = exchange.getRequestURI().getPath();
            Optional<Endpoint> match = endpoints.stream()
                                                .filter(e -> requestPath.startsWith(e.path()))
                                                .max(Comparator.comparingInt(e -> e.path().length()));
            if (match.isEmpty()) {
                exchange.sendResponseHeaders(NOT_FOUND, -1);
            } else if (!match.get().method().equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(METHOD_NOT_ALLOWED, -1);
            } else {
                match.get().handler().handle(exchange);
            }
        }
    }

    private record Endpoint(String path, String method, HttpHandler handler) {}

    private record FixedResponse(int statusCode, byte[] body, Map<String, List<String>> headers, Duration delay)
            implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            sleep(delay);
            headers.forEach((name, values) -> values.forEach(v -> exchange.getResponseHeaders().add(name, v)));
            exchange.sendResponseHeaders(statusCode, body.length == 0 ? -1 : body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        }
    }

    private record StreamingResponse(List<String> chunks, Duration interval) implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream os = exchange.getResponseBody()) {
                for (int i = 0; i < chunks.size(); i++) {
                    if (i > 0) sleep(interval);
                    os.write(chunks.get(i).getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }
            }
        }
    }

    private static void sleep(Duration duration) {
        if (duration.isZero()) return;
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
