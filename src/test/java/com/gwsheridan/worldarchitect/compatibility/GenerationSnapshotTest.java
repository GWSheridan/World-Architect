package com.gwsheridan.worldarchitect.compatibility;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Dynamic;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class GenerationSnapshotTest {
    @TempDir
    Path data;

    @Test
    void originalSnapshotCannotBeReplaced() throws Exception {
        var original = new CompoundTag();
        original.putInt("snapshot_schema", 2);
        original.putString("seed_marker", "original");
        GenerationSnapshot.initialize(data, original);
        byte[] bytes = Files.readAllBytes(GenerationSnapshot.snapshotPath(data));

        var changed = original.copy();
        changed.putString("seed_marker", "changed");
        assertThrows(IllegalStateException.class, () -> GenerationSnapshot.initialize(data, changed));

        assertArrayEquals(bytes, Files.readAllBytes(GenerationSnapshot.snapshotPath(data)));
        assertEquals(original, GenerationSnapshot.load(data).orElseThrow());
    }

    @Test
    void readsLegacySchemaAndRejectsUnknownOrMalformedSchema() {
        var snapshot = new CompoundTag();
        assertFalse(GenerationSnapshot.hasDependencyPins(snapshot));
        snapshot.putInt("snapshot_schema", 2);
        assertTrue(GenerationSnapshot.hasDependencyPins(snapshot));
        snapshot.putInt("snapshot_schema", 3);
        assertThrows(IllegalStateException.class, () -> GenerationSnapshot.hasDependencyPins(snapshot));
        snapshot.putString("snapshot_schema", "2");
        assertThrows(IllegalStateException.class, () -> GenerationSnapshot.hasDependencyPins(snapshot));
    }

    @Test
    void levelDataIdentifiesProtectedSaveAfterSnapshotLoss() throws Exception {
        var root = new CompoundTag();
        var metadata = new CompoundTag();
        root.put("Data", metadata);
        SaveProtection.markLevelData(root, data);
        assertFalse(SaveProtection.knowsProtectedSave(data, new Dynamic<>(NbtOps.INSTANCE, metadata)));

        GenerationSnapshot.initialize(data, new CompoundTag());
        SaveProtection.markLevelData(root, data);
        Files.delete(GenerationSnapshot.snapshotPath(data));

        assertTrue(SaveProtection.knowsProtectedSave(data, new Dynamic<>(NbtOps.INSTANCE, metadata)));
    }

    @Test
    void detectsChangedDimensionDefinitions() {
        var saved = new CompoundTag();
        saved.putInt("contract", 1);
        var types = new CompoundTag();
        types.putString("minecraft:the_nether", "old definition");
        saved.put("dimension_types", types);
        var current = saved.copy();
        current.getCompoundOrEmpty("dimension_types").putString("minecraft:the_nether", "new definition");

        var failure = assertThrows(IllegalStateException.class, () -> GenerationDependencies.verify(saved, current));
        assertTrue(failure.getMessage().contains("minecraft:the_nether"));
    }
}
