package com.renanwillian;

import com.renanwillian.easyproxy.ProxyHandler;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;

public class Main {

    public static void main(String[] args) throws Exception {
        int port = 8000;
        String target = args[0];

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new ProxyHandler(target));
        server.setExecutor(null);
        server.start();

        System.out.println("Proxy server is running on http://localhost:" + port + "/");
    }
}