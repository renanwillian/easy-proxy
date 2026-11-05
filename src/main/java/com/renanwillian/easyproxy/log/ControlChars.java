package com.renanwillian.easyproxy.log;

/**
 * Makes untrusted text safe to print in a terminal. Control characters (such as ESC, which starts ANSI escape
 * sequences) are replaced with a visible escape like {@code \x1b}, so that a body or header cannot clear the
 * screen, rewrite earlier log lines, or send commands to the terminal.
 */
final class ControlChars {

    private ControlChars() {}

    /**
     * Escapes C0 controls, DEL and C1 controls. Newlines, tabs and CRLF line endings are kept so that formatted
     * text stays readable; a lone carriage return is escaped because it lets text overwrite the current line.
     */
    static String escape(String text) {
        if (text == null) return null;

        StringBuilder sb = null;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean keep = !isControl(c) || c == '\n' || c == '\t'
                    || (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n');
            if (keep) {
                if (sb != null) sb.append(c);
                continue;
            }
            if (sb == null) sb = new StringBuilder(text.length() + 16).append(text, 0, i);
            sb.append(String.format("\\x%02x", (int) c));
        }
        return sb == null ? text : sb.toString();
    }

    private static boolean isControl(char c) {
        return c < 0x20 || (c >= 0x7f && c <= 0x9f);
    }
}
