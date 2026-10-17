package com.renanwillian.easyproxy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    @ParameterizedTest
    @CsvSource({
            "--port=0,        http://localhost:1",
            "--port=70000,    http://localhost:1",
            "--timeout=0,     http://localhost:1",
            "--timeout=-5,    http://localhost:1",
            "--port=8000,     ftp://example.com",
            "--port=8000,     https://example.com?x=1",
    })
    @Timeout(10)
    void shouldRejectInvalidArgumentsWithUsageExitCode(String option, String url) {
        int exitCode = new CommandLine(new EasyProxy()).execute(option, url);

        assertEquals(CommandLine.ExitCode.USAGE, exitCode);
    }
}
