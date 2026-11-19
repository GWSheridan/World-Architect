package com.gwsheridan.worldarchitect.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import com.gwsheridan.worldarchitect.rules.RuleSet;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.dimension.DimensionType;

/** The persisted vertical range and optional adapter dependency of an explicitly managed dimension. */
public record DimensionProfile(int minY, int height, Optional<AdapterBinding> adapter, ContentProfile content, RuleSet rules,
        Optional<EndProgression> endProgression) {
    public static final Codec<DimensionProfile> CODEC = RecordCodecBuilder.<DimensionProfile>create(builder -> builder.group(
            Codec.intRange(DimensionType.MIN_Y, DimensionType.MAX_Y - 15).fieldOf("min_y").forGetter(DimensionProfile::minY),
            Codec.intRange(DimensionType.MIN_HEIGHT, DimensionType.Y_SIZE).fieldOf("height").forGetter(DimensionProfile::height),
            AdapterBinding.CODEC.optionalFieldOf("adapter").forGetter(DimensionProfile::adapter),
            ContentProfile.CODEC.optionalFieldOf("content", ContentProfile.EMPTY).forGetter(DimensionProfile::content),
            RuleSet.CODEC.optionalFieldOf("rule_engine", RuleSet.EMPTY).forGetter(DimensionProfile::rules),
            EndProgression.CODEC.optionalFieldOf("end_progression").forGetter(DimensionProfile::endProgression)
    ).apply(builder, DimensionProfile::new)).validate(profile ->
            profile.minY % 16 == 0 && profile.height % 16 == 0 && profile.minY + profile.height - 1 <= DimensionType.MAX_Y
                    ? DataResult.success(profile) : DataResult.error(() -> "Invalid World Architect vertical range"));

    public DimensionProfile(int minY, int height, Optional<AdapterBinding> adapter) {
        this(minY, height, adapter, ContentProfile.EMPTY, RuleSet.EMPTY);
    }

    public DimensionProfile(int minY, int height, Optional<AdapterBinding> adapter, ContentProfile content, RuleSet rules) {
        this(minY, height, adapter, content, rules, Optional.empty());
    }

    public DimensionProfile(int minY, int height, Optional<AdapterBinding> adapter, ContentProfile content) {
        this(minY, height, adapter, content, RuleSet.EMPTY);
    }

    public DimensionProfile withContent(ContentProfile rules) {
        return new DimensionProfile(minY, height, adapter, rules, this.rules, endProgression);
    }

    public DimensionProfile withRules(RuleSet rules) {
        return new DimensionProfile(minY, height, adapter, content, rules, endProgression);
    }

    public DimensionProfile withEndProgression(Optional<EndProgression> progression) {
        return new DimensionProfile(minY, height, adapter, content, rules, progression);
    }

    public record AdapterBinding(Identifier id, int version) {
        private static final Codec<AdapterBinding> CODEC = RecordCodecBuilder.create(builder -> builder.group(
                Identifier.CODEC.fieldOf("id").forGetter(AdapterBinding::id),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("version").forGetter(AdapterBinding::version)
        ).apply(builder, AdapterBinding::new));
    }
}
