package com.gwsheridan.worldarchitect.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.dimension.DimensionType;

public record IslandProfile(IslandTemplate template, int originY, int spacing, int personalSlots) implements com.gwsheridan.worldarchitect.api.IslandLayout {
    public static final int MAX_SLOTS = 4095;
    private static final int COLUMNS = 16;
    public static final Codec<IslandProfile> CODEC = RecordCodecBuilder.<IslandProfile>create(builder -> builder.group(
            IslandTemplate.CODEC.fieldOf("template").forGetter(IslandProfile::template),
            Codec.intRange(DimensionType.MIN_Y, DimensionType.MAX_Y).fieldOf("origin_y").forGetter(IslandProfile::originY),
            Codec.intRange(64, 4096).optionalFieldOf("spacing", 128).forGetter(IslandProfile::spacing),
            Codec.intRange(0, MAX_SLOTS).optionalFieldOf("personal_slots", 0).forGetter(IslandProfile::personalSlots)
    ).apply(builder, IslandProfile::new)).validate(profile -> profile.spacing % 16 == 0 ? DataResult.success(profile)
            : DataResult.error(() -> "Island spacing must be a multiple of 16."));

    public int slotAt(int x, int z) {
        if (x < 0 || z < 0 || x / spacing >= COLUMNS || z / spacing > personalSlots / COLUMNS
                || x % spacing >= template.size().getX() || z % spacing >= template.size().getZ()) {
            return -1;
        }
        int slot = z / spacing * COLUMNS + x / spacing;
        return slot <= personalSlots ? slot : -1;
    }

    public BlockPos origin(int slot) {
        return new BlockPos(slot % COLUMNS * spacing, originY, slot / COLUMNS * spacing);
    }

    public BlockPos spawn(int slot) {
        return origin(slot).offset(template.spawn());
    }

    public java.util.List<BlockPos> floors(int slot) { return template.blocks().stream().map(c -> origin(slot).offset(c.pos())).toList(); }
}
