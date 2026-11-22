package com.gwsheridan.worldsunwritten.environment;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.gwsheridan.worldsunwritten.spawn.SpawnPolicy;
import com.gwsheridan.worldsunwritten.spawn.WorldAdministration;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.MobCategory;
import org.junit.jupiter.api.Test;

final class EnvironmentProfileTest {
    @Test void missingFieldsPreserveVanillaBehaviourAndFullOverridesRoundTrip() {
        assertEquals(EnvironmentProfile.DEFAULT, parse("{}"));
        var profile = parse("""
                {"time":{"mode":"fixed","ticks":18000},"weather":"thunder","monsters":false,
                 "creatures":true,"spawners":false,"portal":"operators",
                 "visual":{"fog_color":3368703,"fog":{"start":4,"end":80},"sky_color":16711680,
                           "clouds":"shown","cloud_color":65280,"cloud_height":128}}
                """);
        assertEquals(profile, EnvironmentProfile.CODEC.parse(JsonOps.INSTANCE, EnvironmentProfile.CODEC.encodeStart(JsonOps.INSTANCE, profile).getOrThrow()).getOrThrow());
    }

    @Test void clockViewsKeepTheOriginalTickSequenceAvailable() {
        var fixed = new EnvironmentProfile.Time(EnvironmentProfile.TimeMode.FIXED, 18000);
        var offset = new EnvironmentProfile.Time(EnvironmentProfile.TimeMode.OFFSET, 6000);
        for (long original : new long[]{0, 23999, 24000, 1000000}) {
            assertEquals(18000, fixed.apply(original));
            assertEquals(original + 6000, offset.apply(original));
            assertEquals(original, EnvironmentProfile.Time.DEFAULT.apply(original));
        }
    }

    @Test void invalidValuesRejectTheEntireProfile() {
        for (String json : new String[]{"{\"time\":{\"mode\":\"fixed\",\"ticks\":24000}}", "{\"weather\":\"snowstorm\"}",
                "{\"visual\":{\"fog_color\":16777216}}", "{\"visual\":{\"fog\":{\"start\":80,\"end\":40}}}",
                "{\"visual\":{\"cloud_height\":5000}}", "{\"portal\":\"unrestricted_admin\"}"}) {
            assertFalse(EnvironmentProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).isSuccess(), json);
        }
    }

    @Test void naturalAndSpawnerRestrictionsDoNotRemoveExistingOrAdministrativeMobs() {
        var profile = parse("{\"monsters\":false,\"creatures\":true,\"spawners\":false}");
        assertFalse(profile.allowsSpawn(MobCategory.MONSTER, EntitySpawnReason.NATURAL));
        assertFalse(profile.allowsSpawn(MobCategory.MONSTER, EntitySpawnReason.CHUNK_GENERATION));
        assertTrue(profile.allowsSpawn(MobCategory.CREATURE, EntitySpawnReason.NATURAL));
        assertFalse(profile.allowsSpawn(MobCategory.CREATURE, EntitySpawnReason.SPAWNER));
        assertFalse(profile.allowsSpawn(MobCategory.MONSTER, EntitySpawnReason.TRIAL_SPAWNER));
        for (var reason : new EntitySpawnReason[]{EntitySpawnReason.LOAD, EntitySpawnReason.COMMAND, EntitySpawnReason.SPAWN_ITEM_USE, EntitySpawnReason.BREEDING, EntitySpawnReason.DIMENSION_TRAVEL}) {
            assertTrue(profile.allowsSpawn(MobCategory.MONSTER, reason));
        }
    }

    @Test void environmentEditsPreserveSpawnAndProtectionSettings() {
        var dimension = Identifier.withDefaultNamespace("overworld");
        var spawn = SpawnPolicy.simple(SpawnPolicy.Kind.FIXED, dimension, new BlockPos(4, 82, 4));
        var profile = parse("{\"weather\":\"rain\"}");
        var settings = WorldAdministration.Settings.DEFAULT.withPolicy("initial", Optional.of(spawn), dimension)
                .withEnvironment(dimension, profile).withProtection(WorldAdministration.Protection.OFF)
                .withPolicy("respawn", Optional.of(spawn), dimension);
        assertEquals(profile, settings.environments().get(dimension));
        settings = settings.withEnvironment(dimension, EnvironmentProfile.DEFAULT);
        assertTrue(settings.environments().isEmpty());
        assertEquals(spawn, settings.initial().orElseThrow());
        assertEquals(spawn, settings.respawn().orElseThrow());
    }

    private static EnvironmentProfile parse(String json) { return EnvironmentProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow(); }
}
