package com.renanwillian.easyproxy.proxy;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProxyHandlerTest {

    @Test
    void shouldDescribeExceptionAndRootCause() {
        Exception e = new IOException("wrapper", new RuntimeException("middle", new ConnectException("Connection refused")));

        assertEquals("IOException: wrapper (caused by ConnectException: Connection refused)", ProxyHandler.describe(e));
    }

    @Test
    void shouldDescribeExceptionsWithoutCauseOrMessage() {
        assertEquals("IllegalStateException", ProxyHandler.describe(new IllegalStateException()));
    }
}
