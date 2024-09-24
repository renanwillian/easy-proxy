package com.renanwillian.easyproxy.proxy;

import com.renanwillian.easyproxy.log.LogService;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProxyServer  {

    private final int port;
    private final String targetUrl;
    private final LogService logService;

    public ProxyServer(int port, String targetUrl, LogService logService) {
        this.port = port;
        this.targetUrl = targetUrl;
        this.logService = logService;
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new ProxyHandler(targetUrl, logService));

        ExecutorService executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);

        server.start();
    }
}