package com.renanwillian.easyproxy;

import com.renanwillian.easyproxy.log.LogService;
import com.renanwillian.easyproxy.proxy.ProxyServer;
import com.renanwillian.easyproxy.utils.TerminalUtils;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "easy-proxy", mixinStandardHelpOptions = true, version = "1.0",
        description = "Starts a reverse proxy server.")
public class EasyProxy implements Runnable {

    @Option(names = {"--port"}, description = "The port on which the server will run (default: 8000).")
    private int port = 8000;

    @Option(names = {"--details"}, description = "Show the details of each request / response (default: false).")
    private boolean details = false;

    @Option(names = {"--headers"}, description = "Show the headers of each request / response (default: false).")
    private boolean headers = false;

    @Parameters(paramLabel = "TARGET_URL", description = "The target URL for the proxy.")
    private String targetUrl;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new EasyProxy()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        try {
            LogService logService = new LogService(headers, details);

            ProxyServer server = new ProxyServer(port, targetUrl, logService);
            server.start();

            TerminalUtils.println("Proxy server running on http://localhost:" + port + " and redirecting to " + targetUrl);
            TerminalUtils.println();

            Thread.currentThread().join();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            TerminalUtils.printlnError("Server start interrupted: " + ie.getMessage());
        } catch (Exception e) {
            TerminalUtils.printlnError("Error starting the server: " + e.getMessage());
        }
    }
}