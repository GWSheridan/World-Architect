package com.gwsheridan.worldarchitect.config;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Explicit generation exceptions. Empty settings retain the pre-0.3 pipeline. */
public record ContentProfile(List<Holder<Biome>> biomes, int biomeScale, long biomeSeed, List<Ore> ores, List<Decoration> decorations,
        Optional<Caves> caves, Optional<Sea> sea, List<StructureRule> structures) {
    public static final int MAX_RULES = 16;
    private static final Codec<Integer> ALTITUDE = Codec.intRange(DimensionType.MIN_Y, DimensionType.MAX_Y);
    private static final Codec<List<Holder<Biome>>> BIOMES = Biome.CODEC.listOf(0, MAX_RULES).validate(list ->
            list.stream().allMatch(biome -> biome.unwrapKey().isPresent()) && new HashSet<>(list).size() == list.size()
                    ? DataResult.success(list) : DataResult.error(() -> "Biome choices must be distinct registered biomes."));
    public static final ContentProfile EMPTY = new ContentProfile(List.of(), 128, 0L, List.of(), List.of(), Optional.empty(), Optional.empty(), List.of());
    public static final Codec<ContentProfile> CODEC = RecordCodecBuilder.<ContentProfile>create(builder -> builder.group(
            Codec.intRange(1, 1).optionalFieldOf("version", 1).forGetter(profile -> 1),
            BIOMES.optionalFieldOf("biomes", List.of()).forGetter(ContentProfile::biomes),
            Codec.intRange(16, 1024).optionalFieldOf("biome_scale", 128).forGetter(ContentProfile::biomeScale),
            Codec.LONG.optionalFieldOf("biome_seed", 0L).forGetter(ContentProfile::biomeSeed),
            Ore.CODEC.listOf(0, MAX_RULES).optionalFieldOf("ores", List.of()).forGetter(ContentProfile::ores),
            Decoration.CODEC.listOf(0, MAX_RULES).optionalFieldOf("decorations", List.of()).forGetter(ContentProfile::decorations),
            Caves.CODEC.optionalFieldOf("caves").forGetter(ContentProfile::caves),
            Sea.CODEC.optionalFieldOf("sea").forGetter(ContentProfile::sea),
            StructureRule.CODEC.listOf(0, MAX_RULES).optionalFieldOf("structures", List.of()).forGetter(ContentProfile::structures)
    ).apply(builder, (version, biomes, scale, seed, ores, decorations, caves, sea, structures) ->
            new ContentProfile(biomes, scale, seed, ores, decorations, caves, sea, structures))).validate(ContentProfile::validate);

    public ContentProfile {
        biomes = List.copyOf(biomes);
        ores = List.copyOf(ores);
        decorations = List.copyOf(decorations);
        structures = List.copyOf(structures);
    }

    private DataResult<ContentProfile> validate() {
        if (structures.stream().map(StructureRule::structure).distinct().count() != structures.size()) {
            return DataResult.error(() -> "Select a structure only once per dimension.");
        }
        return DataResult.success(this);
    }

    public ContentProfile withBiomes(List<Holder<Biome>> list, int scale, long seed) {
        return new ContentProfile(list, scale, seed, ores, decorations, caves, sea, structures);
    }

    public ContentProfile withOres(List<Ore> list) {
        return new ContentProfile(biomes, biomeScale, biomeSeed, list, decorations, caves, sea, structures);
    }

    public ContentProfile withDecorations(List<Decoration> list) {
        return new ContentProfile(biomes, biomeScale, biomeSeed, ores, list, caves, sea, structures);
    }

    public ContentProfile withCaves(Optional<Caves> rule) {
        return new ContentProfile(biomes, biomeScale, biomeSeed, ores, decorations, rule, sea, structures);
    }

    public ContentProfile withSea(Optional<Sea> rule) {
        return new ContentProfile(biomes, biomeScale, biomeSeed, ores, decorations, caves, rule, structures);
    }

    public ContentProfile withStructures(List<StructureRule> list) {
        return new ContentProfile(biomes, biomeScale, biomeSeed, ores, decorations, caves, sea, list);
    }

    public record Ore(BlockState material, Optional<BlockState> target, int attempts, int size, int minY, int maxY, List<Holder<Biome>> biomes) {
        public static final Codec<Ore> CODEC = RecordCodecBuilder.<Ore>create(builder -> builder.group(
                TerrainMaterial.CODEC.fieldOf("material").forGetter(Ore::material),
                TerrainMaterial.CODEC.optionalFieldOf("target").forGetter(Ore::target),
                Codec.intRange(1, 32).fieldOf("attempts").forGetter(Ore::attempts),
                Codec.intRange(1, 64).fieldOf("size").forGetter(Ore::size),
                ALTITUDE.fieldOf("min_y").forGetter(Ore::minY), ALTITUDE.fieldOf("max_y").forGetter(Ore::maxY),
                BIOMES.optionalFieldOf("biomes", List.of()).forGetter(Ore::biomes)
        ).apply(builder, Ore::new)).validate(rule -> rule.material.getBlock() instanceof FallingBlock
                ? DataResult.error(() -> "Ore output must be a stable solid block.") : ordered(rule, rule.minY, rule.maxY));

        public Ore {
            biomes = List.copyOf(biomes);
        }
    }

    public record Decoration(BlockState material, int attempts, int chance, List<Holder<Biome>> biomes,
            Optional<Integer> minY, Optional<Integer> maxY) {
        private static final Codec<BlockState> MATERIAL = Codec.STRING.comapFlatMap(Decoration::parseMaterial, BlockStateParser::serialize);
        public static final Codec<Decoration> CODEC = RecordCodecBuilder.<Decoration>create(builder -> builder.group(
                MATERIAL.fieldOf("material").forGetter(Decoration::material),
                Codec.intRange(1, 32).fieldOf("attempts").forGetter(Decoration::attempts),
                Codec.intRange(1, 100).fieldOf("chance").forGetter(Decoration::chance),
                BIOMES.optionalFieldOf("biomes", List.of()).forGetter(Decoration::biomes),
                ALTITUDE.optionalFieldOf("min_y").forGetter(Decoration::minY),
                ALTITUDE.optionalFieldOf("max_y").forGetter(Decoration::maxY)
        ).apply(builder, Decoration::new)).validate(rule -> ordered(rule,
                rule.minY.orElse(DimensionType.MIN_Y), rule.maxY.orElse(DimensionType.MAX_Y)));

        public Decoration {
            biomes = List.copyOf(biomes);
        }

        public static DataResult<BlockState> parseMaterial(String id) {
            try {
                BlockState state = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, id, false).blockState();
                if (state.getBlock() instanceof FlowerBlock && !state.hasBlockEntity() && state.getFluidState().isEmpty()
                        && !state.isRandomlyTicking()) {
                    return DataResult.success(state);
                }
                return TerrainMaterial.parse(id).flatMap(solid -> solid.getBlock() instanceof FallingBlock
                        ? DataResult.error(() -> "Falling blocks cannot be scattered as decorations.") : DataResult.success(solid));
            } catch (CommandSyntaxException e) {
                return DataResult.error(() -> e.getRawMessage().getString());
            }
        }
    }

    public record Caves(int minY, int maxY, int radius) {
        public static final Codec<Caves> CODEC = RecordCodecBuilder.<Caves>create(builder -> builder.group(
                ALTITUDE.fieldOf("min_y").forGetter(Caves::minY), ALTITUDE.fieldOf("max_y").forGetter(Caves::maxY),
                Codec.intRange(1, 12).fieldOf("radius").forGetter(Caves::radius)
        ).apply(builder, Caves::new)).validate(rule -> ordered(rule, rule.minY, rule.maxY));
    }

    public record Sea(Fluid fluid, int level, boolean floodCaves) {
        public static final Codec<Sea> CODEC = RecordCodecBuilder.create(builder -> builder.group(
                StringRepresentable.fromEnum(Fluid::values).fieldOf("fluid").forGetter(Sea::fluid),
                ALTITUDE.fieldOf("level").forGetter(Sea::level), Codec.BOOL.optionalFieldOf("flood_caves", false).forGetter(Sea::floodCaves)
        ).apply(builder, Sea::new));
    }

    public enum Fluid implements StringRepresentable {
        WATER("water"), LAVA("lava");

        private final String id;
        Fluid(String id) { this.id = id; }
        @Override public String getSerializedName() { return id; }
        public BlockState state() { return (this == WATER ? Blocks.WATER : Blocks.LAVA).defaultBlockState(); }
    }

    public record StructureRule(Holder<Structure> structure, int spacing, int separation) {
        public static final Codec<StructureRule> CODEC = RecordCodecBuilder.<StructureRule>create(builder -> builder.group(
                Structure.CODEC.fieldOf("structure").forGetter(StructureRule::structure),
                Codec.intRange(4, 128).fieldOf("spacing").forGetter(StructureRule::spacing),
                Codec.intRange(3, 127).fieldOf("separation").forGetter(StructureRule::separation)
        ).apply(builder, StructureRule::new)).validate(rule -> {
            if (rule.separation >= rule.spacing) {
                return DataResult.error(() -> "Structure separation must be smaller than spacing.");
            }
            // World presets can be decoded before the referenced structure is bound.
            return rule.structure.unwrapKey().isPresent()
                    ? DataResult.success(rule) : DataResult.error(() -> "Select a registered structure.");
        });
    }

    private static <T> DataResult<T> ordered(T rule, int min, int max) {
        return min <= max ? DataResult.success(rule) : DataResult.error(() -> "Minimum height must not exceed maximum height.");
    }
}
