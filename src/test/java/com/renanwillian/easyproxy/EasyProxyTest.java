package com.renanwillian.easyproxy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import picocli.CommandLine;

import java.net.InetAddress;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EasyProxyTest {

    @Test
    @Timeout(10)
    void shouldReturnErrorExitCodeWhenPortIsInUse() throws Exception {
        try (ServerSocket occupied = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            int exitCode = new CommandLine(new EasyProxy())
                    .execute("--port", String.valueOf(occupied.getLocalPort()), "http://localhost:1");

            assertEquals(EasyProxy.EXIT_ERROR, exitCode);
        }
    }
}
