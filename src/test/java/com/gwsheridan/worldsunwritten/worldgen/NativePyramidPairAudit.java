package com.gwsheridan.worldsunwritten.worldgen;

import java.nio.file.Path;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Diagnostic for the fixed 384-high desert-pyramid benchmark, never a replacement for strict save audits. */
public final class NativePyramidPairAudit {
    private record Footprint(int minX, int minZ, int maxX, int maxZ) {
        boolean contains(int x, int z) { return x >= minX && x <= maxX && z >= minZ && z <= maxZ; }
    }

    public static void main(String[] args) throws Exception {
        Path left = Path.of(args[0]), right = Path.of(args[1]);
        int size=args.length>2?Integer.parseInt(args[2]):6,samples=args.length>3?Integer.parseInt(args[3]):2;
        require(size>=6&&size<=32&&samples>=2&&samples<=5,"Invalid benchmark sampling plan");
        int[] origins=Arrays.copyOf(new int[]{1000,-1000,2000,-2000,3000},samples);
        var a = new HashMap<String, CompoundTag>(); var b = new HashMap<String, CompoundTag>();
        var footprints = new HashSet<Footprint>(); var starts = new HashSet<Long>();
        for (int origin : origins) for (int x = origin; x < origin + size; x++) for (int z = origin; z < origin + size; z++) {
            var old = chunk(left, x, z, a); var current = chunk(right, x, z, b);
            require(Objects.equals(old.getCompoundOrEmpty("structures").get("References"), current.getCompoundOrEmpty("structures").get("References")), "Structure references changed");
            for (var value : old.getCompoundOrEmpty("structures").getCompoundOrEmpty("References").entrySet()) {
                for (long position : old.getCompoundOrEmpty("structures").getCompoundOrEmpty("References").getLongArray(value.getKey()).orElseThrow()) starts.add(position);
            }
            starts.add((x & 0xffffffffL) | ((long) z << 32));
        }
        int shifted = 0;
        for (long position : starts) {
            int x = (int) position, z = (int) (position >> 32);
            var old = chunk(left, x, z, a).getCompoundOrEmpty("structures").copy();
            var current = chunk(right, x, z, b).getCompoundOrEmpty("structures").copy();
            if (!Objects.equals(old, current)) shifted++;
            normalize(old, footprints); normalize(current, footprints);
            require(old.equals(current), "Structure difference exceeds the native pyramid burial offset at " + x + "," + z);
        }
        require(!footprints.isEmpty(), "No pyramid footprint in this fixture");
        long changedInside = 0, checkedOutside = 0; int changedHeightmapColumns = 0;
        for (int origin : origins) for (int x = origin; x < origin + size; x++) for (int z = origin; z < origin + size; z++) {
            var old = chunk(left, x, z, a); var current = chunk(right, x, z, b);
            require(old.getStringOr("Status", "").equals("minecraft:full") && current.getStringOr("Status", "").equals("minecraft:full"), "Incomplete measured chunk");
            var sections = new HashMap<Integer, CompoundTag>();
            for (var value : current.getListOrEmpty("sections")) { var section = (CompoundTag) value; sections.put(section.getIntOr("Y", -9999), section); }
            require(old.getListOrEmpty("sections").size() == 24 && sections.size() == 24, "Unexpected fixture height");
            for (var value : old.getListOrEmpty("sections")) {
                var section = (CompoundTag) value; int y = section.getIntOr("Y", -9999); var other = sections.get(y);
                require(other != null, "Missing section");
                BenchmarkWorldAudit.palette(section.getCompoundOrEmpty("biomes"), other.getCompoundOrEmpty("biomes"), 64, 1, "biomes", true, new HashMap<>(), x + "," + y + "," + z);
                Tag[] oldCells = cells(section.getCompoundOrEmpty("block_states")), newCells = cells(other.getCompoundOrEmpty("block_states"));
                for (int i = 0; i < 4096; i++) {
                    int blockX = x * 16 + (i & 15), blockZ = z * 16 + ((i >> 4) & 15);
                    if (inside(footprints, blockX, blockZ)) { if (!Objects.equals(oldCells[i], newCells[i])) changedInside++; }
                    else { checkedOutside++; require(Objects.equals(oldCells[i], newCells[i]), "Terrain changed outside native footprints at " + blockX + "," + (y * 16 + i / 256) + "," + blockZ); }
                }
            }
            var oldHeights = old.getCompoundOrEmpty("Heightmaps"); var newHeights = current.getCompoundOrEmpty("Heightmaps");
            require(oldHeights.keySet().equals(newHeights.keySet()), "Heightmap types changed");
            for (String key : oldHeights.keySet()) {
                long[] leftData = oldHeights.getLongArray(key).orElseThrow(), rightData = newHeights.getLongArray(key).orElseThrow();
                require(leftData.length == 37 && rightData.length == 37, "Unexpected heightmap encoding");
                for (int i = 0; i < 256; i++) if (packed(leftData, i, 9) != packed(rightData, i, 9)) {
                    require(inside(footprints, x * 16 + (i & 15), z * 16 + i / 16), "Heightmap changed outside native footprints"); changedHeightmapColumns++;
                }
            }
        }
        System.out.println("WA_NATIVE_PYRAMID_DIAGNOSTIC_PASS chunks="+(size*size*samples)+" footprints=" + footprints.size() + " changed_start_records=" + shifted
            + " changed_native_blocks=" + changedInside + " changed_native_heightmap_entries=" + changedHeightmapColumns + " strict_outside_block_positions=" + checkedOutside + " biomes=identical");
    }

    private static CompoundTag chunk(Path world, int x, int z, Map<String, CompoundTag> cache) throws Exception {
        String key = x + "," + z; var value = cache.get(key);
        if (value == null) { value = TerrainWorldAudit.readChunk(world.resolve("dimensions/minecraft/overworld/region"), x, z); cache.put(key, value); }
        return value;
    }

    private static void normalize(CompoundTag structures, Set<Footprint> footprints) {
        var values = structures.getCompoundOrEmpty("starts");
        for (String id : values.keySet()) {
            var start = values.getCompoundOrEmpty(id);
            if (!start.getStringOr("id", "").equals("minecraft:desert_pyramid")) continue;
            for (var value : start.getListOrEmpty("Children")) {
                var piece = (CompoundTag) value; int[] box = piece.getIntArray("BB").orElseThrow(); int height = piece.getIntOr("HPos", -1);
                require(piece.getStringOr("id", "").equals("minecraft:tedp") && box.length == 6 && box[3] - box[0] == 20 && box[5] - box[2] == 20 && box[4] - box[1] == 14, "Unexpected native pyramid shape");
                require(height >= 0 && height - box[1] >= 0 && height - box[1] <= 2, "Burial offset outside vanilla nextInt(3)");
                footprints.add(new Footprint(box[0], box[2], box[3], box[5]));
                box[1] = height; box[4] = height + 14; piece.putIntArray("BB", box);
            }
        }
    }

    private static boolean inside(Set<Footprint> footprints, int x, int z) { return footprints.stream().anyMatch(f -> f.contains(x, z)); }
    private static int packed(long[] data, int i, int bits) { return (int) (data[i / (64 / bits)] >>> (i % (64 / bits) * bits)) & ((1 << bits) - 1); }
    private static Tag[] cells(CompoundTag states) {
        var palette = states.getListOrEmpty("palette"); int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
        long[] data = states.getLongArray("data").orElse(new long[0]); var result = new Tag[4096];
        for (int i = 0; i < result.length; i++) result[i] = palette.get(palette.size() == 1 ? 0 : packed(data, i, bits));
        return result;
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
