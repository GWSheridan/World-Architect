package com.gwsheridan.worldsunwritten.modpack;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PackTargetsTest {
    @Test
    void oneLoaderRoundTripsWithoutAddingOtherTargets() {
        var target = new PackTargets.Target("66.1.9", List.of("sample_provider"), List.of());
        var project = new PackTargets(Map.of("forge", target));
        var encoded = PackTargets.CODEC.encodeStart(JsonOps.INSTANCE, project).getOrThrow();
        var restored = PackTargets.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(Map.of("forge", target), restored.targets());
        assertThrows(IllegalArgumentException.class, () -> restored.with("forge", null));
        assertThrows(IllegalArgumentException.class, () -> new PackTargets.Target("66.1.9",
                List.of("sample_provider"), List.of("sample_provider")));
    }
}
