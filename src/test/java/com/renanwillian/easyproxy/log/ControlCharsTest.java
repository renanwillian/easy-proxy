package com.renanwillian.easyproxy.log;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ControlCharsTest {

    @Test
    void shouldEscapeAnsiEscapeSequences() {
        assertEquals("\\x1b[2J\\x1b]0;title\\x07", ControlChars.escape("\u001b[2J\u001b]0;title\u0007"));
    }

    @Test
    void shouldEscapeLoneCarriageReturnAndBackspace() {
        assertEquals("real\\x0dfake\\x08", ControlChars.escape("real\rfake\b"));
    }

    @Test
    void shouldEscapeDelAndC1Controls() {
        // U+009B is the single-character form of "ESC [", understood by some terminals.
        assertEquals("\\x7f\\x9b2J", ControlChars.escape("\u007f\u009b2J"));
    }

    @Test
    void shouldKeepNewlinesTabsAndCrlf() {
        String text = "{\r\n\t\"a\": 1\n}";
        assertEquals(text, ControlChars.escape(text));
    }

    @Test
    void shouldKeepPrintableUnicode() {
        String text = "olá, 世界 ✓";
        assertSame(text, ControlChars.escape(text));
    }

    @Test
    void shouldReturnNullForNull() {
        assertNull(ControlChars.escape(null));
    }
}
