package com.renanwillian.easyproxy;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "proxy-server", mixinStandardHelpOptions = true, version = "1.0",
        description = "Starts a reverse proxy server.")
public class ProxyServerCLI implements Runnable {

    @Option(names = {"--port"}, description = "The port on which the server will run (default: 8000).")
    private int port = 8000;

    @Parameters(paramLabel = "TARGET_URL", description = "The target URL for the proxy.")
    private String targetUrl;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new ProxyServerCLI()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        try {
            ProxyServer server = new ProxyServer(port, targetUrl);
            server.start();
        } catch (Exception e) {
            System.err.println("Error starting the server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}