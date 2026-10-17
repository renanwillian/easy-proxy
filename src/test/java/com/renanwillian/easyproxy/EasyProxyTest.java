package com.renanwillian.easyproxy;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EasyProxyTest {

    @Test
    void shouldReturnErrorExitCodeWhenPortIsInUse() throws Exception {
        try (ServerSocket occupied = new ServerSocket(0)) {
            int exitCode = new CommandLine(new EasyProxy())
                    .execute("--port", String.valueOf(occupied.getLocalPort()), "http://localhost:1");

            assertEquals(EasyProxy.EXIT_ERROR, exitCode);
        }
    }
}
