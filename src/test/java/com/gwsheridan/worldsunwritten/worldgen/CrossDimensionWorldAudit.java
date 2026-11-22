package com.gwsheridan.worldsunwritten.worldgen;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

/** Verifies the cross-dimension example from flushed dedicated-server region files. */
public final class CrossDimensionWorldAudit {
    private CrossDimensionWorldAudit() {}

    public static void main(String[] args) throws IOException {
        Path world = Path.of(args[0]);
        int origin = Integer.parseInt(args[1]);
        CompoundTag settings = NbtIo.readCompressed(world.resolve("data/minecraft/world_gen_settings.dat"), NbtAccounter.unlimitedHeap())
                .getCompoundOrEmpty("data");
        CompoundTag snapshot = NbtIo.readCompressed(world.resolve("data/worldsunwritten-generation-v1.dat"), NbtAccounter.unlimitedHeap());
        require(settings.equals(snapshot.getCompoundOrEmpty("data")), "Generation settings changed");
        var dependencies = snapshot.getCompoundOrEmpty("structure_dependencies");
        require(dependencies.contains("minecraft:end_city") && dependencies.contains("wa_example:village")
                && dependencies.contains("wa_example:fortress"), "Structure definitions were not pinned");
        require(snapshot.getCompoundOrEmpty("generation_dependencies").contains("native_resources"), "End City template resources were not pinned");
        var city = start(world, "minecraft:overworld", origin, origin, "minecraft:end_city");
        require(city.getListOrEmpty("Children").size() > 1, "The Overworld End City has no modular native pieces");
        Map<String, CompoundTag> chunks = new HashMap<>();
        boolean purpur = false;
        for (int cx = origin - 4; cx <= origin + 4; cx++) {
            for (int cz = origin - 4; cz <= origin + 4; cz++) {
                var chunk = TerrainWorldAudit.readChunk(regions(world, "minecraft:overworld"), cx, cz);
                for (var section : chunk.getListOrEmpty("sections")) {
                    if (section instanceof CompoundTag tag) {
                        for (var entry : tag.getCompoundOrEmpty("block_states").getListOrEmpty("palette")) {
                            if (entry instanceof CompoundTag block && block.getStringOr("id", "").startsWith("minecraft:purpur")) {
                                purpur = true;
                            }
                        }
                    }
                }
            }
        }
        require(purpur, "No End City blocks were generated in the Overworld");
        long cells = auditAssembly(world, "wa_example:workshop", "wa_example:village", origin, 81, chunks);
        cells += auditAssembly(world, "wa_example:fortress", "wa_example:fortress", origin, 81, chunks);
        if (Boolean.parseBoolean(args[2])) {
            cells += auditAssembly(world, "wa_test:matching", "wa_test:matching", origin, 80, chunks);
            auditRejections(world, origin);
        }
        System.out.printf("Verified an Overworld End City, %,d modular cells and their states, metadata and markers.%n", cells);
    }

    private static void auditRejections(Path world, int origin) throws IOException {
        for (String rejection : List.of("wrong_dimension", "wrong_biome", "wrong_height", "unsupported", "collision")) {
            CompoundTag chunk = TerrainWorldAudit.readChunk(regions(world, "wa_test:" + rejection), origin, origin);
            CompoundTag invalid = chunk.getCompoundOrEmpty("structures").getCompoundOrEmpty("starts").getCompoundOrEmpty("wa_test:" + rejection);
            require(invalid.isEmpty() || invalid.getStringOr("id", "").equals("INVALID"), "Rejected assembly generated in " + rejection);
            var sections = FeatureWorldAudit.sections(chunk, -64, 384, "block_states", 4);
            for (int y = -64; y < 320; y++) {
                for (int xz = 0; xz < 256; xz++) {
                    String expected = y <= 80 ? "minecraft:stone" : "minecraft:air";
                    require(expected.equals(FeatureWorldAudit.block(sections, -64, y, xz)), "Rejected assembly changed terrain in " + rejection);
                }
            }
        }
        System.out.println("Verified five rejected whole-assembly rules and unchanged terrain.");
    }

