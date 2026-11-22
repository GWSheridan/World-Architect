package com.gwsheridan.worldsunwritten.structure;

import com.gwsheridan.worldsunwritten.spawn.SafeSpawns;
import com.gwsheridan.worldsunwritten.spawn.SpawnPolicy;
import java.io.IOException;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

public final class StructureSpawns {
    private StructureSpawns() {}

    public static Optional<SafeSpawns.Target> resolve(ServerLevel level, SpawnPolicy policy) throws IOException {
        if (policy.kind() == SpawnPolicy.Kind.MARKER) {
            LibraryEntry entry = StructureLibrary.load(level.getServer(), policy.template().orElseThrow());
            return marker(level, entry, policy.marker(), new TemplatePlacement(policy.position(), policy.rotation(), policy.mirror()));
        }
        var structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(policy.template().orElseThrow());
        if (!(structure instanceof ModularStructure)) {
            throw new IOException("Choose a registered modular structure for the generated spawn.");
        }
        // Resolve its saved start and pieces after the containing chunk has completed generation.
        level.getChunkAt(policy.position());
        var start = level.structureManager().getStructureAt(policy.position(), structure);
        if (!start.isValid()) {
            throw new IOException("No generated structure contains " + policy.position().toShortString());
        }
        var piece = start.getPieces().stream().filter(ModularPiece.class::isInstance).map(ModularPiece.class::cast)
                .filter(module -> module.name().equals(policy.module())).findFirst()
                .orElseThrow(() -> new IOException("Unknown generated module " + policy.module()));
        return marker(level, piece.entry(), policy.marker(), new TemplatePlacement(piece.origin(), Rotation.NONE, Mirror.NONE));
    }

    private static Optional<SafeSpawns.Target> marker(ServerLevel level, LibraryEntry entry, String name, TemplatePlacement placement) throws IOException {
        var marker = entry.markers().get(name);
        if (marker == null) {
            throw new IOException("Unknown structure marker " + name);
        }
        BlockPos position = placement.position(entry.template().size(), marker.pos());
        return SafeSpawns.nearby(level, position).map(pos -> new SafeSpawns.Target(level, pos, placement.facing(marker.facing()).toYRot()));
    }
}
