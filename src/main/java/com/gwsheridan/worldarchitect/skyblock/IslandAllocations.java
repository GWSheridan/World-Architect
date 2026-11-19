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
            store(file, new CompoundTag(), new CompoundTag());
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
        store(file, claims.tag, claims.teamsTag);
        return slot;
    }

    public static int claimTeam(Path dataDirectory, int capacity, String team) {
        checkTeam(team);
        Path file = dataDirectory.resolve(FILE);
        Claims claims = read(file, capacity);
        Integer existing = claims.teams.get(team);
        if (existing != null) {
            return existing;
        }
        int slot = claims.occupied.nextClearBit(1);
        if (slot > capacity) {
            throw new SkyblockJoinException("All " + capacity + " personal Skyblock islands are assigned.");
        }
        claims.teamsTag.putInt(team, slot);
        store(file, claims.tag, claims.teamsTag);
        return slot;
    }

    public static void reassign(Path dataDirectory, int capacity, UUID player, int slot) {
        Path file = dataDirectory.resolve(FILE);
        Claims claims = read(file, capacity);
        int current = claims.players.getOrDefault(player, 0);
        requireVacant(claims, capacity, slot, current);
        if (current != slot) {
            claims.tag.putInt(player.toString(), slot);
            store(file, claims.tag, claims.teamsTag);
        }
    }

    public static void reassignTeam(Path dataDirectory, int capacity, String team, int slot) {
        checkTeam(team);
        Path file = dataDirectory.resolve(FILE);
        Claims claims = read(file, capacity);
        int current = claims.teams.getOrDefault(team, 0);
        requireVacant(claims, capacity, slot, current);
        if (current != slot) {
            claims.teamsTag.putInt(team, slot);
            store(file, claims.tag, claims.teamsTag);
        }
    }

    public static void swap(Path dataDirectory, int capacity, UUID first, UUID second) {
        Path file = dataDirectory.resolve(FILE);
        Claims claims = read(file, capacity);
        Integer a = claims.players.get(first);
        Integer b = claims.players.get(second);
        if (first.equals(second) || a == null || b == null) {
            throw new IllegalArgumentException("Choose two different players with assigned islands.");
        }
        claims.tag.putInt(first.toString(), b);
        claims.tag.putInt(second.toString(), a);
        store(file, claims.tag, claims.teamsTag);
    }

    public static Map<UUID, Integer> players(Path dataDirectory, int capacity) {
        return Map.copyOf(read(dataDirectory.resolve(FILE), capacity).players);
    }

    public static Map<String, Integer> teams(Path dataDirectory, int capacity) {
        return Map.copyOf(read(dataDirectory.resolve(FILE), capacity).teams);
    }

    public static boolean owns(Path dataDirectory, int capacity, UUID player, String team, int slot) {
        Claims claims = read(dataDirectory.resolve(FILE), capacity);
        return claims.players.getOrDefault(player, 0) == slot || claims.teams.getOrDefault(team, 0) == slot;
    }

    private static void requireVacant(Claims claims, int capacity, int slot, int current) {
        if (slot < 1 || slot > capacity) {
            throw new IllegalArgumentException("Choose an island slot between 1 and " + capacity + ".");
        }
        if (claims.occupied.get(slot) && slot != current) {
            throw new IllegalArgumentException("Island slot " + slot + " already has an owner. Use swap for two player assignments.");
        }
    }

    private static void checkTeam(String team) {
        if (team.isBlank() || team.length() > 64) {
            throw new IllegalArgumentException("Team names must contain 1–64 characters.");
        }
    }

    public static int assignedSlot(Path dataDirectory, int capacity, UUID player) {
        return read(dataDirectory.resolve(FILE), capacity).players.getOrDefault(player, 0);
    }

    private static Claims read(Path file, int capacity) {
        try {
            var root = NbtIo.readCompressed(file, NbtAccounter.create(2 * 1024 * 1024));
            int version = root.getIntOr("version", 0);
            if (version != 1 && version != 2) {
                throw new IllegalStateException("Invalid Skyblock allocation version in " + file);
            }
            var tag = root.getCompound("players").orElseThrow(() -> new IllegalStateException("Missing Skyblock allocations in " + file));
            var teamsTag = root.getCompoundOrEmpty("teams");
            if (version == 1 && root.contains("teams") || version == 2 && root.getCompound("teams").isEmpty()) {
                throw new IllegalStateException("Invalid team allocations in " + file);
            }
            if (tag.size() + teamsTag.size() > capacity) {
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
            Map<String, Integer> teams = new HashMap<>();
            for (String team : teamsTag.keySet()) {
                checkTeam(team);
                int slot = teamsTag.getIntOr(team, 0);
                if (slot < 1 || slot > capacity || occupied.get(slot)) {
                    throw new IllegalStateException("Invalid or duplicate Skyblock team allocation in " + file + " for " + team);
                }
                teams.put(team, slot);
                occupied.set(slot);
            }
            return new Claims(tag, teamsTag, players, teams, occupied);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read Skyblock allocations from " + file + ". Restore the original allocation file before opening the world.", e);
        }
    }

    private static void store(Path file, CompoundTag players, CompoundTag teams) {
        CompoundTag root = new CompoundTag();
        root.putInt("version", teams.isEmpty() ? 1 : 2);
        root.put("players", players);
        if (!teams.isEmpty()) {
            root.put("teams", teams);
        }
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

    private record Claims(CompoundTag tag, CompoundTag teamsTag, Map<UUID, Integer> players, Map<String, Integer> teams, BitSet occupied) {}
}
