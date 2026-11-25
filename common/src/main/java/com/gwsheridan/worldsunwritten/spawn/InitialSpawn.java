package com.gwsheridan.worldsunwritten.spawn;

import com.gwsheridan.worldsunwritten.api.CellLayout;
import com.gwsheridan.worldsunwritten.worldgen.CellGenerator;
import com.gwsheridan.worldsunwritten.worldgen.SolidColumnGenerator;
import com.gwsheridan.worldsunwritten.worldgen.VoidGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.ServerLevelData;

public final class InitialSpawn {
    private InitialSpawn() {}

    public static boolean apply(ServerLevel level, ServerLevelData settings) {
        BlockPos position = switch (level.getChunkSource().getGenerator()) {
            case CellGenerator cells -> SafeSpawns.nearby(level, cells.spawn(new CellGenerator.Key(0, CellLayout.Cell.ORIGIN), level))
                    .orElseThrow(() -> new IllegalStateException("The starting cell needs a safe floor and two blocks of headroom. Add or repair its spawn marker."));
            case SolidColumnGenerator terrain -> terrain.initialSpawn(level, level.getChunkSource().randomState().seed())
                    .orElseThrow(() -> new IllegalStateException("Worlds Unwritten could not find a safe starting island near the origin. Choose a different seed, sea level, or island material."));
            case VoidGenerator empty -> empty.layout().map(island -> island.spawn(0))
                    .orElseGet(() -> new BlockPos(8, level.getMinY() + 4, 8));
            default -> null;
        };
        if (position == null) return false;
        settings.setSpawn(LevelData.RespawnData.of(level.dimension(), position, 0.0F, 0.0F));
        return true;
    }
}
