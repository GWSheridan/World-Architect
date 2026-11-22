package com.gwsheridan.worldsunwritten.spawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

class SpawnPolicyTest {
    @Test
    void persistentMarkerPolicyRetainsItsDimensionAndOrientation() {
        var json = JsonParser.parseString("""
                {"kind":"marker","dimension":"example:city","position":[-48,90,32],
                 "template":"builds:tower","marker":"entry","rotation":"clockwise_90","mirror":"left_right"}
                """);
        var policy = SpawnPolicy.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(-48, policy.position().getX());
        assertEquals("example:city", policy.dimension().toString());
        assertEquals(policy, SpawnPolicy.CODEC.parse(JsonOps.INSTANCE,
                SpawnPolicy.CODEC.encodeStart(JsonOps.INSTANCE, policy).getOrThrow()).getOrThrow());
    }

    @Test
    void invalidBoundsAndIncompleteMarkersAreRejected() {
        for (String json : new String[] {
                "{\"kind\":\"fixed\",\"dimension\":\"minecraft:overworld\",\"position\":[30000000,80,0]}",
                "{\"kind\":\"generated\",\"dimension\":\"minecraft:overworld\",\"position\":[0,80,0],\"marker\":\"entry\"}"
        }) {
            assertFalse(SpawnPolicy.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).isSuccess());
        }
    }
}
