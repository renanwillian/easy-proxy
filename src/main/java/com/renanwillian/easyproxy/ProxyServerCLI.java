package com.renanwillian.easyproxy;

import com.sun.net.httpserver.HttpServer;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.net.InetSocketAddress;

@Command(name = "proxy-server", mixinStandardHelpOptions = true, version = "1.0",
        description = "Starts a reverse proxy server.")
public class ProxyServer implements Runnable {

    @Option(names = {"--port"}, description = "The port on which the server will run (default: 8000).")
    private int port = 8000;

    @Parameters(paramLabel = "TARGET_URL", description = "The target URL for the proxy.")
    private String targetUrl;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new ProxyServer()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        try {
            // Start the server with the specified port and target URL
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", new ProxyHandler(targetUrl));
            server.setExecutor(null);
            server.start();

            System.out.println("Proxy server is running on http://localhost:" + port + "/");
            System.out.println("Proxying to: " + targetUrl);

            // Wait for the server to stop
            Thread.currentThread().join();
        } catch (Exception e) {
            System.err.println("Error starting the server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}