package com.renanwillian.easyproxy.utils;

public class AnsiUtils {

    // Reset color
    public static final String RESET = "\033[0m";

    // Regular colors
    public static final String CYAN = "\033[0;36m";
    public static final String WHITE = "\033[0;37m";

    // Bold colors
    public static final String WHITE_BOLD = "\033[1;37m";
    public static final String GREEN_BOLD = "\033[1;32m";
    public static final String RED_BOLD = "\033[1;31m";
    public static final String PURPLE_BOLD = "\033[1;35m";

    private AnsiUtils() {}

    public static String colorize(String text, String color) {
        return color + text + RESET;
    }
}
