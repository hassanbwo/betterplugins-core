package de.betterplugins.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UpdateMonitorTest {

    @Test
    void comparesNumerically() {
        assertTrue(UpdateMonitor.compareVersions("4.10", "4.9") > 0);
        assertTrue(UpdateMonitor.compareVersions("1.2.0", "1.10.0") < 0);
        assertEquals(0, UpdateMonitor.compareVersions("v.4.2", "4.2"));
        assertEquals(0, UpdateMonitor.compareVersions("2.0", "2.0.0"));
        assertTrue(UpdateMonitor.compareVersions("2.0.1", "2.0") > 0);
    }

    @Test
    void ignoresGarbage() {
        assertEquals(0, UpdateMonitor.compareVersions("", ""));
        assertTrue(UpdateMonitor.compareVersions("1.0-beta", "0.9") > 0);
    }

    @Test
    void majorVersion() {
        assertEquals(4, UpdateMonitor.majorOf("4.9.0"));
        assertEquals(2, UpdateMonitor.majorOf("v2.0"));
    }
}
