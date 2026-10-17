package com.renanwillian.easyproxy.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnsiUtilsTest {

    private final boolean initial = AnsiUtils.isEnabled();

    @AfterEach
    void restore() {
        AnsiUtils.setEnabled(initial);
    }

    @Test
    void shouldUseColorsOnTerminalsOnly() {
        assertTrue(AnsiUtils.shouldUseColors(Map.of(), true));
        assertFalse(AnsiUtils.shouldUseColors(Map.of(), false));
    }

    @Test
    void shouldRespectNoColor() {
        assertFalse(AnsiUtils.shouldUseColors(Map.of("NO_COLOR", "1"), true));
        assertTrue(AnsiUtils.shouldUseColors(Map.of("NO_COLOR", ""), true), "An empty NO_COLOR is ignored");
    }

    @Test
    void shouldRespectForceColor() {
        assertTrue(AnsiUtils.shouldUseColors(Map.of("FORCE_COLOR", "1"), false));
        assertTrue(AnsiUtils.shouldUseColors(Map.of("CLICOLOR_FORCE", "1"), false));
        assertFalse(AnsiUtils.shouldUseColors(Map.of("CLICOLOR_FORCE", "0"), false));
        assertFalse(AnsiUtils.shouldUseColors(Map.of("NO_COLOR", "1", "FORCE_COLOR", "1"), true), "NO_COLOR wins");
    }

    @Test
    void shouldNotUseColorsOnDumbTerminals() {
        assertFalse(AnsiUtils.shouldUseColors(Map.of("TERM", "dumb"), true));
    }

    @Test
    void shouldReturnPlainTextWhenDisabled() {
        AnsiUtils.setEnabled(false);
        assertEquals("text", AnsiUtils.colorize("text", AnsiUtils.RED_BOLD));

        AnsiUtils.setEnabled(true);
        assertEquals(AnsiUtils.RED_BOLD + "text" + AnsiUtils.RESET, AnsiUtils.colorize("text", AnsiUtils.RED_BOLD));
    }
}
