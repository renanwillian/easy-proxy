package com.renanwillian.easyproxy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionProviderTest {

    @Test
    void shouldReadTheFilteredProjectVersion() throws Exception {
        String version = new VersionProvider().getVersion()[0];

        assertFalse(version.contains("${"), "Version should be filtered by Maven: " + version);
        assertTrue(version.matches("\\d+\\.\\d+\\.\\d+.*"), version);
    }

    @Test
    void shouldReturnUnknownWhenResourceIsMissing() throws Exception {
        assertEquals(VersionProvider.UNKNOWN, VersionProvider.readVersion("/missing.properties"));
    }
}
