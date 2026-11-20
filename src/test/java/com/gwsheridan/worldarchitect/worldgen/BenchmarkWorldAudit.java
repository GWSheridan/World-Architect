package com.gwsheridan.worldarchitect.worldgen;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import net.minecraft.nbt.CompoundTag;

/** Strict managed-generation comparison; native generation has a separate same-binary control. */
public final class BenchmarkWorldAudit {
    private static Path output;
    private static int size=6,samples=2;
    public static void main(String[] args) throws Exception {
        output = Path.of(args[1]);
        if(args.length>3)size=Integer.parseInt(args[3]);if(args.length>4)samples=Integer.parseInt(args[4]);
        if(size<6||size>8||samples<2||samples>5)throw new IllegalArgumentException("Invalid benchmark sampling plan");
        if (args.length > 2 && !args[2].isEmpty()) compare("same-binary 0.9.0 native control", Path.of(args[0], "normal", "world"), Path.of(args[2]), false);
        for (String profile : new String[] {"normal", "void", "single-block", "layered", "structure-heavy"}) {
            compare(profile, Path.of(args[0], profile, "world"), Path.of(args[1], profile, "world"), !profile.equals("normal"));
        }
        int chunks=size*size*samples;System.out.println("Compared "+(chunks*5)+" measured chunks: "+(chunks*4)+" managed chunks strictly; "+chunks+" native chunks diagnostically.");
    }
    private static void compare(String profile, Path left, Path right, boolean strict) throws Exception {
        var a = new HashMap<String, CompoundTag>(); var b = new HashMap<String, CompoundTag>();
        var differences = new TreeMap<String, Integer>(); int heightmaps = 0, structures = 0;
        for (int origin : java.util.Arrays.copyOf(new int[] {1000, -1000,2000,-2000,3000},samples)) for (int x = origin; x < origin + size; x++) for (int z = origin; z < origin + size; z++) {
            var old = CrossDimensionWorldAudit.chunk(left, "minecraft:overworld", x * 16, z * 16, a);
            var current = CrossDimensionWorldAudit.chunk(right, "minecraft:overworld", x * 16, z * 16, b);
            if (!Objects.equals(old.get("structures"), current.get("structures"))) { structures++; require(!strict, profile + " changed structures at " + x + "," + z + " BASELINE=" + old.get("structures") + " CANDIDATE=" + current.get("structures")); }
            if (!Objects.equals(old.get("Heightmaps"), current.get("Heightmaps"))) { heightmaps++; require(!strict, profile + " changed heightmaps at " + x + "," + z); }
            for (var value : old.getListOrEmpty("sections")) {
                var section = (CompoundTag) value; int y = section.getIntOr("Y", -9999);
                var other = current.getListOrEmpty("sections").stream().map(CompoundTag.class::cast).filter(s -> s.getIntOr("Y", -9998) == y).findFirst().orElseThrow();
                palette(section.getCompoundOrEmpty("block_states"), other.getCompoundOrEmpty("block_states"), 4096, 4, "blocks", strict, differences, profile + " " + x + "," + y + "," + z);
                palette(section.getCompoundOrEmpty("biomes"), other.getCompoundOrEmpty("biomes"), 64, 1, "biomes", true, differences, profile + " " + x + "," + y + "," + z);
            }
        }
        if (!strict) {
            var lines = new java.util.ArrayList<String>(); lines.add("count\tblock-state transition");
            differences.forEach((key, count) -> lines.add(count + "\t" + key));
            java.nio.file.Files.write(output.resolve(profile.replaceAll("[^a-zA-Z0-9-]", "-") + "-native-differences.tsv"), lines);
        }
        System.out.println(profile + ": " + (strict ? "PASS, "+(size*size*samples)+" identical managed chunks" : "native diagnostic, changed heightmap chunks=" + heightmaps + ", structures=" + structures + ", changed blocks=" + differences.values().stream().mapToLong(Integer::longValue).sum() + ", transition types=" + differences.size()));
    }
    static void palette(CompoundTag left, CompoundTag right, int size, int minimumBits, String kind, boolean strict, Map<String, Integer> differences, String context) {
        if (left.equals(right)) return;
        var a = left.getListOrEmpty("palette"); var b = right.getListOrEmpty("palette");
        int aBits = Math.max(minimumBits, 32 - Integer.numberOfLeadingZeros(a.size() - 1));
        int bBits = Math.max(minimumBits, 32 - Integer.numberOfLeadingZeros(b.size() - 1));
        long[] aData = left.getLongArray("data").orElse(new long[0]), bData = right.getLongArray("data").orElse(new long[0]);
        for (int i = 0; i < size; i++) {
            int ai = a.size() == 1 ? 0 : (int) (aData[i / (64 / aBits)] >>> (i % (64 / aBits) * aBits)) & (1 << aBits) - 1;
            int bi = b.size() == 1 ? 0 : (int) (bData[i / (64 / bBits)] >>> (i % (64 / bBits) * bBits)) & (1 << bBits) - 1;
            if (!Objects.equals(a.get(ai), b.get(bi))) {
                require(!strict, context + " " + kind + " cell " + i + ": " + a.get(ai) + " -> " + b.get(bi));
                differences.merge(a.get(ai) + " -> " + b.get(bi), 1, Integer::sum);
            }
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
