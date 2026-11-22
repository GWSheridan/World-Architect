package com.gwsheridan.worldsunwritten.spawn;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.serialization.Dynamic;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class WorldAdministrationTest {
    @TempDir Path data;

    @Test
    void requiredAdministrationIsCheckedBeforeTheLevelDataIsRewritten() throws Exception {
        var level = new CompoundTag();
        assertDoesNotThrow(() -> WorldAdministration.verify(data, new Dynamic<>(NbtOps.INSTANCE, level)));
        Path settings = data.resolve("worldsunwritten-administration-v1.dat");
        assertFalse(Files.exists(settings));
        level.putBoolean("worldsunwritten_administration", true);
        var error = assertThrows(IllegalStateException.class,
                () -> WorldAdministration.verify(data, new Dynamic<>(NbtOps.INSTANCE, level)));
        assertTrue(error.getMessage().contains("administration file is missing"));
        assertFalse(Files.exists(settings));
        Files.writeString(settings, "Preserve this file for the settings loader");
        assertDoesNotThrow(() -> WorldAdministration.verify(data, new Dynamic<>(NbtOps.INSTANCE, level)));
        assertEquals("Preserve this file for the settings loader", Files.readString(settings));
    }
}
