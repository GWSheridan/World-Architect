package com.gwsheridan.worldsunwritten.cells;

import com.gwsheridan.worldsunwritten.worldgen.CellGenerator;
import com.gwsheridan.worldsunwritten.worldgen.CellGenerator.Key;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.*;

import java.io.IOException;
import java.util.*;

/**
 * Bounded two-pass activation. No live block changes until the whole reserved cell has been
 * checked.
 */
final class CellActivation {
    private final ServerLevel level;
    private final CellGenerator generator;
    private final CellLedger ledger;
    private final Key key;
    private final List<ChunkPos> chunks = new ArrayList<>();
    private final boolean recovering;
    private boolean applying;
    private int chunkIndex, columnIndex, y;
    private ProtoChunk plan;
    private LevelChunk live;
    private String conflict = "";

    CellActivation(ServerLevel level, CellGenerator generator, CellLedger ledger, Key key) {
        this.level = level;
        this.generator = generator;
        this.ledger = ledger;
        this.key = key;
        recovering = ledger.status(key) == CellLedger.Status.APPLYING;
        var b = generator.cells().layout().bounds(key.cell());
        var offset = generator.cells().offset(key.territory());
        for (int z = Math.floorDiv(b.minZ() + offset.getZ(), 16);
                z <= Math.floorDiv(b.maxZ() - 1 + offset.getZ(), 16);
                z++)
            for (int x = Math.floorDiv(b.minX() + offset.getX(), 16);
                    x <= Math.floorDiv(b.maxX() - 1 + offset.getX(), 16);
                    x++) chunks.add(new ChunkPos(x, z));
        y = level.getMinY();
    }

    Key key() {
        return key;
    }

    String conflict() {
        return conflict;
    }

