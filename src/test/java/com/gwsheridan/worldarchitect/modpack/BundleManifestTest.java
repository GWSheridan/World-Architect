package com.gwsheridan.worldarchitect.modpack;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gwsheridan.worldarchitect.modpack.BundleManifest.Feature;
import com.gwsheridan.worldarchitect.modpack.BundleManifest.Option;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.WorldOptions;
import org.junit.jupiter.api.Test;

final class BundleManifestTest {
    private static final Identifier OVERWORLD = Identifier.parse("minecraft:overworld");
    private static final Identifier NETHER = Identifier.parse("minecraft:the_nether");
    private static final Identifier END = Identifier.parse("minecraft:the_end");

    @Test
    void partialLockGrantsOnlyExplicitEdits() {
        var json = manifest("partial");
        json.add("editable_dimensions", JsonParser.parseString("[\"minecraft:the_end\"]"));
        json.add("editable_features", JsonParser.parseString("{\"minecraft:the_nether\":[\"ores\"]}"));
        json.add("editable_options", JsonParser.parseString("[\"seed\"]"));
        var policy = decode(json);

        assertTrue(policy.canEditDimension(END));
        assertFalse(policy.canEditDimension(NETHER));
        assertFalse(policy.canEditDimension(OVERWORLD));
        assertTrue(policy.canEditFeature(NETHER, Feature.ORES));
        assertFalse(policy.canEditFeature(NETHER, Feature.FLUIDS));
        assertFalse(policy.canEditFeature(OVERWORLD, Feature.ORES));
        assertTrue(policy.canEditOption(Option.SEED));
        assertFalse(policy.canEditOption(Option.BONUS_CHEST));
    }

    @Test
    void fullAndUnlockedModesSetOppositeEditPolicies() {
        var full = decode(manifest("full"));
        var unlocked = decode(manifest("unlocked"));

        assertFalse(full.canEditDimension(OVERWORLD));
        assertFalse(full.canEditFeature(OVERWORLD, Feature.ORES));
        assertFalse(full.canEditOption(Option.SEED));
        assertTrue(unlocked.canEditDimension(OVERWORLD));
        assertTrue(unlocked.canEditFeature(OVERWORLD, Feature.ORES));
        assertTrue(unlocked.canEditOption(Option.SEED));
    }

    @Test
    void contradictoryAndUnknownPermissionsFailValidation() {
        var full = manifest("full");
        full.add("editable_options", JsonParser.parseString("[\"seed\"]"));
        assertTrue(BundleManifest.CODEC.parse(JsonOps.INSTANCE, full).error().isPresent());

        var partial = manifest("partial");
        assertTrue(BundleManifest.CODEC.parse(JsonOps.INSTANCE, partial).error().isPresent());
        partial.add("editable_dimensions", JsonParser.parseString("[\"minecraft:the_end\"]"));
        partial.add("editable_features", JsonParser.parseString("{\"minecraft:the_end\":[\"ores\"]}"));
        assertTrue(BundleManifest.CODEC.parse(JsonOps.INSTANCE, partial).error().isPresent());
        partial.remove("editable_dimensions");
        partial.add("editable_features", JsonParser.parseString("{\"minecraft:the_end\":[\"unknown_feature\"]}"));
        assertTrue(BundleManifest.CODEC.parse(JsonOps.INSTANCE, partial).error().isPresent());
    }

    private static BundleManifest decode(JsonObject json) {
        return BundleManifest.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    @Test
    void structureOptionPermissionAllowsOnlyStructureChanges() {
        var json = manifest("partial");
        json.add("editable_options", JsonParser.parseString("[\"generate_structures\"]"));
        var policy = decode(json);
        var baseline = policy.options();
        assertDoesNotThrow(() -> ModpackRuntime.compareOptions(policy,
                new WorldOptions(baseline.seed(), !baseline.generateStructures(), baseline.generateBonusChest())));
        assertThrows(IllegalStateException.class, () -> ModpackRuntime.compareOptions(policy,
                new WorldOptions(baseline.seed() + 1, baseline.generateStructures(), baseline.generateBonusChest())));
        assertThrows(IllegalStateException.class, () -> ModpackRuntime.compareOptions(policy,
                new WorldOptions(baseline.seed(), baseline.generateStructures(), !baseline.generateBonusChest())));
    }

    @Test
    void fullLockRejectsStructureOptionChanges() {
        var policy = decode(manifest("full"));
        var baseline = policy.options();
        assertThrows(IllegalStateException.class, () -> ModpackRuntime.compareOptions(policy,
                new WorldOptions(baseline.seed(), !baseline.generateStructures(), baseline.generateBonusChest())));
    }

    @Test
    void allFeatureEditorsCanBeUnlockedWithoutUnlockingTheDimension() {
        var json = manifest("partial");
        json.add("editable_features", JsonParser.parseString("""
                {"minecraft:the_end":["biomes","ores","decorations","caves","fluids","structures","native_features"]}
                """));
        var policy = decode(json);
        for (Feature feature : Feature.values()) assertTrue(policy.canEditFeature(END, feature));
        assertFalse(policy.canEditDimension(END));
    }

    private static JsonObject manifest(String mode) {
        var json = JsonParser.parseString("""
                {
                  "format": 1,
                  "preset": "example:skyblock",
                  "title": "Skyblock",
                  "description": "A starter island",
                  "author": "Example",
                  "release": "1.0.0",
                  "options": {"seed": 1234}
                }
                """).getAsJsonObject();
        json.addProperty("lock", mode);
        return json;
    }
}
