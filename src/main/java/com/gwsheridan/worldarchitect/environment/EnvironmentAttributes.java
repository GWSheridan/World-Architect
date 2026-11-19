package com.gwsheridan.worldarchitect.environment;

import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import static net.minecraft.world.attribute.EnvironmentAttributes.*;

public final class EnvironmentAttributes {
    private EnvironmentAttributes() {}

    public static Vector3fc color(int rgb) {
        return new Vector3f((rgb >> 16 & 255) / 255F, (rgb >> 8 & 255) / 255F, (rgb & 255) / 255F);
    }

    public static void addLayers(EnvironmentAttributeSystem.Builder builder, Level level) {
        builder.addTimeBasedLayer(FOG_COLOR, (base, tick) -> WorldEnvironment.profile(level).visual().fogColor().map(EnvironmentAttributes::color).orElse(base));
        builder.addTimeBasedLayer(FOG_START_DISTANCE, (base, tick) -> WorldEnvironment.profile(level).visual().fog().map(EnvironmentProfile.Fog::start).orElse(base));
        builder.addTimeBasedLayer(FOG_END_DISTANCE, (base, tick) -> WorldEnvironment.profile(level).visual().fog().map(EnvironmentProfile.Fog::end).orElse(base));
        builder.addTimeBasedLayer(SKY_COLOR, (base, tick) -> WorldEnvironment.profile(level).visual().skyColor().map(EnvironmentAttributes::color).orElse(base));
        builder.addTimeBasedLayer(CLOUD_HEIGHT, (base, tick) -> WorldEnvironment.profile(level).visual().cloudHeight().orElse(base));
        builder.addTimeBasedLayer(CLOUD_COLOR, (base, tick) -> {
            var visual = WorldEnvironment.profile(level).visual();
            if (visual.clouds() == EnvironmentProfile.Clouds.HIDDEN) return new Vector4f(0, 0, 0, 0);
            if (visual.clouds() == EnvironmentProfile.Clouds.SHOWN || visual.cloudColor().isPresent()) {
                Vector3fc rgb = color(visual.cloudColor().orElse(0xffffff));
                return new Vector4f(rgb, visual.clouds() == EnvironmentProfile.Clouds.SHOWN ? 1 : base.w());
            }
            return base;
        });
    }
}
