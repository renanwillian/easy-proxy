package com.renanwillian.easyproxy.proxy;

import com.renanwillian.easyproxy.log.LogEntry;
import com.renanwillian.easyproxy.log.LogService;
import com.renanwillian.easyproxy.utils.HttpUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;

public class ProxyHandler implements HttpHandler {

    /** Hop-by-hop headers (RFC 9110 section 7.6.1): they describe a single connection and are never forwarded. */
    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection", "keep-alive", "proxy-connection", "te", "trailer", "transfer-encoding", "upgrade",
            "proxy-authenticate", "proxy-authorization");
    /** Request headers set by HttpClient itself, or by this proxy. */
    private static final Set<String> MANAGED_REQUEST_HEADERS = Set.of(
            "host", "content-length", "expect", "x-forwarded-for", "x-forwarded-host", "x-forwarded-proto");
    /** Response headers HttpServer sets itself from the length passed to sendResponseHeaders. */
    private static final Set<String> MANAGED_RESPONSE_HEADERS = Set.of("content-length");
    private static final int BUFFER_SIZE = 16 * 1024;

    private final String targetUrl;
    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final LogService logService;
    private final int bodyCaptureLimit;

    public ProxyHandler(String targetUrl, HttpClient httpClient, Duration requestTimeout, LogService logService) {
        this.targetUrl = targetUrl;
        this.httpClient = httpClient;
        this.requestTimeout = requestTimeout;
        this.logService = logService;
        this.bodyCaptureLimit = logService.bodyCaptureLimit();
    }

    @Override
    public void handle(HttpExchange exchange) {
        Instant start = Instant.now();
        LogEntry log = new LogEntry();
        log.setTimestamp(LocalDateTime.now());
        BodyCapture requestCapture = new BodyCapture(bodyCaptureLimit);
        BodyCapture responseCapture = new BodyCapture(bodyCaptureLimit);
        try {
            log.setMethod(exchange.getRequestMethod());
            log.setPath(exchange.getRequestURI().toString());
            log.setRequestHeaders(copyHeaders(exchange.getRequestHeaders()));

            HttpRequest request = buildRequest(exchange, requestCapture);
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            try (InputStream upstreamBody = response.body()) {
                log.setStatusCode(response.statusCode());
                log.setResponseMessage(HttpUtils.getStatusText(response.statusCode()));

                Map<String, List<String>> responseHeaders = getResponseHeaders(response);
                rewriteLocation(exchange, responseHeaders);
                log.setResponseHeaders(responseHeaders);
                forwardResponseHeaders(exchange, responseHeaders);

                forwardResponse(exchange, response, upstreamBody, responseCapture);
            }
        } catch (Exception e) {
            handleException(exchange, e, log);
        } finally {
            exchange.close();
        }
        log.setRequestBody(requestCapture.bytes());
        log.setRequestBodySize(requestCapture.totalSize());
        log.setResponseBody(responseCapture.bytes());
        log.setResponseBodySize(responseCapture.totalSize());

        long duration = Instant.now().toEpochMilli() - start.toEpochMilli();
        log.setDuration(duration);
        logService.log(log);
    }

    private HttpRequest buildRequest(HttpExchange exchange, BodyCapture requestCapture) {
        String fullTargetUrl = targetUrl + exchange.getRequestURI().toString();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(fullTargetUrl))
                                                 .timeout(requestTimeout);

        Map<String, List<String>> requestHeaders = exchange.getRequestHeaders();
        Set<String> excluded = excludedHeaders(requestHeaders, MANAGED_REQUEST_HEADERS);
        requestHeaders.forEach((name, values) -> {
            if (!excluded.contains(name.toLowerCase(Locale.ROOT))) {
                values.forEach(value -> builder.header(name, value));
            }
        });
        addForwardedHeaders(exchange, builder);

        return builder.method(exchange.getRequestMethod(), requestBodyPublisher(exchange, requestCapture)).build();
    }

    /**
     * Tells the target who the original client was. An existing X-Forwarded-For chain is extended; existing
     * X-Forwarded-Host and X-Forwarded-Proto values (set by a proxy in front of this one) are kept.
     */
    private static void addForwardedHeaders(HttpExchange exchange, HttpRequest.Builder builder) {
        var headers = exchange.getRequestHeaders();
        String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();

        String forwardedFor = headers.containsKey("X-Forwarded-For")
                ? String.join(", ", headers.get("X-Forwarded-For")) + ", " + clientIp
                : clientIp;
        builder.header("X-Forwarded-For", forwardedFor);

        String forwardedHost = headers.containsKey("X-Forwarded-Host") ? headers.getFirst("X-Forwarded-Host") : headers.getFirst("Host");
        if (forwardedHost != null) builder.header("X-Forwarded-Host", forwardedHost);

        String forwardedProto = headers.containsKey("X-Forwarded-Proto") ? headers.getFirst("X-Forwarded-Proto") : "http";
        builder.header("X-Forwarded-Proto", forwardedProto);
    }

    /** Hop-by-hop headers, the headers named in Connection, and {@code managed}, all in lowercase. */
    private static Set<String> excludedHeaders(Map<String, List<String>> headers, Set<String> managed) {
        Set<String> excluded = new HashSet<>(HOP_BY_HOP_HEADERS);
        excluded.addAll(managed);
        headers.forEach((name, values) -> {
            if (name != null && name.equalsIgnoreCase("Connection")) {
                values.forEach(value -> Arrays.stream(value.split(","))
                                              .map(token -> token.strip().toLowerCase(Locale.ROOT))
                                              .filter(token -> !token.isEmpty())
                                              .forEach(excluded::add));
            }
        });
        return excluded;
    }

    /** Streams the client's body upstream, keeping its Content-Length when it is known. */
    private static HttpRequest.BodyPublisher requestBodyPublisher(HttpExchange exchange, BodyCapture requestCapture) {
        long contentLength = contentLength(exchange);
        boolean chunked = isChunked(exchange);
        if (contentLength <= 0 && !chunked) return HttpRequest.BodyPublishers.noBody();

        Supplier<InputStream> body = () -> requestCapture.wrap(exchange.getRequestBody());
        HttpRequest.BodyPublisher streaming = HttpRequest.BodyPublishers.ofInputStream(body);
        return contentLength > 0 ? HttpRequest.BodyPublishers.fromPublisher(streaming, contentLength) : streaming;
    }

    private static long contentLength(HttpExchange exchange) {
        String value = exchange.getRequestHeaders().getFirst("Content-Length");
        if (value == null) return -1;
        try {
            return Long.parseLong(value.strip());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static boolean isChunked(HttpExchange exchange) {
        List<String> transferEncoding = exchange.getRequestHeaders().get("Transfer-Encoding");
        return transferEncoding != null
                && transferEncoding.stream().anyMatch(v -> v.toLowerCase(Locale.ROOT).contains("chunked"));
    }

    /**
     * Points redirects to the target back at the proxy, so that the client keeps going through it. For example,
     * with target {@code https://api.example.com/v1}, {@code Location: https://api.example.com/v1/login} becomes
     * {@code http://localhost:8000/login}. Relative and external locations are left untouched.
     */
    private void rewriteLocation(HttpExchange exchange, Map<String, List<String>> responseHeaders) {
        List<String> locations = responseHeaders.get("Location");
        if (locations == null || locations.isEmpty()) return;

        String proxyOrigin = "http://" + proxyAuthority(exchange);
        responseHeaders.put("Location", locations.stream().map(location -> rewriteUrl(location, proxyOrigin)).toList());
    }

    private String rewriteUrl(String location, String proxyOrigin) {
        if (!location.regionMatches(true, 0, targetUrl, 0, targetUrl.length())) return location;

        String rest = location.substring(targetUrl.length());
        if (!rest.isEmpty() && "/?#".indexOf(rest.charAt(0)) < 0) return location; // e.g. /v10 for target /v1
        return proxyOrigin + rest;
    }

    private static String proxyAuthority(HttpExchange exchange) {
        String host = exchange.getRequestHeaders().getFirst("Host");
        if (host != null && !host.isBlank()) return host.strip();
        return "localhost:" + exchange.getLocalAddress().getPort();
    }

    private static void forwardResponseHeaders(HttpExchange exchange, Map<String, List<String>> responseHeaders) {
        responseHeaders.forEach((name, values) -> values.forEach(value -> exchange.getResponseHeaders().add(name, value)));
    }

    private static Map<String, List<String>> copyHeaders(Map<String, List<String>> source) {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        source.forEach((k, v) -> headers.put(k, List.copyOf(v)));
        return headers;
    }

    private static Map<String, List<String>> getResponseHeaders(HttpResponse<?> response) {
        Map<String, List<String>> responseHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Set<String> excluded = excludedHeaders(response.headers().map(), MANAGED_RESPONSE_HEADERS);
        response.headers().map().forEach((key, value) -> {
            if (key != null && value != null && !excluded.contains(key.toLowerCase(Locale.ROOT))) {
                responseHeaders.put(key, List.copyOf(value));
            }
        });
        return responseHeaders;
    }

    /**
     * Sends the status line and streams the body as it arrives. The upstream Content-Length is kept when known,
     * otherwise the body is sent chunked. Each read is flushed so that streams such as SSE are not delayed.
     */
    private static void forwardResponse(HttpExchange exchange, HttpResponse<?> response, InputStream upstreamBody,
                                         BodyCapture responseCapture) throws IOException {
        int status = response.statusCode();
        boolean isHead = "HEAD".equalsIgnoreCase(exchange.getRequestMethod());
        long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);

        if (isHead || status == 204 || status == 304 || status < 200) {
            // HttpServer only accepts a manually set Content-Length for these responses.
            if ((isHead || status == 304) && contentLength >= 0) {
                exchange.getResponseHeaders().set("Content-Length", Long.toString(contentLength));
            }
            exchange.sendResponseHeaders(status, -1);
            return;
        }

        // For HttpServer, a length of 0 means "chunked"; -1 means "no body".
        long responseLength = contentLength == 0 ? -1 : Math.max(contentLength, 0);
        exchange.sendResponseHeaders(status, responseLength);
        if (responseLength == -1) return;

        try (OutputStream out = exchange.getResponseBody()) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = upstreamBody.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                out.flush();
                responseCapture.record(buffer, 0, read);
            }
        }
    }

    private static void handleException(HttpExchange exchange, Exception e, LogEntry log) {
        if (exchange.getResponseCode() != -1) {
            // The status line was already sent; the client sees a truncated response.
            log.setResponseMessage(log.getResponseMessage() + " (response interrupted)");
            return;
        }

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
