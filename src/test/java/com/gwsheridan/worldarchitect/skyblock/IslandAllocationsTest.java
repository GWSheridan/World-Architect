package com.gwsheridan.worldarchitect.skyblock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class IslandAllocationsTest {
    @TempDir Path data;

    @Test
    void separateClaimsSurviveReopenAndCapacity() {
        UUID alpha = UUID.randomUUID();
        UUID beta = UUID.randomUUID();
        IslandAllocations.prepare(data, 2, true);
        assertEquals(1, IslandAllocations.claim(data, 2, alpha));
        assertEquals(2, IslandAllocations.claim(data, 2, beta));

        IslandAllocations.prepare(data, 2, false);
        assertThrows(SkyblockJoinException.class, () -> IslandAllocations.claim(data, 2, UUID.randomUUID()));
        assertEquals(2, IslandAllocations.claim(data, 2, beta));
        assertEquals(1, IslandAllocations.assignedSlot(data, 2, alpha));
    }

    @Test
    void missingOrDuplicateClaimsFailClosed() throws Exception {
        assertThrows(UncheckedIOException.class, () -> IslandAllocations.prepare(data, 2, false));
        Path file = data.resolve("worldarchitect-islands-v1.dat");
        assertFalse(Files.exists(file));

        var players = new CompoundTag();
        players.putInt(UUID.randomUUID().toString(), 1);
        players.putInt(UUID.randomUUID().toString(), 1);
        var root = new CompoundTag();
        root.putInt("version", 1);
        root.put("players", players);
        NbtIo.writeCompressed(root, file);
        assertThrows(IllegalStateException.class, () -> IslandAllocations.prepare(data, 2, false));
    }
}
