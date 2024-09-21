package com.renanwillian.easyproxy;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProxyServer  {

    private final int port;
    private final String targetUrl;

    public ProxyServer(int port, String targetUrl) {
        this.port = port;
        this.targetUrl = targetUrl;
    }

    public void start() throws IOException, InterruptedException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new ProxyHandler(targetUrl));

        ExecutorService executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);

        server.start();
        System.out.println("Proxy server is running on http://localhost:" + port + "/");
        System.out.println("Proxying to: " + targetUrl);

        Thread.currentThread().join();
    }
}