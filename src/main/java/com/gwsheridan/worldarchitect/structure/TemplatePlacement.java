package com.gwsheridan.worldarchitect.structure;

import com.gwsheridan.worldarchitect.config.IslandTemplate;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

record TemplatePlacement(BlockPos origin, Rotation rotation, Mirror mirror) {
    private static final int UPDATE = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    BlockPos position(BlockPos size, BlockPos local) {
        BlockPos zero = StructureTemplate.getZeroPositionWithTransform(origin, mirror, rotation, size.getX(), size.getZ());
        return zero.offset(StructureTemplate.transform(local, mirror, rotation, BlockPos.ZERO));
    }

    Direction facing(Direction direction) {
        return rotation.rotate(mirror.mirror(direction));
    }

    BlockPos size(BlockPos original) {
        return rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90
                ? new BlockPos(original.getZ(), original.getY(), original.getX()) : original;
    }

    BlockState state(BlockState state) {
        return state.mirror(mirror).rotate(rotation);
    }

    int place(ServerLevel level, LibraryEntry entry, @Nullable Entity actor) throws IOException {
        var plan = plan(level, entry, actor);
        int result = apply(level, plan);
        if (actor instanceof ServerPlayer player && !plan.isEmpty()) {
            try { PlacementHistory.record(level, player, plan); }
            catch (IOException | RuntimeException error) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Placement completed, but its undo snapshot could not be recorded."));
                com.mojang.logging.LogUtils.getLogger().error("Cannot record manual placement history", error);
            }
        }
        return result;
    }

    List<Change> plan(ServerLevel level, LibraryEntry entry, @Nullable Entity actor) throws IOException {
        if (!level.isInWorldBounds(origin)) {
            throw new IOException("Placement origin is outside the dimension's build bounds.");
        }
        var rules = entry.rules();
        if (rules.dimension().isPresent() && !rules.dimension().orElseThrow().equals(level.dimension().identifier())) {
            throw new IOException("This template requires dimension " + rules.dimension().orElseThrow());
        }
        BlockPos size = entry.template().size();
        var plan = new ArrayList<Change>();
        Map<BlockPos, BlockPos> bottom = new HashMap<>();
        for (var cell : entry.template().blocks()) {
            BlockPos pos = position(size, cell.pos());
            requireAvailable(level, pos);
            if (pos.getY() < rules.minY() || pos.getY() > rules.maxY()) {
                throw new IOException("A placement cell falls outside the template's allowed height range at " + pos.toShortString());
            }
            if (actor != null && !level.mayInteract(actor, pos)) {
                throw new IOException("Placement is protected at " + pos.toShortString());
            }
            if (actor instanceof ServerPlayer player && com.gwsheridan.worldarchitect.spawn.WorldAdministration.get(level.getServer()).protection().placement()
                    && com.gwsheridan.worldarchitect.skyblock.IslandProtection.denied(player, pos)) {
                throw new IOException("Island protection denies placement at " + pos.toShortString());
            }
            if (rules.biome().isPresent() && !level.getBiome(pos).unwrapKey()
                    .filter(key -> key.identifier().equals(rules.biome().orElseThrow())).isPresent()) {
                throw new IOException("This template requires biome " + rules.biome().orElseThrow() + " throughout its occupied cells.");
            }
            if (rules.terrain() == TemplateRules.Terrain.BURIED
                    && pos.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()) - 1) {
                throw new IOException("This template needs an underground cavity with terrain above " + pos.toShortString());
            }
            if (rules.terrain() == TemplateRules.Terrain.SUPPORTED) {
                bottom.merge(new BlockPos(pos.getX(), 0, pos.getZ()), pos, (first, second) -> first.getY() < second.getY() ? first : second);
            }
            BlockState state = state(cell.state());
            BlockState existing = level.getBlockState(pos);
            if (!existing.isAir()) {
                if (rules.collision() == TemplateRules.Collision.MATCHING && existing == state && !existing.hasBlockEntity()) {
                    continue;
                }
                if(!rules.collision().allows(existing,state))throw new IOException("Placement collides with " + existing.getBlock().getName().getString() + " at " + pos.toShortString());
            }
            plan.add(new Change(pos, existing, state, cell.metadata()));
        }
        for (BlockPos floor : bottom.values()) {
            if (!level.isInsideBuildHeight(floor.below()) || !IslandTemplate.safeFloor(level.getBlockState(floor.below()), level, floor.below())) {
                throw new IOException("Placement requires a safe full block below " + floor.toShortString());
            }
        }
        for (var marker : entry.markers().values()) {
            BlockPos pos = position(size, marker.pos());
            requireAvailable(level, pos);
            requireAvailable(level, pos.above());
            if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) {
                throw new IOException("Placement would obstruct a saved spawn marker at " + pos.toShortString());
            }
        }
        BlockPos transformedSize = size(size);
        AABB volume = new AABB(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + transformedSize.getX(), origin.getY() + transformedSize.getY(), origin.getZ() + transformedSize.getZ());
        if (!plan.isEmpty() && !level.getEntitiesOfClass(LivingEntity.class, volume, living -> !living.isSpectator()).isEmpty()) {
            throw new IOException("A player or mob occupies the placement volume.");
        }
        return List.copyOf(plan);
    }

    void spawn(ServerLevel level, ServerPlayer player, LibraryEntry entry, String markerName) throws IOException {
        var marker = entry.markers().get(markerName);
        if (marker == null) {
            throw new IOException("Unknown spawn marker " + markerName);
        }
        BlockPos pos = position(entry.template().size(), marker.pos());
        requireAvailable(level, pos);
        requireAvailable(level, pos.above());
        requireAvailable(level, pos.below());
        if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()
                || !IslandTemplate.safeFloor(level.getBlockState(pos.below()), level, pos.below())) {
            throw new IOException("The transformed spawn marker is unsafe at " + pos.toShortString());
        }
        Vec3 center = Vec3.atBottomCenterOf(pos);
        if (!player.teleportTo(level, center.x, center.y, center.z, Set.of(), facing(marker.facing()).toYRot(), 0.0F, true)) {
            throw new IOException("The server refused travel to the spawn marker.");
        }
    }

    static void requireAvailable(ServerLevel level, BlockPos pos) throws IOException {
        if (!level.isInWorldBounds(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
            throw new IOException("Placement falls outside the build bounds or world border at " + pos.toShortString());
        }
        if (!level.hasChunkAt(pos)) {
            throw new IOException("Load the destination chunk before using the template at " + pos.toShortString());
        }
    }

    private static int apply(ServerLevel level, List<Change> plan) throws IOException {
        int touched = 0;
        try {
            for (Change change : plan) {
                touched++;
                if (!level.setBlock(change.pos, change.next, UPDATE) || level.getBlockState(change.pos) != change.next) {
                    throw new IOException("The server refused to place a block at " + change.pos.toShortString());
                }
            }
            for (Change change : plan) {
                if (change.metadata != null) {
                    var entity = level.getBlockEntity(change.pos);
                    if (entity == null) {
                        throw new IOException("Missing block entity at " + change.pos.toShortString());
                    }
                    entity.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), change.metadata));
                    entity.setChanged();
                    level.sendBlockUpdated(change.pos, change.next, change.next, Block.UPDATE_CLIENTS);
                }
            }
        } catch (IOException | RuntimeException e) {
            for (int index = touched - 1; index >= 0; index--) {
                Change change = plan.get(index);
                if (level.getBlockState(change.pos) != change.previous) {
                    level.setBlock(change.pos, change.previous, UPDATE | Block.UPDATE_SUPPRESS_DROPS);
                }
            }
            throw e;
        }
        return plan.size();
    }

    record Change(BlockPos pos, BlockState previous, BlockState next, @Nullable CompoundTag metadata) {}
}
