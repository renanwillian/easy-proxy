package com.renanwillian.easyproxy.utils;

@SuppressWarnings("java:S106")
public class TerminalUtils {

    private TerminalUtils() {}

    public static void println() {
        System.out.println();
    }

    public static void println(String message) {
        System.out.println(message);
    }

    public static void printlnError(String message) {
        System.err.println(message);
    }
}
