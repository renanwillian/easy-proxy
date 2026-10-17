package com.renanwillian.easyproxy.utils;

import java.io.Console;
import java.util.Map;

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

    private static volatile boolean enabled = shouldUseColors(System.getenv(), isTerminal());

    private AnsiUtils() {}

    public static String colorize(String text, String color) {
        return enabled ? color + text + RESET : text;
    }

    public static void setEnabled(boolean enabled) {
        AnsiUtils.enabled = enabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Colors are used on terminals only, following the NO_COLOR (https://no-color.org) and
     * FORCE_COLOR / CLICOLOR_FORCE conventions.
     */
    static boolean shouldUseColors(Map<String, String> env, boolean terminal) {
        if (isSet(env.get("NO_COLOR"))) return false;
        if (isSet(env.get("FORCE_COLOR")) || (isSet(env.get("CLICOLOR_FORCE")) && !"0".equals(env.get("CLICOLOR_FORCE")))) {
            return true;
        }
        if ("dumb".equals(env.get("TERM"))) return false;
        return terminal;
    }

    private static boolean isSet(String value) {
        return value != null && !value.isEmpty();
    }

    private static boolean isTerminal() {
        Console console = System.console();
        return console != null && console.isTerminal();
    }
}
