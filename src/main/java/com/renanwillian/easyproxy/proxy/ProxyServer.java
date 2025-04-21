package com.renanwillian.easyproxy.proxy;

import com.renanwillian.easyproxy.log.LogService;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ProxyServer implements AutoCloseable {

    private final int port;
    private final String targetUrl;
    private final LogService logService;
    private HttpServer server;
    private ExecutorService executor;
    private boolean isRunning = false;

    public ProxyServer(int port, String targetUrl, LogService logService) {
        this.port = port;
        this.targetUrl = targetUrl;
        this.logService = logService;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new ProxyHandler(targetUrl, logService));

        executor = Executors.newCachedThreadPool();
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
    }

    public boolean isRunning() {
        return isRunning;
    }

    @Override
    public void close() {
        stop();
    }
}