package com.gwsheridan.worldsunwritten.worldgen;

import com.gwsheridan.worldsunwritten.worldgen.terrain.HeightfieldV1;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

/** Audits real terrain and profile data after saving and stopping a dedicated test server. */
public final class TerrainWorldAudit {
    private TerrainWorldAudit() {}

    public static void main(String[] args) throws IOException {
        Path world = Path.of(args[0]);
        CompoundTag settings = NbtIo.readCompressed(world.resolve("data/minecraft/world_gen_settings.dat"), NbtAccounter.unlimitedHeap());
        CompoundTag snapshot = NbtIo.readCompressed(world.resolve("data/worldsunwritten-generation-v1.dat"), NbtAccounter.unlimitedHeap());
        CompoundTag data = settings.getCompoundOrEmpty("data");
        require(data.equals(snapshot.getCompoundOrEmpty("data")), "Saved settings and snapshot differ");
        String dimension = args[4];
        CompoundTag generator = data.getCompoundOrEmpty("dimensions").getCompoundOrEmpty(dimension).getCompoundOrEmpty("generator");
        require(generator.getStringOr("type", "").equals("worldsunwritten:" + args[2]), "Unexpected saved generator");
        CompoundTag profile = generator.getCompoundOrEmpty("profile");
        List<String> paletteByDepth = new ArrayList<>();
        boolean layered = args[2].equals("layered");
        String base = profile.getStringOr(layered ? "base_material" : "material", "");
        if (layered) {
            var layers = profile.getListOrEmpty("layers");
            for (int index = 0; index < layers.size(); index++) {
                CompoundTag layer = layers.getCompoundOrEmpty(index);
                paletteByDepth.addAll(Collections.nCopies(layer.getInt("depth").orElseThrow(), layer.getString("material").orElseThrow()));
            }
            require(!paletteByDepth.isEmpty() && !paletteByDepth.contains(base), "Expected the Layered fixture with distinct materials");
        }
        String expectedBase = args[3].isEmpty() ? (layered ? "minecraft:stone" : "minecraft:sand") : args[3];
        require(base.equals(expectedBase), "Expected base material " + expectedBase + ", found " + base);
        require(profile.getIntOr("generator_version", 0) == 1, "Unexpected generator version");
        long seed = data.getLong("seed").orElseThrow();
        int elevation = profile.getInt("elevation").orElseThrow();
        int relief = profile.getInt("relief").orElseThrow();
        CompoundTag dimensionProfile = generator.getCompoundOrEmpty("dimension_profile");
        int minY = dimensionProfile.getIntOr("min_y", -64);
        int height = dimensionProfile.getIntOr("height", 384);
        int maxY = minY + height - 1;
        Path regionDir = world.resolve("dimensions").resolve(dimension.replace(':', '/')).resolve("region");
        long inspected = 0;
        int minimum = Integer.MAX_VALUE;
        int maximum = Integer.MIN_VALUE;
        for (String pair : args[1].split(";")) {
            String[] coordinates = pair.split(",");
            int chunkX = Integer.parseInt(coordinates[0]);
            int chunkZ = Integer.parseInt(coordinates[1]);
            CompoundTag chunk = readChunk(regionDir, chunkX, chunkZ);
            require(chunk.getStringOr("Status", "").equals("minecraft:full"), "Chunk is incomplete at " + pair);
            require(chunk.getCompoundOrEmpty("structures").getCompoundOrEmpty("starts").isEmpty(), "Structure starts at " + pair);
            Section[] sections = new Section[height / 16];
            var sectionTags = chunk.getListOrEmpty("sections");
            for (int index = 0; index < sectionTags.size(); index++) {
                CompoundTag section = sectionTags.getCompoundOrEmpty(index);
                int y = section.getByte("Y").orElseThrow() - minY / 16;
                if (y >= 0 && y < sections.length) {
                    sections[y] = Section.read(section.getCompoundOrEmpty("block_states"));
                }
            }
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int top = Math.clamp(HeightfieldV1.surface(seed, chunkX * 16 + x, chunkZ * 16 + z, elevation, relief), minY, maxY - 3);
                    minimum = Math.min(minimum, top);
                    maximum = Math.max(maximum, top);
                    for (int y = minY; y <= maxY; y++) {
                        Section section = sections[(y - minY) / 16];
                        String actual = section == null ? "minecraft:air" : section.blockAt((y & 15) * 256 + z * 16 + x);
                        int depth = top - y;
                        String expected = depth < 0 ? "minecraft:air" : depth < paletteByDepth.size() ? paletteByDepth.get(depth) : base;
                        if (!actual.equals(expected)) {
                            throw new AssertionError("Incorrect block at " + (chunkX * 16 + x) + ", " + y + ", " + (chunkZ * 16 + z)
                                    + ". Expected " + expected + ", found " + actual);
                        }
                        inspected++;
                    }
                }
            }
        }
        require(relief != 0 || maximum == minimum, "Flat terrain did not stay flat");
        System.out.printf("Verified %,d block positions in %s, surface Y %d to %d, saved %s profile with base %s, and no structure starts.%n",
                inspected, dimension, minimum, maximum, args[2], base);
    }

    static CompoundTag readChunk(Path regionDirectory, int chunkX, int chunkZ) throws IOException {
        Path region = regionDirectory.resolve("r." + Math.floorDiv(chunkX, 32) + "." + Math.floorDiv(chunkZ, 32) + ".mca");
        try (RandomAccessFile file = new RandomAccessFile(region.toFile(), "r")) {
            file.seek(4L * (Math.floorMod(chunkX, 32) + 32 * Math.floorMod(chunkZ, 32)));
            int sector = file.readInt() >>> 8;
            require(sector >= 2, "Missing chunk at " + chunkX + "," + chunkZ);
            file.seek(sector * 4096L);
            int length = file.readInt();
            int compression = file.readUnsignedByte();
            byte[] buffer = new byte[length - 1];
            file.readFully(buffer);
            ByteArrayInputStream input = new ByteArrayInputStream(buffer);
            try (DataInputStream decoded = new DataInputStream(switch (compression) {
                case 1 -> new GZIPInputStream(input);
                case 2 -> new InflaterInputStream(input);
                case 3 -> input;
                default -> throw new IOException("Unsupported region compression " + compression);
            })) {
                return NbtIo.read(decoded, NbtAccounter.unlimitedHeap());
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    record Section(String[] blocks, long[] indices, int width) {
        static Section read(CompoundTag states) {
            return read(states, 4);
        }

        static Section read(CompoundTag states, int minimumBits) {
            var palette = states.getListOrEmpty("palette");
            if (palette.isEmpty()) {
                return null;
            }
            String[] blocks = new String[palette.size()];
            for (int index = 0; index < palette.size(); index++) {
                CompoundTag entry = palette.getCompoundOrEmpty(index);
                blocks[index] = palette.getStringOr(index, entry.getStringOr("id", entry.getStringOr("Name", "")));
            }
            int width = Math.max(minimumBits, Integer.SIZE - Integer.numberOfLeadingZeros(blocks.length - 1));
            return new Section(blocks, states.getLongArray("data").orElseGet(() -> new long[0]), width);
        }

        String blockAt(int index) {
            if (blocks.length == 1) {
                return blocks[0];
            }
            int perLong = Long.SIZE / width;
            int paletteIndex = (int) (indices[index / perLong] >>> ((index % perLong) * width)) & ((1 << width) - 1);
            return blocks[paletteIndex];
        }
    }
}