    boolean tick(int budget) throws IOException {
        if (plan != null) {
            var cp = chunks.get(chunkIndex);
            live = level.getChunk(cp.x(), cp.z());
        }
        var b = generator.cells().layout().bounds(key.cell());
        var offset = generator.cells().offset(key.territory());
        var area =
                new net.minecraft.world.phys.AABB(
                        b.minX() + offset.getX(),
                        level.getMinY(),
                        b.minZ() + offset.getZ(),
                        b.maxX() + offset.getX(),
                        level.getMaxY() + 1,
                        b.maxZ() + offset.getZ());
        for (var entity : level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, area))
            if (!(entity instanceof net.minecraft.server.level.ServerPlayer)) {
                var box = entity.getBoundingBox();
                boolean overlaps =
                        generator.contains(
                                key, entity.blockPosition().getX(), entity.blockPosition().getZ());
                for (double x : new double[] {box.minX, box.maxX - 1.0e-7})
                    for (double z : new double[] {box.minZ, box.maxZ - 1.0e-7})
                        overlaps |=
                                generator.contains(
                                        key, (int) StrictMath.floor(x), (int) StrictMath.floor(z));
                if (overlaps || box.getXsize() > 8 || box.getZsize() > 8)
                    return conflict(
                            "An entity occupies the reserved cell at "
                                    + entity.blockPosition().toShortString());
            }
        var position = new BlockPos.MutableBlockPos();
        while (budget-- > 0) {
            if (chunkIndex >= chunks.size()) {
                if (!applying) {
                    if (!recovering)
                        ledger.transition(
                                key, CellLedger.Status.QUEUED, CellLedger.Status.APPLYING);
                    applying = true;
                    chunkIndex = 0;
                    columnIndex = 0;
                    y = level.getMinY();
                    plan = null;
                    continue;
                }
                finish();
                return true;
            }
            if (plan == null) {
                var cp = chunks.get(chunkIndex);
                live = level.getChunk(cp.x(), cp.z());
                plan =
                        new ProtoChunk(
                                cp,
                                UpgradeData.EMPTY,
                                level,
                                level.palettedContainerFactory(),
                                null);
                generator.fill(plan, generator.seed(), key);
            }
            var cp = chunks.get(chunkIndex);
            int x = cp.getMinBlockX() + (columnIndex & 15),
                    z = cp.getMinBlockZ() + (columnIndex >> 4);
            if (!generator.contains(key, x, z)
                    || generator.campaignEnd() && generator.nativeReservation(x, z)) {
                advanceColumn();
                continue;
            }
            position.set(x, y, z);
            var expected = plan.getBlockState(position);
            var actual = live.getBlockState(position);
            if (!applying) {
                if (!actual.isAir() && (!recovering || actual != expected))
                    return conflict("Unexpected block at " + position.toShortString());
            } else if (actual != expected) {
                if (!actual.isAir())
                    return conflict(
                            "A block changed during activation at " + position.toShortString());
                CellWorlds.write(
                        level,
                        key,
                        position,
                        expected,
                        () -> level.setBlock(position, expected, Block.UPDATE_CLIENTS));
                if (level.getBlockState(position) != expected)
                    return conflict(
                            "A block placement was rejected at " + position.toShortString());
                if (!expected.getFluidState().isEmpty())
                    level.scheduleTick(
                            position.immutable(),
                            expected.getFluidState().getType(),
                            expected.getFluidState().getType().getTickDelay(level));
            }
            if (++y > level.getMaxY()) advanceColumn();
        }
        return false;
    }

    private void advanceColumn() {
        y = level.getMinY();
        if (++columnIndex == 256) {
            columnIndex = 0;
            chunkIndex++;
            plan = null;
            live = null;
        }
    }

    private boolean conflict(String message) throws IOException {
        conflict = message;
        ledger.transition(key, ledger.status(key), CellLedger.Status.CONFLICT);
        return true;
    }

    private void finish() throws IOException {
        // Locked cells cannot be opened or ticked. Starter inventories are installed once, before
        // the flush.
        var tile = generator.cells().tile(generator.seed(), key.territory(), key.cell());
        var build = generator.build(tile, key);
        if (build.isPresent()) {
            var origin = generator.origin(tile, key);
            for (var cell : build.orElseThrow().cells())
                if (cell.data().isPresent()) {
                    var pos = origin.offset(cell.pos());
                    var tag = cell.data().orElseThrow();
                    tag.putInt("x", pos.getX());
                    tag.putInt("y", pos.getY());
                    tag.putInt("z", pos.getZ());
                    if (level.getBlockEntity(pos) instanceof net.minecraft.world.Container container
                            && !container.isEmpty()) {
                        var expectedItems =
                                new HashMap<Integer, net.minecraft.world.item.ItemStack>();
                        for (var value : tag.getListOrEmpty("Items")) {
                            var item = (net.minecraft.nbt.CompoundTag) value;
                            expectedItems.put(
                                    item.getIntOr("Slot", -1),
                                    net.minecraft.world.item.ItemStack.CODEC
                                            .parse(
                                                    net.minecraft.resources.RegistryOps.create(
                                                            net.minecraft.nbt.NbtOps.INSTANCE,
                                                            level.registryAccess()),
                                                    item)
                                            .getOrThrow());
                        }
                        boolean matches = recovering;
                        for (int i = 0; i < container.getContainerSize(); i++) {
                            var expected =
                                    expectedItems.getOrDefault(
                                            i, net.minecraft.world.item.ItemStack.EMPTY);
                            var actual = container.getItem(i);
                            matches &=
                                    actual.getCount() == expected.getCount()
                                            && (actual.isEmpty() && expected.isEmpty()
                                                    || net.minecraft.world.item.ItemStack
                                                            .isSameItemSameComponents(
                                                                    actual, expected));
                        }
                        if (!matches) {
                            conflict("Unexpected inventory contents at " + pos.toShortString());
                            return;
                        }
                    }
                    var entity =
                            BlockEntity.loadStatic(pos, cell.state(), tag, level.registryAccess());
                    if (entity == null)
                        throw new IOException(
                                "Cannot load saved template metadata at " + pos.toShortString());
                    level.setBlockEntity(entity);
                    entity.setChanged();
                }
        }
        // Commit READY only after chunk data and inventories have reached disk. A restart rechecks
        // APPLYING.
        if (!level.getServer().saveEverything(true, true, true))
            throw new IOException("The server could not flush activated terrain.");
        ledger.transition(key, CellLedger.Status.APPLYING, CellLedger.Status.READY);
        generator.active(ledger.ready());
    }
}
