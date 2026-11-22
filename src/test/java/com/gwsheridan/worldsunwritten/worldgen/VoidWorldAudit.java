package com.gwsheridan.worldsunwritten.worldgen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

/** Checks every generated block in selected chunks against the pinned island template (or air). */
public final class VoidWorldAudit {
    private VoidWorldAudit() {}

    public static void main(String[] args) throws IOException {
        Path world = Path.of(args[0]);
        CompoundTag saved = read(world.resolve("data/minecraft/world_gen_settings.dat")).getCompoundOrEmpty("data");
        require(saved.equals(read(world.resolve("data/worldsunwritten-generation-v1.dat")).getCompoundOrEmpty("data")),
                "Saved settings differ from the generation snapshot");
        String dimension = args[1];
        var generator = saved.getCompoundOrEmpty("dimensions").getCompoundOrEmpty(dimension).getCompoundOrEmpty("generator");
        require(generator.getStringOr("type", "").equals("worldsunwritten:void"), "Unexpected generator in " + dimension);
        var profile = generator.getCompoundOrEmpty("dimension");
        int minY = profile.getInt("min_y").orElseThrow();
        int height = profile.getInt("height").orElseThrow();
        var island = generator.getCompoundOrEmpty("island");
        var template = island.getCompoundOrEmpty("template");
        int spacing = island.getIntOr("spacing", 128);
        int personal = island.getIntOr("personal_slots", 0);
        Path ledger = world.resolve("data/worldsunwritten-islands-v1.dat");
        String allocationCount = args.length > 3 ? args[3] : "";
        String assignments = args.length > 4 ? args[4] : "";
        if (!allocationCount.isEmpty() || !assignments.isEmpty()) {
            verifyAllocations(ledger, allocationCount, assignments);
        }
        if (generator.getCompoundOrEmpty("island").isEmpty() && dimension.equals("minecraft:overworld")) {
            require(Files.notExists(ledger), "An empty Overworld has an island allocation file");
        }
        Path regions = world.resolve("dimensions").resolve(dimension.replace(':', '/')).resolve("region");
        long examined = 0;
        int nonAir = 0;
        for (String pair : args[2].split(";")) {
            String[] indices = pair.split(",");
            int cx = Integer.parseInt(indices[0]);
            int cz = Integer.parseInt(indices[1]);
            var chunk = TerrainWorldAudit.readChunk(regions, cx, cz);
            require(chunk.getStringOr("Status", "").equals("minecraft:full"), "Incomplete chunk " + pair);
            require(chunk.getListOrEmpty("block_entities").isEmpty(), "Unexpected block entities at " + pair);
            var structures = chunk.getCompoundOrEmpty("structures");
            require(structures.getCompoundOrEmpty("starts").isEmpty() && structures.getCompoundOrEmpty("References").isEmpty(),
                    "Natural structures at " + pair);

            Map<Position, String> expected = new HashMap<>();
            int startX = cx * 16;
            int startZ = cz * 16;
            if (!template.isEmpty() && startX >= 0 && startZ >= 0 && startX / spacing < 16
                    && (startZ / spacing) * 16 + startX / spacing <= personal) {
                int originX = (startX / spacing) * spacing;
                int originZ = (startZ / spacing) * spacing;
                int originY = island.getInt("origin_y").orElseThrow();
                var blocks = template.getListOrEmpty("blocks");
                for (int index = 0; index < blocks.size(); index++) {
                    var block = blocks.getCompoundOrEmpty(index);
                    int[] local = block.getIntArray("pos").orElseThrow();
                    int x = originX + local[0];
                    int y = originY + local[1];
                    int z = originZ + local[2];
                    if (x >= startX && x < startX + 16 && z >= startZ && z < startZ + 16) {
                        expected.put(new Position(x, y, z), block.getString("state").orElseThrow().split("\\[", 2)[0]);
                    }
                }
            }
            var sections = new TerrainWorldAudit.Section[height / 16];
            var tags = chunk.getListOrEmpty("sections");
            for (int index = 0; index < tags.size(); index++) {
                var section = tags.getCompoundOrEmpty(index);
                int y = section.getByte("Y").orElseThrow() - minY / 16;
                if (y >= 0 && y < sections.length) {
                    sections[y] = TerrainWorldAudit.Section.read(section.getCompoundOrEmpty("block_states"));
                }
            }
            for (int y = minY; y < minY + height; y++) {
                var section = sections[(y - minY) / 16];
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        var position = new Position(startX + x, y, startZ + z);
                        String actual = section == null ? "minecraft:air" : section.blockAt((y & 15) * 256 + z * 16 + x);
                        String block = expected.getOrDefault(position, "minecraft:air");
                        if (!actual.equals(block)) {
                            throw new AssertionError("Unexpected block at " + position + ": " + actual + " instead of " + block);
                        }
                        if (!actual.equals("minecraft:air")) {
                            nonAir++;
                        }
                        examined++;
                    }
                }
            }
        }
        System.out.printf("Verified %,d positions in %s, %,d template blocks, and no natural structures.%n", examined, dimension, nonAir);
    }

    private static void verifyAllocations(Path ledger, String expectedCount, String assignments) throws IOException {
        var players = read(ledger).getCompound("players").orElseThrow();
        if (!expectedCount.isEmpty()) {
            require(players.size() == Integer.parseInt(expectedCount), "Unexpected number of allocated islands");
        }
        if (!assignments.isEmpty()) {
            for (String assignment : assignments.split(";")) {
                String[] entry = assignment.split("=", 2);
                require(entry.length == 2, "Expected a UUID=slot assignment, found " + assignment);
                UUID player = UUID.fromString(entry[0]);
                int slot = Integer.parseInt(entry[1]);
                require(slot > 0 && players.getIntOr(player.toString(), 0) == slot,
                        "Expected player " + player + " to own island " + slot);
            }
        }
        System.out.printf("Verified island allocation ledger with %d owners.%n", players.size());
    }

    private static CompoundTag read(Path path) throws IOException {
        return NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record Position(int x, int y, int z) {}
}
