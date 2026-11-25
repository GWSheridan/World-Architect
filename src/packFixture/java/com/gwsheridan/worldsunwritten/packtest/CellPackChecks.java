package com.gwsheridan.worldsunwritten.packtest;

import com.gwsheridan.worldsunwritten.api.CellLayout.Cell;
import com.gwsheridan.worldsunwritten.cells.*;
import com.gwsheridan.worldsunwritten.worldgen.CellGenerator;
import com.gwsheridan.worldsunwritten.worldgen.CellGenerator.Key;

import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/** Progression checks run only in the test kit's disposable world. */
final class CellPackChecks {
    @FunctionalInterface
    interface Check {
        void run() throws Exception;
    }

    private static final Map<MinecraftServer, Check> PENDING = new WeakHashMap<>();

    private static List<Key> keys(CellGenerator generator) {
        var cells = new LinkedHashSet<Cell>();
        cells.add(Cell.ORIGIN);
        if (generator.cells().progressive())
            cells.add(
                    generator.campaignEnd()
                            ? generator
                                    .cells()
                                    .layout()
                                    .within(generator.cells().growth().radius())
                                    .stream()
                                    .filter(c -> !generator.alwaysOpenCells().contains(c))
                                    .findFirst()
                                    .orElseThrow()
                            : new Cell(1, 0));
        for (var site : generator.cells().campaign().orElse(CellCampaign.EMPTY).sites())
            generator.cells().layout().within(generator.cells().growth().radius()).stream()
                    .filter(
                            c ->
                                    generator
                                            .cells()
                                            .tile(generator.seed(), 0, c)
                                            .id()
                                            .equals(site.type()))
                    .findFirst()
                    .ifPresent(cells::add);
        return cells.stream().map(c -> new Key(0, c)).toList();
    }

    private static boolean ready(MinecraftServer server) {
        boolean waiting = false;
        for (var level : server.getAllLevels())
            if (level.getChunkSource().getGenerator() instanceof CellGenerator generator
                    && generator.cells().progressive()) {
                var state = CellWorlds.state(level);
                for (var key : keys(generator)) {
                    var status = state.ledger.status(key);
                    require(
                            status != CellLedger.Status.CONFLICT,
                            "Cell activation preserved an unexpected block");
                    waiting |= status != CellLedger.Status.READY;
                }
                for (var link :
                        generator.cells().campaign().orElse(CellCampaign.EMPTY).connections())
                    if (state.ledger.status(new Key(0, link.from())) == CellLedger.Status.READY
                            && state.ledger.status(new Key(0, link.to()))
                                    == CellLedger.Status.READY) {
                        var row = state.ledger.connections().get("0:" + link.id());
                        require(
                                row == null || row.status() != CellLedger.Status.CONFLICT,
                                "Connection conflict in the disposable campaign");
                        waiting |= row == null || row.status() != CellLedger.Status.READY;
                    }
            }
        return !waiting;
    }

    static void request(MinecraftServer server, int stage, Check check) throws Exception {
        if (PENDING.containsKey(server))
            throw new IllegalStateException("A cell test is already running.");
        for (var level : server.getAllLevels())
            if (level.getChunkSource().getGenerator() instanceof CellGenerator generator
                    && generator.cells().progressive()) {
                var state = CellWorlds.state(level);
                require(state != null, "Missing saved cell journal");
                for (var key : keys(generator))
                    if (stage == 1 && state.ledger.status(key) == null) {
                        var centre = generator.cells().layout().centre(key.cell());
                        var pos =
                                new net.minecraft.core.BlockPos(
                                        (int) centre.x(),
                                        generator.getMinY() + 1,
                                        (int) centre.z());
                        require(level.getBlockState(pos).isAir(), "Reserved cell is not void");
                        state.ledger.queue(key);
                    }
            }
        if (ready(server)) check.run();
        else PENDING.put(server, check);
    }

    static void tick(MinecraftServer server) {
        var check = PENDING.get(server);
        if (check == null) return;
        try {
            if (!ready(server)) return;
            PENDING.remove(server);
            check.run();
        } catch (Exception error) {
            PENDING.remove(server);
            error.printStackTrace();
            System.err.println("WA_PACK_TEST_FAILED " + error.getMessage());
        }
    }

