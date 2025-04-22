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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.only;
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
        mockServer = new MockServer(MOCK_SERVER_PORT);
        proxyServer = new ProxyServer(PROXY_SERVER_PORT, TARGET_URL, logService);
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
        verify(logService, only()).log(logEntryCaptor.capture());

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
            verify(logService, only()).log(logEntryCaptor.capture());

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
        try (ProxyServer autoCloseableServer = new ProxyServer(8889, TARGET_URL, logService)) {
            autoCloseableServer.start();
            assertTrue(autoCloseableServer.isRunning());
        }

        try (ProxyServer newServer = new ProxyServer(8889, TARGET_URL, logService)) {
            newServer.start();
            assertTrue(newServer.isRunning());
        }
    }
}
