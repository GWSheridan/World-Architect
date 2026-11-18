package com.gwsheridan.worldarchitect.skyblock;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

/** Accessed on the server thread. Commit the claim before putting a player on its island. */
public final class IslandAllocations {
    private static final String FILE = "worldarchitect-islands-v1.dat";

    private IslandAllocations() {}

    public static boolean exists(Path dataDirectory) {
        return Files.exists(dataDirectory.resolve(FILE));
    }

    public static void prepare(Path dataDirectory, int capacity, boolean newWorld) {
        Path file = dataDirectory.resolve(FILE);
        if (newWorld && Files.notExists(file)) {
            store(file, new CompoundTag());
        } else {
            read(file, capacity);
        }
    }

    public static int claim(Path dataDirectory, int capacity, UUID player) {
        Path file = dataDirectory.resolve(FILE);
        Claims claims = read(file, capacity);
        Integer existing = claims.players.get(player);
        if (existing != null) {
            return existing;
        }
        int slot = claims.occupied.nextClearBit(1);
        if (slot > capacity) {
            throw new SkyblockJoinException("All " + capacity + " personal Skyblock islands are assigned.");
        }
        claims.tag.putInt(player.toString(), slot);
        store(file, claims.tag);
        return slot;
    }

    public static int assignedSlot(Path dataDirectory, int capacity, UUID player) {
        return read(dataDirectory.resolve(FILE), capacity).players.getOrDefault(player, 0);
    }

    private static Claims read(Path file, int capacity) {
        try {
            var root = NbtIo.readCompressed(file, NbtAccounter.create(2 * 1024 * 1024));
            if (root.getIntOr("version", 0) != 1) {
                throw new IllegalStateException("Invalid Skyblock allocation version in " + file);
            }
            var tag = root.getCompound("players").orElseThrow(() -> new IllegalStateException("Missing Skyblock allocations in " + file));
            if (tag.size() > capacity) {
                throw new IllegalStateException("Too many Skyblock allocations in " + file);
            }
            BitSet occupied = new BitSet(capacity + 1);
            Map<UUID, Integer> players = new HashMap<>();
            for (String key : tag.keySet()) {
                int slot = tag.getIntOr(key, 0);
                UUID player = UUID.fromString(key);
                if (!player.toString().equals(key) || slot < 1 || slot > capacity || occupied.get(slot)) {
                    throw new IllegalStateException("Invalid or duplicate Skyblock allocation in " + file + " for " + key);
                }
                players.put(player, slot);
                occupied.set(slot);
            }
            return new Claims(tag, players, occupied);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read Skyblock allocations from " + file + ". Restore the original allocation file before opening the world.", e);
        }
    }

    private static void store(Path file, CompoundTag players) {
        CompoundTag root = new CompoundTag();
        root.putInt("version", 1);
        root.put("players", players);
        try {
            Files.createDirectories(file.getParent());
            Path temporary = Files.createTempFile(file.getParent(), ".worldarchitect-islands-", ".tmp");
            try {
                NbtIo.writeCompressed(root, temporary);
                try (FileChannel data = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                    data.force(true);
                }
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot save Skyblock allocations to " + file, e);
        }
    }

    private record Claims(CompoundTag tag, Map<UUID, Integer> players, BitSet occupied) {}
}
