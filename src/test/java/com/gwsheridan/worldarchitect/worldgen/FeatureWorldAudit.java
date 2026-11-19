package com.gwsheridan.worldarchitect.worldgen;

import com.gwsheridan.worldarchitect.worldgen.TerrainWorldAudit.Section;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

/** Checks actual blocks, biome palettes, and structure starts from the dedicated-server example. */
public final class FeatureWorldAudit {
    private FeatureWorldAudit() {}

    public static void main(String[] args) throws IOException {
        Path world = Path.of(args[0]);
        int origin = Integer.parseInt(args[1]);
        CompoundTag snapshot = NbtIo.readCompressed(world.resolve("data/worldarchitect-generation-v1.dat"), NbtAccounter.unlimitedHeap());
        CompoundTag settings = NbtIo.readCompressed(world.resolve("data/minecraft/world_gen_settings.dat"), NbtAccounter.unlimitedHeap());
        require(snapshot.getCompoundOrEmpty("data").equals(settings.getCompoundOrEmpty("data")), "Generation settings differ from the snapshot");
        require(snapshot.getCompoundOrEmpty("structure_dependencies").contains("minecraft:desert_pyramid"), "Pyramid definition was not pinned");
        var content = settings.getCompoundOrEmpty("data").getCompoundOrEmpty("dimensions").getCompoundOrEmpty("minecraft:overworld")
                .getCompoundOrEmpty("generator").getCompoundOrEmpty("dimension_profile").getCompoundOrEmpty("content");
        var oreBiomes = ruleBiomes(findRule(content, "ores", "minecraft:gold_ore"));
        var decoration = findRule(content, "decorations", "minecraft:amethyst_block");
        var decorationBiomes = ruleBiomes(decoration);
        if (Boolean.parseBoolean(args[2])) {
            require(oreBiomes.equals(Set.of("minecraft:forest")), "Expected the saved forest filter for gold");
            require(decorationBiomes.equals(Set.of("minecraft:plains"))
                    && decoration.getIntOr("min_y", 0) == 57 && decoration.getIntOr("max_y", 0) == 57,
                    "Expected the saved plains filter and Y=57 for amethyst");
        }

        Set<String> biomes = new HashSet<>();
        int ores = 0;
        int decorations = 0;
        int caves = 0;
        for (int offset : new int[] {-6, -1, 0, 6}) {
            CompoundTag chunk = read(world, "overworld", origin + offset, origin + offset);
            require(starts(chunk).isEmpty(), "Unexpected Overworld structure");
            collectBiomes(chunk, biomes);
            Section[] sections = sections(chunk, -64, 384);
            Section[] biomeSections = sections(chunk, -64, 384, "biomes", 1);
            for (int y = -64; y < 320; y++) {
                for (int xz = 0; xz < 256; xz++) {
                    String id = block(sections, -64, y, xz);
                    if (y <= 56 && id.equals("minecraft:stone") || y > 56 && id.equals("minecraft:air")) {
                        continue;
                    }
                    if (y >= 8 && y <= 18 && id.equals("minecraft:gold_ore")) {
                        checkBiome(oreBiomes, biomeSections, y, xz, id);
                        ores++;
                    } else if (y >= 20 && y <= 43 && id.equals("minecraft:air")) {
                        caves++;
                    } else if (y == 57 && id.equals("minecraft:amethyst_block")) {
                        checkBiome(decorationBiomes, biomeSections, y, xz, id);
                        decorations++;
                    } else {
                        throw new AssertionError("Unexpected Overworld block " + id + " at Y=" + y);
                    }
                }
            }
        }
        require(ores > 0 && decorations > 0 && caves > 0, "Expected all configured Overworld exceptions. Found "
                + ores + " ore, " + decorations + " decorations and " + caves + " cave blocks");
        require(biomes.equals(Set.of("minecraft:plains", "minecraft:forest")), "Unexpected Overworld biomes " + biomes);

        CompoundTag nether = read(world, "the_nether", origin, origin);
        require(starts(nether).isEmpty(), "Unexpected Nether structure");
        Section[] netherSections = sections(nether, 0, 256);
        for (int y = 0; y < 256; y++) {
            String expected = y <= 28 ? "minecraft:netherrack" : y <= 34 ? "minecraft:lava" : "minecraft:air";
            for (int xz = 0; xz < 256; xz++) {
                if (!block(netherSections, 0, y, xz).equals(expected)) {
                    throw new AssertionError("Unexpected Nether block at Y=" + y);
                }
            }
        }

        CompoundTag end = read(world, "the_end", origin, origin);
        require(starts(end).keySet().equals(Set.of("minecraft:desert_pyramid")), "Unexpected End structure starts: " + starts(end).keySet());
        require(!starts(end).getCompoundOrEmpty("minecraft:desert_pyramid").getListOrEmpty("Children").isEmpty(), "Pyramid start has no pieces");
        Set<String> endBiomes = new HashSet<>();
        collectBiomes(end, endBiomes);
        require(endBiomes.equals(Set.of("minecraft:desert")), "Unexpected End biomes " + endBiomes);
        boolean tnt = false;
        Section[] endSections = sections(end, 0, 256);
        for (int y = 0; y < 120; y++) {
            for (int xz = 0; xz < 256; xz++) {
                tnt |= block(endSections, 0, y, xz).equals("minecraft:tnt");
            }
        }
        require(tnt, "No pyramid trap blocks were placed in the End");
        System.out.printf("Audited 458,752 terrain positions, %d ore blocks, %d decorations, %d dry cave blocks, biome palettes, and a placed End pyramid.%n",
                ores, decorations, caves);
        if (!oreBiomes.isEmpty() || !decorationBiomes.isEmpty()) {
            System.out.printf("Verified ore biomes %s and decoration biomes %s.%n", oreBiomes, decorationBiomes);
        }
    }

