package com.gwsheridan.worldsunwritten.spawn;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

public record SpawnPolicy(Kind kind, Identifier dimension, BlockPos position, Optional<Identifier> template,
        String module, String marker, Rotation rotation, Mirror mirror) {
    public static final Codec<SpawnPolicy> CODEC = RecordCodecBuilder.<SpawnPolicy>create(builder -> builder.group(
            StringRepresentable.fromEnum(Kind::values).fieldOf("kind").forGetter(SpawnPolicy::kind),
            Identifier.CODEC.fieldOf("dimension").forGetter(SpawnPolicy::dimension),
            BlockPos.CODEC.fieldOf("position").forGetter(SpawnPolicy::position),
            Identifier.CODEC.optionalFieldOf("template").forGetter(SpawnPolicy::template),
            Codec.STRING.optionalFieldOf("module", "").forGetter(SpawnPolicy::module),
            Codec.STRING.optionalFieldOf("marker", "").forGetter(SpawnPolicy::marker),
            Rotation.CODEC.optionalFieldOf("rotation", Rotation.NONE).forGetter(SpawnPolicy::rotation),
            Mirror.CODEC.optionalFieldOf("mirror", Mirror.NONE).forGetter(SpawnPolicy::mirror)
    ).apply(builder, SpawnPolicy::new)).validate(policy -> {
        if (Math.abs((long) policy.position.getX()) >= 29_999_984 || Math.abs((long) policy.position.getZ()) >= 29_999_984
                || policy.position.getY() < -2032 || policy.position.getY() > 2031) {
            return DataResult.error(() -> "The spawn position is outside Minecraft's build bounds.");
        }
        if ((policy.kind == Kind.MARKER || policy.kind == Kind.GENERATED)
                && (policy.template.isEmpty() || !policy.marker.matches("[a-z0-9_-]{1,32}")
                || policy.kind == Kind.GENERATED && !policy.module.matches("[a-z0-9_-]{1,32}"))) {
            return DataResult.error(() -> "Choose a template and a valid marker or module name.");
        }
        return DataResult.success(policy);
    });

    public SpawnPolicy {
        position = position.immutable();
    }

    public static SpawnPolicy simple(Kind kind, Identifier dimension, BlockPos position) {
        return new SpawnPolicy(kind, dimension, position, Optional.empty(), "", "", Rotation.NONE, Mirror.NONE);
    }

    public enum Kind implements StringRepresentable {
        FIXED, ISLAND, TEAM, MARKER, GENERATED;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