    static int[] audit(MinecraftServer server, int stage, CompoundTag consumed) throws Exception {
        int markers = 0, blocks = 0, containers = 0;
        for (var level : server.getAllLevels())
            if (level.getChunkSource().getGenerator() instanceof CellGenerator generator) {
                generator.validateAssets(server.registryAccess());
                ProgressionGraph.layers(generator.cells().growth());
                var connected =
                        new HashMap<
                                net.minecraft.core.BlockPos,
                                net.minecraft.world.level.block.state.BlockState>();
                var state = CellWorlds.state(level);
                if (state != null) {
                    require(
                            state.ledger.pendingDeliveries().isEmpty(),
                            "A fresh pack has an unresolved delivery");
                    for (var link :
                            generator.cells().campaign().orElse(CellCampaign.EMPTY).connections()) {
                        var row = state.ledger.connections().get("0:" + link.id());
                        if (row != null && row.status() == CellLedger.Status.READY) {
                            var plan =
                                    CellConnectionPlan.create(
                                            generator, link, 0, level, level.getSeed());
                            require(plan.problem().isEmpty(), plan.problem());
                            connected.putAll(plan.blocks());
                            for (var e : plan.blocks().entrySet())
                                require(
                                        level.getBlockState(e.getKey()) == e.getValue(),
                                        "A saved connection changed");
                        }
                    }
                }
                for (var delivery :
                        generator.cells().campaign().orElse(CellCampaign.EMPTY).deliveries()) {
                    var item =
                            net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                                    delivery.item());
                    require(
                            item != null && item != net.minecraft.world.item.Items.AIR,
                            "Missing delivery item");
                    require(
                            delivery.count() <= 36 * new ItemStack(item).getMaxStackSize(),
                            "A delivery exceeds the normal main-inventory capacity");
                }
                if (generator.campaignEnd()) {
                    require(level.getDragonFight() != null, "Native fight controller missing");
                    require(
                            generator.alwaysOpenCells().stream()
                                    .allMatch(
                                            c ->
                                                    state.ledger.status(new Key(0, c))
                                                            == CellLedger.Status.READY),
                            "Native arena was locked");
                    for (var landing : CellEndReservations.arrivalsByGateway().values()) {
                        require(
                                level.getBlockState(landing)
                                        .is(net.minecraft.world.level.block.Blocks.END_STONE),
                                "Gateway landing floor missing");
                        require(
                                !CellWorlds.locked(level, landing.above()),
                                "A gateway landing became a reward cell");
                    }
                }
                if (generator.cells().layout().gap() > 0) {
                    var sampleY = new TreeSet<Integer>();
                    sampleY.add(level.getMinY() + 1);
                    for (var tile : generator.cells().tiles()) {
                        sampleY.add(tile.terrain().elevation());
                        sampleY.add(tile.terrain().elevation() + 3);
                        tile.content().sea().ifPresent(sea -> sampleY.add(sea.level()));
                    }
                    boolean checkedFluid = false;
                    for (int x = -48; x <= 48; x++)
                        for (int z = -48; z <= 48; z++) {
                            if (generator.endProgression().isPresent()
                                    && com.gwsheridan.worldsunwritten.config.EndProgression
                                            .contains(x, z)) continue;
                            var address = generator.cells().address(x, z);
                            if (address == null
                                    || generator
                                            .cells()
                                            .layout()
                                            .contains(
                                                    address.cell(),
                                                    address.localX(),
                                                    address.localZ())) continue;
                            for (int y : sampleY)
                                if (level.isInsideBuildHeight(y))
                                    require(
                                            level.getBlockState(
                                                            new net.minecraft.core.BlockPos(
                                                                    x, y, z))
                                                    == connected.getOrDefault(
                                                            new net.minecraft.core.BlockPos(
                                                                    x, y, z),
                                                            net.minecraft.world.level.block.Blocks
                                                                    .AIR
                                                                    .defaultBlockState()),
                                            "Cell terrain or fluid crossed an unreserved void gap");
                            if (!checkedFluid) {
                                require(
                                        !level.setBlock(
                                                new net.minecraft.core.BlockPos(x, 70, z),
                                                net.minecraft.world.level.block.Blocks.WATER
                                                        .defaultBlockState(),
                                                2),
                                        "Fluid write filled a reserved gap");
                                checkedFluid = true;
                            }
                        }
                }
                if (generator.endProgression().isPresent())
                    continue; // The native arena owns the origin, including its spawn.
                var keys = keys(generator);
                for (var key : keys) {
                    var tile =
                            generator.cells().tile(generator.seed(), key.territory(), key.cell());
                    var marker = generator.spawn(key, level);
                    require(
                            com.gwsheridan.worldsunwritten.spawn.SafeSpawns.safe(level, marker),
                            "Unsafe cell spawn marker");
                    markers++;
                    var build = generator.build(tile, key);
                    if (build.isPresent()) {
                        var origin = generator.origin(tile, key);
                        for (var saved : build.orElseThrow().markers().values())
                            require(
                                    com.gwsheridan.worldsunwritten.spawn.SafeSpawns.safe(
                                            level, origin.offset(saved)),
                                    "A saved site marker is unsafe");
                        for (var cell : build.orElseThrow().cells()) {
                            blocks++;
                            if (cell.data().isEmpty()) continue;
                            var pos = origin.offset(cell.pos());
                            var entity = level.getBlockEntity(pos);
                            require(entity != null, "Cell template metadata is missing");
                            if (entity instanceof Container container) {
                                containers++;
                                for (var value :
                                        cell.data().orElseThrow().getListOrEmpty("Items")) {
                                    var item = (CompoundTag) value;
                                    int slot = item.getIntOr("Slot", -1);
                                    var expected =
                                            ItemStack.CODEC
                                                    .parse(
                                                            net.minecraft.resources.RegistryOps
                                                                    .create(
                                                                            NbtOps.INSTANCE,
                                                                            server
                                                                                    .registryAccess()),
                                                            item)
                                                    .getOrThrow();
                                    String id =
                                            level.dimension().identifier()
                                                    + "/"
                                                    + pos.toShortString()
                                                    + "/"
                                                    + slot;
                                    var actual = container.getItem(slot);
                                    int count =
                                            expected.getCount() - (consumed.contains(id) ? 1 : 0);
                                    require(
                                            actual.getCount() == count
                                                    && (count == 0
                                                            || ItemStack.isSameItemSameComponents(
                                                                    expected, actual)),
                                            "Cell starter inventory changed or refilled");
                                    if (stage == 1) {
                                        container.removeItem(slot, 1);
                                        entity.setChanged();
                                        consumed.putBoolean(id, true);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        return new int[] {markers, blocks, containers};
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
