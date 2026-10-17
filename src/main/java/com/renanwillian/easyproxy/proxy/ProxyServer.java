package com.renanwillian.easyproxy.proxy;

import com.renanwillian.easyproxy.log.LogService;
import com.renanwillian.easyproxy.utils.UrlUtils;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ProxyServer implements AutoCloseable {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private final ProxyConfig config;
    private final String targetUrl;
    private final LogService logService;
    private HttpServer server;
    private ExecutorService executor;
    private HttpClient httpClient;
    private volatile boolean isRunning = false;

    public ProxyServer(ProxyConfig config, LogService logService) {
        this.config = config;
        this.targetUrl = UrlUtils.sanitizeUrl(config.targetUrl());
        this.logService = logService;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(config.host(), config.port()), 0);
        HttpClient.Builder clientBuilder = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT);
        if (config.insecure()) clientBuilder.sslContext(InsecureTls.sslContext());
        httpClient = clientBuilder.build();
        server.createContext("/", new ProxyHandler(targetUrl, httpClient, config.timeout(), logService));

        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);

        server.start();
        isRunning = true;
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            isRunning = false;
        }

        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (httpClient != null) {
            httpClient.close();
        }
    }

    /** The address the server is bound to; the port differs from the configured one when it was 0. */
    public InetSocketAddress getAddress() {
        return server.getAddress();
    }

    public int getPort() {
        return getAddress().getPort();
    }

    public boolean isRunning() {
        return isRunning;
    }

    @Override
    public void close() {
        stop();
    }
}