    private static long auditAssembly(Path world, String dimension, String id, int origin, int floor, Map<String, CompoundTag> chunks) throws IOException {
        var structure = start(world, dimension, origin, origin, id);
        ListTag pieces = structure.getListOrEmpty("Children");
        require(pieces.size() == 3, "Expected all three modules in " + id);
        long cells = 0;
        int markers = 0;
        boolean metadata = false;
        for (var value : pieces) {
            var piece = (CompoundTag) value;
            require(piece.getStringOr("id", "").equals("worldsunwritten:module"), "Unexpected module piece type");
            int[] bounds = piece.getIntArray("BB").orElseThrow();
            require(bounds[1] == floor, "Incorrect placement height in " + id);
            CompoundTag template = piece.getCompoundOrEmpty("template");
            ListTag palette = template.getListOrEmpty("palette");
            for (var cellValue : template.getListOrEmpty("blocks")) {
                var cell = (CompoundTag) cellValue;
                var pos = cell.getListOrEmpty("pos");
                int x = bounds[0] + pos.getIntOr(0, 0);
                int y = bounds[1] + pos.getIntOr(1, 0);
                int z = bounds[2] + pos.getIntOr(2, 0);
                CompoundTag expected = palette.getCompoundOrEmpty(cell.getInt("state").orElseThrow());
                CompoundTag chunk = chunk(world, dimension, x, z, chunks);
                CompoundTag actual = block(chunk, x, y, z);
                require(expected.equals(actual), "Wrong generated state at " + dimension + " " + x + "," + y + "," + z
                        + ". Expected " + expected + ", found " + actual);
                if (cell.contains("nbt")) {
                    CompoundTag entity = entity(chunk, x, y, z);
                    CompoundTag data = cell.getCompoundOrEmpty("nbt");
                    for (String key : data.keySet()) {
                        require(java.util.Objects.equals(data.get(key), entity.get(key)), "Wrong generated metadata field " + key);
                    }
                    metadata = true;
                }
                cells++;
            }
            var savedMarkers = template.getCompoundOrEmpty("worldsunwritten").getCompoundOrEmpty("markers");
            for (String name : savedMarkers.keySet()) {
                var marker = savedMarkers.getCompoundOrEmpty(name).getListOrEmpty("pos");
                int x = bounds[0] + marker.getIntOr(0, 0);
                int y = bounds[1] + marker.getIntOr(1, 0);
                int z = bounds[2] + marker.getIntOr(2, 0);
                require(block(chunk(world, dimension, x, z, chunks), x, y, z).getStringOr("id", "").equals("minecraft:air"), "Occupied marker");
                require(block(chunk(world, dimension, x, z, chunks), x, y + 1, z).getStringOr("id", "").equals("minecraft:air"), "Occupied marker headroom");
                require(!block(chunk(world, dimension, x, z, chunks), x, y - 1, z).getStringOr("id", "").equals("minecraft:air"), "Unsupported marker");
                markers++;
            }
        }
        require(markers == 3 && metadata, "Missing generated markers or metadata in " + id);
        return cells;
    }

    private static CompoundTag start(Path world, String dimension, int x, int z, String id) throws IOException {
        var chunk = TerrainWorldAudit.readChunk(regions(world, dimension), x, z);
        require(chunk.getStringOr("Status", "").equals("minecraft:full"), "Incomplete start chunk");
        var start = chunk.getCompoundOrEmpty("structures").getCompoundOrEmpty("starts").getCompoundOrEmpty(id);
        require(start.getStringOr("id", "").equals(id), "Missing structure start " + id);
        return start;
    }

    static CompoundTag chunk(Path world, String dimension, int x, int z, Map<String, CompoundTag> cache) throws IOException {
        String key = dimension + "/" + (x >> 4) + "/" + (z >> 4);
        CompoundTag chunk = cache.get(key);
        if (chunk == null) {
            chunk = TerrainWorldAudit.readChunk(regions(world, dimension), x >> 4, z >> 4);
            cache.put(key, chunk);
        }
        require(chunk.getStringOr("Status", "").equals("minecraft:full"), "Incomplete module chunk");
        return chunk;
    }

    static CompoundTag block(CompoundTag chunk, int x, int y, int z) {
        for (var value : chunk.getListOrEmpty("sections")) {
            var section = (CompoundTag) value;
            if (section.getByte("Y").orElseThrow() == (y >> 4)) {
                var states = section.getCompoundOrEmpty("block_states");
                var palette = states.getListOrEmpty("palette");
                int index = 0;
                if (palette.size() > 1) {
                    int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
                    int offset = (y & 15) * 256 + (z & 15) * 16 + (x & 15);
                    long[] data = states.getLongArray("data").orElseThrow();
                    int perLong = 64 / bits;
                    index = (int) (data[offset / perLong] >>> (offset % perLong * bits)) & (1 << bits) - 1;
                }
                if (palette.get(index) instanceof CompoundTag state) {
                    return state;
                }
                return defaultState(palette.getString(index).orElseThrow());
            }
        }
        var air = new CompoundTag();
        air.putString("id", "minecraft:air");
        return air;
    }

    // Minecraft 26.3 writes default block states as registry ID strings in chunk palettes.
    private static CompoundTag defaultState(String id) {
        var state = new CompoundTag();
        state.putString("id", id);
        Map<String, String> values = switch (id) {
            case "minecraft:white_banner" -> Map.of("rotation", "8");
            case "minecraft:oak_log" -> Map.of("axis", "y");
            case "minecraft:oak_wall_sign" -> Map.of("facing", "north", "waterlogged", "false");
            case "minecraft:chest" -> Map.of("facing", "north", "type", "single", "waterlogged", "false");
            case "minecraft:polished_andesite_stairs" -> Map.of("facing", "north", "half", "bottom", "shape", "straight", "waterlogged", "false");
            default -> Map.of();
        };
        if (!values.isEmpty()) {
            var properties = new CompoundTag();
            values.forEach(properties::putString);
            state.put("properties", properties);
        }
        return state;
    }

    static CompoundTag entity(CompoundTag chunk, int x, int y, int z) {
        for (var value : chunk.getListOrEmpty("block_entities")) {
            var entity = (CompoundTag) value;
            if (entity.getIntOr("x", 0) == x && entity.getIntOr("y", 0) == y && entity.getIntOr("z", 0) == z) {
                return entity;
            }
        }
        throw new AssertionError("Missing generated block entity");
    }

    private static Path regions(Path world, String dimension) {
        return world.resolve("dimensions").resolve(dimension.replace(':', '/')).resolve("region");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
