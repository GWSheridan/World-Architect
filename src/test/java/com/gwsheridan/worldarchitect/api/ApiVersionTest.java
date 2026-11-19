package com.gwsheridan.worldarchitect.api;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ApiVersionTest {
    @Test void explicitVersionNegotiationRejectsOtherContracts() {
        assertEquals(1, WorldArchitectApi.VERSION);
        assertDoesNotThrow(() -> WorldArchitectApi.requireVersion(1));
        for (int version : new int[] {0, 2, Integer.MAX_VALUE}) {
            assertThrows(IllegalStateException.class, () -> WorldArchitectApi.requireVersion(version));
        }
    }
}