    private static CompoundTag findRule(CompoundTag content, String category, String material) {
        var rules = content.getListOrEmpty(category);
        for (int index = 0; index < rules.size(); index++) {
            CompoundTag rule = rules.getCompoundOrEmpty(index);
            if (material.equals(rule.getStringOr("material", ""))) {
                return rule;
            }
        }
        throw new AssertionError("Missing saved rule for " + material);
    }

    private static Set<String> ruleBiomes(CompoundTag rule) {
        var list = rule.getListOrEmpty("biomes");
        Set<String> ids = new HashSet<>();
        for (int biome = 0; biome < list.size(); biome++) {
            ids.add(list.getString(biome).orElseThrow());
        }
        return ids;
    }

    private static void checkBiome(Set<String> allowed, Section[] sections, int y, int xz, String block) {
        if (!allowed.isEmpty()) {
            String actual = sections[(y + 64) / 16].blockAt((y & 15) / 4 * 16 + (xz >> 6) * 4 + (xz & 15) / 4);
            require(allowed.contains(actual), "Unexpected biome " + actual + " for " + block);
        }
    }

    private static CompoundTag read(Path world, String dimension, int x, int z) throws IOException {
        CompoundTag chunk = TerrainWorldAudit.readChunk(world.resolve("dimensions/minecraft").resolve(dimension).resolve("region"), x, z);
        require(chunk.getStringOr("Status", "").equals("minecraft:full"), "Chunk is incomplete in " + dimension + " at " + x + "," + z);
        return chunk;
    }

    private static CompoundTag starts(CompoundTag chunk) {
        return chunk.getCompoundOrEmpty("structures").getCompoundOrEmpty("starts");
    }

    private static void collectBiomes(CompoundTag chunk, Set<String> result) {
        var sections = chunk.getListOrEmpty("sections");
        for (int index = 0; index < sections.size(); index++) {
            var palette = sections.getCompoundOrEmpty(index).getCompoundOrEmpty("biomes").getListOrEmpty("palette");
            for (int value = 0; value < palette.size(); value++) {
                result.add(palette.getString(value).orElseThrow());
            }
        }
    }

    private static Section[] sections(CompoundTag chunk, int min, int height) {
        return sections(chunk, min, height, "block_states", 4);
    }

    static Section[] sections(CompoundTag chunk, int min, int height, String key, int minimumBits) {
        Section[] result = new Section[height / 16];
        var list = chunk.getListOrEmpty("sections");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag section = list.getCompoundOrEmpty(i);
            int index = section.getByte("Y").orElseThrow() - min / 16;
            if (index >= 0 && index < result.length) {
                result[index] = Section.read(section.getCompoundOrEmpty(key), minimumBits);
            }
        }
        return result;
    }

    static String block(Section[] sections, int min, int y, int xz) {
        Section section = sections[(y - min) / 16];
        return section == null ? "minecraft:air" : section.blockAt((y & 15) * 256 + xz);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
