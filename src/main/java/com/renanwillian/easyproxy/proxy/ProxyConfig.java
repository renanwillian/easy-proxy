package com.renanwillian.easyproxy.proxy;

/**
 * Settings for a {@link ProxyServer}.
 *
 * @param host      the address the server binds to (e.g. 127.0.0.1 or 0.0.0.0).
 * @param port      the port the server listens on; 0 picks a free port.
 * @param targetUrl the upstream base URL requests are forwarded to.
 */
public record ProxyConfig(String host, int port, String targetUrl) {

    public static final String DEFAULT_HOST = "127.0.0.1";

    public static ProxyConfig of(int port, String targetUrl) {
        return new ProxyConfig(DEFAULT_HOST, port, targetUrl);
    }

    public ProxyConfig withHost(String host) {
        return new ProxyConfig(host, port, targetUrl);
    }
}
