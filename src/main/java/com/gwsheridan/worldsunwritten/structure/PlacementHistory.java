package com.gwsheridan.worldsunwritten.structure;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Only manual library placements enter this bounded, session-local journal. */
final class PlacementHistory {
    static final long MAX_BYTES = 32L * 1024 * 1024;
    static final int MAX_PER_AUTHOR = 10;
    private static final Map<MinecraftServer, Deque<Operation>> HISTORY = new WeakHashMap<>();
    private record Cell(BlockPos pos, BlockState before, BlockState after, CompoundTag metadata) {}
    private record Operation(UUID author, ResourceKey<Level> dimension, List<Cell> cells, long bytes) {}
    private PlacementHistory() {}

    static void record(ServerLevel level, ServerPlayer author, List<TemplatePlacement.Change> changes) throws IOException {
        var cells = new ArrayList<Cell>(); long bytes = 128;
        for (var change : changes) {
            var tag = metadata(level, change.pos());
            bytes += 256;
            if (tag != null) {
                var output = new ByteArrayOutputStream(); NbtIo.write(tag, new java.io.DataOutputStream(output)); bytes += output.size() * 4L;
            }
            cells.add(new Cell(change.pos().immutable(), change.previous(), change.next(), tag));
        }
        if (bytes > MAX_BYTES) { author.sendSystemMessage(net.minecraft.network.chat.Component.literal("Placement completed; it exceeds the undo memory budget.")); return; }
        var history = HISTORY.computeIfAbsent(level.getServer(), unused -> new ArrayDeque<>());
        while (history.stream().filter(op -> op.author.equals(author.getUUID())).count() >= MAX_PER_AUTHOR) {
            var oldest = history.stream().filter(op -> op.author.equals(author.getUUID())).findFirst().orElseThrow(); history.remove(oldest);
        }
        long needed = bytes;
        while (!history.isEmpty() && history.stream().mapToLong(Operation::bytes).sum() + needed > MAX_BYTES) history.removeFirst();
        history.addLast(new Operation(author.getUUID(), level.dimension(), List.copyOf(cells), bytes));
    }

    static int undo(ServerPlayer player) throws IOException {
        var history = HISTORY.get(player.level().getServer());
        var op = history == null ? null : history.reversed().stream().filter(item -> item.author.equals(player.getUUID())).findFirst().orElse(null);
        if (op == null) throw new IOException("No manual placement remains in your session undo history.");
        if (!op.dimension.equals(player.level().dimension())) throw new IOException("Return to " + op.dimension.identifier() + " to undo that placement.");
        var level = player.level();
        for (var cell : op.cells) {
            TemplatePlacement.requireAvailable(level, cell.pos);
            if (!level.mayInteract(player, cell.pos) || com.gwsheridan.worldsunwritten.skyblock.IslandProtection.denied(player, cell.pos))
                throw new IOException("Undo is protected at " + cell.pos.toShortString());
            if (level.getBlockState(cell.pos) != cell.after || !Objects.equals(metadata(level, cell.pos), cell.metadata))
                throw new IOException("Undo refused: a block or block entity changed at " + cell.pos.toShortString());
            if (!level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(cell.pos), e -> !e.isSpectator()).isEmpty())
                throw new IOException("Move players and mobs clear before undoing this placement.");
        }
        int restored = 0;
        try {
            for (var cell : op.cells.reversed()) {
                if (!level.setBlock(cell.pos, cell.before, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS)
                        || level.getBlockState(cell.pos) != cell.before) throw new IOException("The server refused undo at " + cell.pos.toShortString());
                restored++;
            }
        } catch (IOException | RuntimeException error) {
            // Restore the exact unchanged placement if an undo write fails. Never restore an inventory.
            for (var cell : op.cells.reversed().subList(0, restored)) {
                level.setBlock(cell.pos, cell.after, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
                if (cell.metadata != null && level.getBlockEntity(cell.pos) != null) {
                    level.getBlockEntity(cell.pos).loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(
                            net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), cell.metadata));
                    level.getBlockEntity(cell.pos).setChanged();
                }
            }
            throw error;
        }
        history.remove(op); return restored;
    }

    private static CompoundTag metadata(ServerLevel level, BlockPos pos) {
        var entity = level.getBlockEntity(pos); return entity == null ? null : entity.saveWithoutMetadata(level.registryAccess()).copy();
    }
}
