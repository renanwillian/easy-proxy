package com.renanwillian.easyproxy.proxy;

import com.renanwillian.easyproxy.MockServer;
import com.renanwillian.easyproxy.log.LogEntry;
import com.renanwillian.easyproxy.log.LogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProxyServerTest {

    private static final int MOCK_SERVER_PORT = 9999;
    private static final int PROXY_SERVER_PORT = 8888;
    private static final String TARGET_URL = "http://localhost:" + MOCK_SERVER_PORT;
    private static final String PROXY_URL = "http://localhost:" + PROXY_SERVER_PORT;
    private static final Duration CLIENT_TIMEOUT = Duration.ofMillis(500);

    private ProxyServer proxyServer;
    private MockServer mockServer;
    private HttpClient httpClient;

    @Mock
    private LogService logService;

    @BeforeEach
    void setUp() throws IOException {
        lenient().when(logService.bodyCaptureLimit()).thenReturn(Integer.MAX_VALUE);
        mockServer = new MockServer(MOCK_SERVER_PORT);
        proxyServer = new ProxyServer(ProxyConfig.of(PROXY_SERVER_PORT, TARGET_URL), logService);
        proxyServer.start();

        httpClient = HttpClient.newBuilder()
                               .connectTimeout(CLIENT_TIMEOUT)
                               .build();
    }

    @AfterEach
    void teardown() {
        if (mockServer != null && mockServer.isRunning()) mockServer.stop();
        if (proxyServer != null && proxyServer.isRunning()) proxyServer.stop();
    }

    protected void startMockServer(int statusCode, String method, String path, String responseBody) throws IOException {
        mockServer.addEndpoint(path, method, statusCode, responseBody);
        mockServer.start();
    }

    protected HttpResponse<String> sendRequestToProxyServer(String path, String method) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                                         .uri(URI.create(PROXY_URL + path))
                                         .method(method, HttpRequest.BodyPublishers.noBody())
                                         .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Nested
    @DisplayName("Server Lifecycle Tests")
    class ServerLifecycleTests {
        @Test
        @DisplayName("Server starts successfully")
        void testServerStartsSuccessfully() {
            assertTrue(proxyServer.isRunning());
        }

        @Test
        @DisplayName("Server stops successfully")
        void testServerStopsSuccessfully() {
            proxyServer.stop();
            assertFalse(proxyServer.isRunning());
        }
    }

    @Nested
    @DisplayName("HTTP Methods Forwarding Tests")
    class HttpMethodsTests {
        @ParameterizedTest
        @CsvSource({
                "GET,200",
                "POST,201",
                "PUT,200",
                "PATCH,200",
                "DELETE,204",
                "HEAD,200",
                "OPTIONS,200"
        })
        @DisplayName("Should forward various HTTP methods")
        void shouldForwardVariousHttpMethods(String method, int expectedStatusCode) throws Exception {
            String path = "/api";
            String responseBody = expectedStatusCode != 204 && !method.equals("HEAD") ? "ok" : "";

            startMockServer(expectedStatusCode, method, path, responseBody);

            HttpResponse<String> response = sendRequestToProxyServer(path, method);

            assertEquals(expectedStatusCode, response.statusCode());
            assertEquals(responseBody, response.body());
        }
    }

    @Nested
    @DisplayName("Header Forwarding Tests")
    class HeaderForwardingTests {
        @Test
        @DisplayName("Should forward each Set-Cookie header separately")
        void shouldForwardMultipleSetCookieHeadersSeparately() throws Exception {
            mockServer.addEndpoint("/cookies", "GET", 200, "ok".getBytes(),
                    Map.of("Set-Cookie", List.of("a=1; Path=/; Expires=Wed, 21 Oct 2026 07:28:00 GMT", "b=2; Path=/")));
            mockServer.start();

            HttpResponse<String> response = sendRequestToProxyServer("/cookies", "GET");

            assertEquals(List.of("a=1; Path=/; Expires=Wed, 21 Oct 2026 07:28:00 GMT", "b=2; Path=/"),
                    response.headers().allValues("Set-Cookie"));
        }

        @Test
        @DisplayName("Should forward repeated request headers as separate values")
        void shouldForwardRepeatedRequestHeaders() throws Exception {
            startMockServer(200, "GET", "/echo", "ok");

            HttpRequest request = HttpRequest.newBuilder(URI.create(PROXY_URL + "/echo"))
                                             .header("X-Custom", "one")
                                             .header("X-Custom", "two")
                                             .GET()
                                             .build();
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(List.of("one", "two"), mockServer.getLastRequestHeaders().get("X-Custom"));
        }
    }

    @Test
    @DisplayName("Should not use chunked encoding for empty responses")
    void shouldNotUseChunkedEncodingForEmptyResponses() throws Exception {
        startMockServer(200, "GET", "/empty", "");

        HttpResponse<String> response = sendRequestToProxyServer("/empty", "GET");

        assertEquals(200, response.statusCode());
        assertEquals("", response.body());
        assertTrue(response.headers().firstValue("Transfer-Encoding").isEmpty(),
                "Empty body should not be sent as chunked");
        assertEquals("0", response.headers().firstValue("Content-Length").orElse(null));
    }

    @Test
    @DisplayName("Should return Gateway Timeout when upstream exceeds the configured timeout")
    void shouldReturnGatewayTimeoutWhenUpstreamIsTooSlow() throws Exception {
        mockServer.addSlowEndpoint("/slow", "GET", Duration.ofSeconds(2));
        mockServer.start();

        ProxyConfig config = ProxyConfig.of(0, TARGET_URL).withTimeout(Duration.ofMillis(200));
        try (ProxyServer server = new ProxyServer(config, logService)) {
            server.start();
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + server.getPort() + "/slow")).build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(504, response.statusCode());
        }
    }

    @Nested
    @DisplayName("Forwarded and Hop-by-hop Header Tests")
    class ForwardedHeaderTests {
        @Test
        @DisplayName("Should add X-Forwarded-* headers")
        void shouldAddForwardedHeaders() throws Exception {
            startMockServer(200, "GET", "/fwd", "ok");

            sendRequestToProxyServer("/fwd", "GET");

            var received = mockServer.getLastRequestHeaders();
            assertEquals("127.0.0.1", received.getFirst("X-Forwarded-For"));
            assertEquals("localhost:" + PROXY_SERVER_PORT, received.getFirst("X-Forwarded-Host"));
            assertEquals("http", received.getFirst("X-Forwarded-Proto"));
        }

        @Test
        @DisplayName("Should extend an existing X-Forwarded-For chain")
        void shouldExtendExistingForwardedFor() throws Exception {
            startMockServer(200, "GET", "/fwd", "ok");

            HttpRequest request = HttpRequest.newBuilder(URI.create(PROXY_URL + "/fwd"))
                                             .header("X-Forwarded-For", "203.0.113.7")
                                             .header("X-Forwarded-Proto", "https")
                                             .build();
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            var received = mockServer.getLastRequestHeaders();
            assertEquals(List.of("203.0.113.7, 127.0.0.1"), received.get("X-Forwarded-For"));
            assertEquals(List.of("https"), received.get("X-Forwarded-Proto"));
        }

        @Test
        @DisplayName("Should not forward hop-by-hop request headers")
        void shouldNotForwardHopByHopRequestHeaders() throws Exception {
            startMockServer(200, "GET", "/hop", "ok");

            // HttpClient refuses to send Connection, so write the request by hand.
            String rawRequest = "GET /hop HTTP/1.1\r\n"
                    + "Host: localhost:" + PROXY_SERVER_PORT + "\r\n"
                    + "Connection: close, X-Internal\r\n"
                    + "X-Internal: secret\r\n"
                    + "Keep-Alive: timeout=5\r\n"
                    + "Proxy-Authorization: Basic abc\r\n"
                    + "X-Kept: yes\r\n"
                    + "\r\n";
            try (Socket socket = new Socket("localhost", PROXY_SERVER_PORT)) {
                socket.getOutputStream().write(rawRequest.getBytes(StandardCharsets.US_ASCII));
                socket.getInputStream().readAllBytes();
            }

            var received = mockServer.getLastRequestHeaders();
            assertEquals("yes", received.getFirst("X-Kept"));
            assertFalse(received.containsKey("X-Internal"));
            assertFalse(received.containsKey("Keep-Alive"));
            assertFalse(received.containsKey("Proxy-Authorization"));
        }

        @Test
        @DisplayName("Should not forward hop-by-hop response headers")
        void shouldNotForwardHopByHopResponseHeaders() throws Exception {
            mockServer.addEndpoint("/hop", "GET", 200, "ok".getBytes(), Map.of(
                    "Connection", List.of("X-Internal"),
                    "X-Internal", List.of("secret"),
                    "Proxy-Authenticate", List.of("Basic"),
                    "X-Kept", List.of("yes")));
            mockServer.start();

            HttpResponse<String> response = sendRequestToProxyServer("/hop", "GET");

            assertEquals("yes", response.headers().firstValue("X-Kept").orElse(null));
            assertTrue(response.headers().firstValue("X-Internal").isEmpty());
            assertTrue(response.headers().firstValue("Proxy-Authenticate").isEmpty());
        }
    }

    @Nested
    @DisplayName("Redirect Tests")
    class RedirectTests {
        @ParameterizedTest
        @CsvSource({
                // upstream Location,                   expected Location seen by the client
                "http://localhost:9999/next?x=1,        http://localhost:8888/next?x=1",
                "HTTP://LOCALHOST:9999/next,            http://localhost:8888/next",
                "http://localhost:9999,                 http://localhost:8888",
                "http://localhost:99999/elsewhere,      http://localhost:99999/elsewhere",
                "https://other.example.com/next,        https://other.example.com/next",
                "/relative/path,                        /relative/path",
        })
        @DisplayName("Should point redirects to the target back at the proxy")
        void shouldRewriteLocationHeaders(String upstreamLocation, String expectedLocation) throws Exception {
            mockServer.addEndpoint("/redirect", "GET", 302, new byte[0], Map.of("Location", List.of(upstreamLocation)));
            mockServer.start();

            HttpResponse<String> response = sendRequestToProxyServer("/redirect", "GET");

            assertEquals(302, response.statusCode());
            assertEquals(expectedLocation, response.headers().firstValue("Location").orElse(null));
        }
    }

    @Nested
    @DisplayName("Streaming Tests")
    class StreamingTests {
        @Test
        @DisplayName("Should deliver each chunk as soon as upstream sends it")
        void shouldStreamChunksWithoutWaitingForTheWholeBody() throws Exception {
            Duration interval = Duration.ofMillis(1500);
            mockServer.addStreamingEndpoint("/events", List.of("data: first\n\n", "data: second\n\n"), interval);
            mockServer.start();

            HttpRequest request = HttpRequest.newBuilder(URI.create(PROXY_URL + "/events")).build();
            long start = System.nanoTime();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body()))) {
                assertEquals("data: first", reader.readLine());
                Duration firstChunkAfter = Duration.ofNanos(System.nanoTime() - start);
                assertTrue(firstChunkAfter.compareTo(interval) < 0,
                        "First chunk took " + firstChunkAfter.toMillis() + "ms; it should not wait for the second one");
                assertEquals("", reader.readLine());
                assertEquals("data: second", reader.readLine());
            }
        }

        @Test
        @DisplayName("Should forward large bodies intact while capturing only a prefix for the log")
        void shouldForwardLargeBodiesAndCaptureOnlyAPrefix() throws Exception {
            byte[] largeBody = new byte[5 * 1024 * 1024];
            new Random(42).nextBytes(largeBody);
            mockServer.addEndpoint("/download", "GET", 200, largeBody, Map.of("Content-Type", List.of("application/octet-stream")));
            mockServer.start();

            LogService limitedLog = mock(LogService.class);
            when(limitedLog.bodyCaptureLimit()).thenReturn(1024);
            try (ProxyServer server = new ProxyServer(ProxyConfig.of(0, TARGET_URL), limitedLog)) {
                server.start();
                HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + server.getPort() + "/download")).build();

                HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

                assertArrayEquals(largeBody, response.body());
                assertEquals(String.valueOf(largeBody.length), response.headers().firstValue("Content-Length").orElse(null));
                assertTrue(response.headers().firstValue("Transfer-Encoding").isEmpty());

                ArgumentCaptor<LogEntry> captor = ArgumentCaptor.forClass(LogEntry.class);
                verify(limitedLog, timeout(2000)).log(captor.capture());
                assertEquals(1024, captor.getValue().getResponseBody().length);
                assertEquals(largeBody.length, captor.getValue().getResponseBodySize());
            }
        }

        @Test
        @DisplayName("Should stream request bodies upstream keeping Content-Length")
        void shouldStreamRequestBodiesKeepingContentLength() throws Exception {
            byte[] upload = new byte[2 * 1024 * 1024];
            new Random(7).nextBytes(upload);
            startMockServer(201, "POST", "/upload", "stored");

            HttpRequest request = HttpRequest.newBuilder(URI.create(PROXY_URL + "/upload"))
                                             .POST(HttpRequest.BodyPublishers.ofByteArray(upload))
                                             .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(201, response.statusCode());
            assertArrayEquals(upload, mockServer.getLastRequestBody());
            assertEquals(String.valueOf(upload.length), mockServer.getLastRequestHeaders().getFirst("Content-Length"));
        }

        @Test
        @DisplayName("Should keep upstream Content-Length on HEAD responses")
        void shouldKeepContentLengthOnHeadResponses() throws Exception {
            mockServer.addEndpoint("/file", "HEAD", 200, new byte[0], Map.of("Content-Length", List.of("1234")));
            mockServer.start();

            HttpResponse<String> response = sendRequestToProxyServer("/file", "HEAD");

            assertEquals(200, response.statusCode());
            assertEquals("1234", response.headers().firstValue("Content-Length").orElse(null));
        }
    }

    @Nested
    @DisplayName("Bind Address Tests")
    class BindAddressTests {
        @Test
        @DisplayName("Should bind to loopback by default")
        void shouldBindToLoopbackByDefault() throws Exception {
            try (ProxyServer server = new ProxyServer(ProxyConfig.of(0, TARGET_URL), logService)) {
                server.start();
                assertTrue(server.getAddress().getAddress().isLoopbackAddress());
            }
        }

        @Test
        @DisplayName("Should bind to the configured host")
        void shouldBindToConfiguredHost() throws Exception {
            try (ProxyServer server = new ProxyServer(ProxyConfig.of(0, TARGET_URL).withHost("0.0.0.0"), logService)) {
                server.start();
                assertTrue(server.getAddress().getAddress().isAnyLocalAddress());
            }
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {
        @Test
        @DisplayName("Should return Bad Gateway when upstream server is unavailable")
        void shouldReturnBadGatewayWhenUpstreamServerIsUnavailable() throws Exception {
            HttpResponse<String> response = sendRequestToProxyServer("/unavailable", "GET");

            assertEquals(502, response.statusCode());
            assertEquals("Bad Gateway: Unable to connect to upstream server.", response.body());
        }

        @ParameterizedTest
        @ValueSource(ints = {400, 401, 403, 404, 500})
        @DisplayName("Should forward error status codes from upstream server")
        void shouldForwardErrorStatusCodesFromUpstreamServer(int errorStatusCode) throws Exception {
            String path = "/error";
            String errorMessage = "Error message";

            startMockServer(errorStatusCode, "GET", path, errorMessage);

            HttpResponse<String> response = sendRequestToProxyServer(path, "GET");

            assertEquals(errorStatusCode, response.statusCode());
            assertEquals(errorMessage, response.body());
        }
    }

    @Test
    void testProxyForwardsPostRequests() throws IOException, InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(logService).log(any());

        int statusCode = 201;
        String method = "POST";
        String path = "/api";
        String responseBody = "{}";

        startMockServer(statusCode, method, path, responseBody);

        HttpResponse<String> response = sendRequestToProxyServer(path, method);
        assertEquals(statusCode, response.statusCode());
        assertEquals(responseBody, response.body());

        boolean logHappened = latch.await(2, TimeUnit.SECONDS);
        assertTrue(logHappened);

        ArgumentCaptor<LogEntry> logEntryCaptor = ArgumentCaptor.forClass(LogEntry.class);
        verify(logService).log(logEntryCaptor.capture());

        LogEntry capturedEntry = logEntryCaptor.getValue();
        assertNotNull(capturedEntry);
        assertEquals(method, capturedEntry.getMethod());
        assertEquals(path, capturedEntry.getPath());
        assertEquals(statusCode, capturedEntry.getStatusCode());
        assertEquals(responseBody, new String(capturedEntry.getResponseBody()));
    }

    @Nested
    @DisplayName("Logging Tests")
    class LoggingTests {
        @Test
        @DisplayName("Should log requests and responses")
        void testLoggingRequestsAndResponses() throws IOException, InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            doAnswer(invocation -> {
                latch.countDown();
                return null;
            }).when(logService).log(any());

            int statusCode = 201;
            String method = "POST";
            String path = "/api";
            String responseBody = "{}";
            String requestBody = "{\"test\":\"data\"}";

            startMockServer(statusCode, method, path, responseBody);

            HttpRequest request = HttpRequest.newBuilder()
                                             .uri(URI.create(PROXY_URL + path))
                                             .header("Content-Type", "application/json")
                                             .method(method, HttpRequest.BodyPublishers.ofString(requestBody))
                                             .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(statusCode, response.statusCode());
            assertEquals(responseBody, response.body());

            boolean logHappened = latch.await(2, TimeUnit.SECONDS);
            assertTrue(logHappened, "Logging should happen within timeout");

            ArgumentCaptor<LogEntry> logEntryCaptor = ArgumentCaptor.forClass(LogEntry.class);
            verify(logService).log(logEntryCaptor.capture());

            LogEntry capturedEntry = logEntryCaptor.getValue();
            assertNotNull(capturedEntry);
            assertEquals(method, capturedEntry.getMethod());
            assertEquals(path, capturedEntry.getPath());
            assertEquals(statusCode, capturedEntry.getStatusCode());
            assertEquals(requestBody, new String(capturedEntry.getRequestBody()));
            assertEquals(responseBody, new String(capturedEntry.getResponseBody()));
        }

        @Test
        @DisplayName("Should log errors when upstream server is unavailable")
        void shouldLogErrorsWhenUpstreamServerIsUnavailable() throws IOException, InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            doAnswer(invocation -> {
                latch.countDown();
                return null;
            }).when(logService).log(any());

            sendRequestToProxyServer("/unavailable", "GET");

            boolean logHappened = latch.await(2, TimeUnit.SECONDS);
            assertTrue(logHappened, "Error logging should happen within timeout");

            ArgumentCaptor<LogEntry> logEntryCaptor = ArgumentCaptor.forClass(LogEntry.class);
            verify(logService).log(logEntryCaptor.capture());

            LogEntry capturedEntry = logEntryCaptor.getValue();
            assertEquals(502, capturedEntry.getStatusCode());
            assertEquals("GET", capturedEntry.getMethod());
            assertEquals("/unavailable", capturedEntry.getPath());
        }
    }

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {
        @Test
        @DisplayName("Should handle concurrent requests")
        void shouldHandleConcurrentRequests() throws Exception {
            int concurrentRequests = 10;
            CountDownLatch requestsCompleted = new CountDownLatch(concurrentRequests);

            startMockServer(200, "GET", "/concurrent", "ok");

            for (int i = 0; i < concurrentRequests; i++) {
                new Thread(() -> {
                    try {
                        HttpResponse<String> response = sendRequestToProxyServer("/concurrent", "GET");
                        assertEquals(200, response.statusCode());
                        assertEquals("ok", response.body());
                    } catch (Exception e) {
                        fail("Exception during concurrent request: " + e.getMessage());
                    } finally {
                        requestsCompleted.countDown();
                    }
                }).start();
            }

            boolean allRequestsCompleted = requestsCompleted.await(5, TimeUnit.SECONDS);
            assertTrue(allRequestsCompleted, "All concurrent requests should complete within timeout");
        }
    }

    @Test
    @DisplayName("Should close connection with AutoCloseable")
    void testAutoCloseableImplementation() throws Exception {
        try (ProxyServer autoCloseableServer = new ProxyServer(ProxyConfig.of(8889, TARGET_URL), logService)) {
            autoCloseableServer.start();
            assertTrue(autoCloseableServer.isRunning());
        }

        try (ProxyServer newServer = new ProxyServer(ProxyConfig.of(8889, TARGET_URL), logService)) {
            newServer.start();
            assertTrue(newServer.isRunning());
        }
    }
}
