package com.renanwillian.easyproxy.proxy;

import com.renanwillian.easyproxy.log.LogService;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InsecureTlsTest {

    private static final char[] PASSWORD = "changeit".toCharArray();

    @TempDir
    Path tempDir;

    private HttpsServer httpsServer;
    private final HttpClient client = HttpClient.newHttpClient();
    private final LogService logService = mock(LogService.class);

    @BeforeEach
    void startSelfSignedServer() throws Exception {
        when(logService.bodyCaptureLimit()).thenReturn(0);

        Path keystore = tempDir.resolve("keystore.p12");
        Process keytool = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                "-genkeypair", "-alias", "test", "-keyalg", "RSA", "-keysize", "2048", "-validity", "1",
                "-dname", "CN=self-signed.invalid", "-storetype", "PKCS12",
                "-keystore", keystore.toString(), "-storepass", new String(PASSWORD))
                .redirectErrorStream(true)
                .start();
        assertTrue(keytool.waitFor(30, TimeUnit.SECONDS), "keytool timed out");
        assertEquals(0, keytool.exitValue(), new String(keytool.getInputStream().readAllBytes()));

        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(keystore)) {
            ks.load(in, PASSWORD);
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, PASSWORD);
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);

        httpsServer = HttpsServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext));
        httpsServer.createContext("/", exchange -> {
            byte[] body = "secure ok".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        httpsServer.start();
    }

    @AfterEach
    void stopServer() {
        httpsServer.stop(0);
    }

    private HttpResponse<String> requestThroughProxy(boolean insecure) throws Exception {
        String target = "https://localhost:" + httpsServer.getAddress().getPort();
        try (ProxyServer proxy = new ProxyServer(ProxyConfig.of(0, target).withInsecure(insecure), logService)) {
            proxy.start();
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + proxy.getPort() + "/")).build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test
    void shouldRejectSelfSignedCertificateByDefault() throws Exception {
        HttpResponse<String> response = requestThroughProxy(false);

        assertNotEquals(200, response.statusCode());
    }

    @Test
    void shouldAcceptSelfSignedCertificateWhenInsecure() throws Exception {
        HttpResponse<String> response = requestThroughProxy(true);

        assertEquals(200, response.statusCode());
        assertEquals("secure ok", response.body());
    }
}
