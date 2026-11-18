package com.gwsheridan.worldarchitect.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.dimension.DimensionType;

record TemplateRules(Optional<Identifier> dimension, Optional<Identifier> biome, int minY, int maxY,
        Terrain terrain, Collision collision) {
    static final TemplateRules DEFAULT = new TemplateRules(Optional.empty(), Optional.empty(), DimensionType.MIN_Y,
            DimensionType.MAX_Y, Terrain.FREE, Collision.AIR);

    static final Codec<TemplateRules> CODEC = RecordCodecBuilder.<TemplateRules>create(builder -> builder.group(
            Identifier.CODEC.optionalFieldOf("dimension").forGetter(TemplateRules::dimension),
            Identifier.CODEC.optionalFieldOf("biome").forGetter(TemplateRules::biome),
            Codec.intRange(DimensionType.MIN_Y, DimensionType.MAX_Y).optionalFieldOf("min_y", DimensionType.MIN_Y).forGetter(TemplateRules::minY),
            Codec.intRange(DimensionType.MIN_Y, DimensionType.MAX_Y).optionalFieldOf("max_y", DimensionType.MAX_Y).forGetter(TemplateRules::maxY),
            Codec.STRING.xmap(Terrain::parse, Terrain::key).optionalFieldOf("terrain", Terrain.FREE).forGetter(TemplateRules::terrain),
            Codec.STRING.xmap(Collision::parse, Collision::key).optionalFieldOf("collision", Collision.AIR).forGetter(TemplateRules::collision)
    ).apply(builder, TemplateRules::new)).validate(rules -> rules.minY <= rules.maxY ? DataResult.success(rules)
            : DataResult.error(() -> "The minimum placement height exceeds the maximum."));

    enum Terrain {
        FREE, SUPPORTED, BURIED;

        static Terrain parse(String key) {
            return valueOf(key.toUpperCase(Locale.ROOT));
        }

        String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    enum Collision {
        AIR, MATCHING;

        static Collision parse(String key) {
            return valueOf(key.toUpperCase(Locale.ROOT));
        }

        String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
