package com.gwsheridan.worldsunwritten.cells;

import com.gwsheridan.worldsunwritten.compatibility.SaveProtection;
import com.gwsheridan.worldsunwritten.config.IslandTemplate;
import com.gwsheridan.worldsunwritten.worldgen.CellGenerator;
import com.gwsheridan.worldsunwritten.worldgen.CellGenerator.Key;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.util.*;

/** Reserved corridors use the same durable check/apply/flush lifecycle as cell activation. */
final class CellConnections {
    private record Territory(
            List<CellConnectionPlan> plans, Map<BlockPos, List<CellConnectionPlan>> reserved) {}

    private final CellGenerator generator;
    private final CellLedger ledger;
    private final long seed;
    private final boolean enabled;
    private final Map<Integer, Territory> cached =
            new LinkedHashMap<>(16, .75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, Territory> entry) {
                    return size() > 16;
                }
            };
    private volatile Map<String, CellLedger.ConnectionState> states;
    private Job job;
    private int nextTerritory;
    private final Map<
                    Integer,
                    Map<com.gwsheridan.worldsunwritten.api.CellLayout.Cell, CellLedger.Status>>
            scanned = new HashMap<>();

    CellConnections(CellGenerator generator, CellLedger ledger, long seed) {
        this.generator = generator;
        this.ledger = ledger;
        this.seed = seed;
        states = ledger.connections();
        enabled = generator.cells().campaign().map(c -> !c.connections().isEmpty()).orElse(false);
        if (enabled) generator.connectionBlocks(this::blocks);
    }

    private synchronized Territory territory(int slot) {
        var found = cached.get(slot);
        if (found != null) return found;
        var plans = new ArrayList<CellConnectionPlan>();
        var positions = new HashMap<BlockPos, List<CellConnectionPlan>>();
        var height =
                net.minecraft.world.level.LevelHeightAccessor.create(
                        generator.getMinY(), generator.getGenDepth());
        for (var link : generator.cells().campaign().orElse(CellCampaign.EMPTY).connections()) {
            var plan = CellConnectionPlan.create(generator, link, slot, height, seed);
            plans.add(plan);
            if (!plan.problem().isEmpty()) continue;
            for (var p : plan.blocks().keySet()) {
                var owners = positions.computeIfAbsent(p, ignored -> new ArrayList<>());
                if (owners.stream()
                        .anyMatch(other -> other.blocks().get(p) != plan.blocks().get(p)))
                    throw new IllegalArgumentException(
                            "Connection reservations disagree at their junction; move their"
                                    + " ports.");
                owners.add(plan);
                if (positions.size() > CellConnectionPlan.MAX_POSITIONS)
                    throw new IllegalArgumentException(
                            "Combined connections exceed 65,536 positions per territory.");
            }
            var old = states.get(slot + ":" + plan.id());
            if (old != null && !old.fingerprint().equals(plan.fingerprint()))
                throw SaveProtection.incompatible(
                        "Saved connection " + plan.id() + " no longer matches its reserved plan.");
        }
        var immutable = new HashMap<BlockPos, List<CellConnectionPlan>>();
        positions.forEach((p, list) -> immutable.put(p, List.copyOf(list)));
        found = new Territory(List.copyOf(plans), Map.copyOf(immutable));
        cached.put(slot, found);
        return found;
    }

    private boolean ready(CellConnectionPlan plan) {
        var row = states.get(plan.territory() + ":" + plan.id());
        return row != null && row.status() == CellLedger.Status.READY;
    }

    boolean reserved(BlockPos pos) {
        if (!enabled) return false;
        var a = generator.cells().address(pos.getX(), pos.getZ());
        if (a == null) return false;
        return territory(a.territory()).reserved().containsKey(pos);
    }

    boolean open(BlockPos pos) {
        if (!enabled) return false;
        var a = generator.cells().address(pos.getX(), pos.getZ());
        if (a == null) return false;
        var owners = territory(a.territory()).reserved().get(pos);
        return owners != null && owners.stream().anyMatch(this::ready);
    }

    private Map<BlockPos, BlockState> blocks(ChunkPos chunk) {
        var result = new LinkedHashMap<BlockPos, BlockState>();
        var a = generator.cells().address(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ());
        if (a == null) return Map.of();
        for (var plan : territory(a.territory()).plans())
            if (ready(plan))
                plan.blocks()
                        .forEach(
                                (p, s) -> {
                                    if ((p.getX() >> 4) == chunk.x()
                                            && (p.getZ() >> 4) == chunk.z()) result.put(p, s);
                                });
        return result;
    }

    List<String> report(int slot) {
        var lines = new ArrayList<String>();
        for (var plan : territory(slot).plans()) {
            var state = states.get(slot + ":" + plan.id());
            lines.add(
                    plan.id()
                            + " · "
                            + (state == null
                                    ? "waiting for endpoints"
                                    : state.status().name().toLowerCase(Locale.ROOT))
                            + " · "
                            + plan.blocks().size()
                            + " positions"
                            + (plan.problem().isEmpty() ? "" : " · " + plan.problem())
                            + (state == null || state.problem().isEmpty()
                                    ? ""
                                    : " · " + state.problem()));
        }
        return List.copyOf(lines);
    }

    void retry(int slot, String id) throws IOException {
        var plan =
                territory(slot).plans().stream()
                        .filter(p -> p.id().equals(id))
                        .findFirst()
                        .orElseThrow();
        var old = states.get(slot + ":" + id);
        if (old == null || old.status() != CellLedger.Status.CONFLICT || !plan.problem().isEmpty())
            throw new IllegalArgumentException(
                    "Repair the saved connection conflict before retrying.");
        ledger.connection(
                slot,
                id,
                CellLedger.Status.CONFLICT,
                old.resume() ? CellLedger.Status.APPLYING : CellLedger.Status.QUEUED,
                plan.fingerprint(),
                "");
        states = ledger.connections();
        scanned.remove(slot);
    }

    void tick(ServerLevel level, int budget) throws IOException {
        if (!enabled) return;
        if (job == null) {
            var slots = new ArrayList<>(new TreeSet<>(ledger.allocatedTerritories()));
            if (slots.isEmpty()) return;
            for (int scan = 0; scan < Math.min(32, slots.size()); scan++) {
                int slot = slots.get(Math.floorMod(nextTerritory++, slots.size()));
                var current = ledger.territoryCells(slot);
                if (current.equals(scanned.get(slot))) continue;
                for (var plan : territory(slot).plans()) {
                    var link =
                            generator.cells().campaign().orElseThrow().connections().stream()
                                    .filter(c -> c.id().equals(plan.id()))
                                    .findFirst()
                                    .orElseThrow();
                    if (ledger.status(new Key(slot, link.from())) != CellLedger.Status.READY
                            || ledger.status(new Key(slot, link.to())) != CellLedger.Status.READY)
                        continue;
                    var row = states.get(slot + ":" + plan.id());
                    if (row != null
                            && (row.status() == CellLedger.Status.READY
                                    || row.status() == CellLedger.Status.CONFLICT)) continue;
                    if (row == null) {
                        ledger.connection(
                                slot,
                                plan.id(),
                                null,
                                plan.problem().isEmpty()
                                        ? CellLedger.Status.QUEUED
                                        : CellLedger.Status.CONFLICT,
                                plan.fingerprint(),
                                plan.problem());
                        states = ledger.connections();
                        if (!plan.problem().isEmpty()) continue;
                    }
                    job =
                            new Job(
                                    plan,
                                    states.get(slot + ":" + plan.id()).status()
                                            == CellLedger.Status.APPLYING);
                    break;
                }
                if (job == null) scanned.put(slot, current);
                break;
            }
        }
        if (job != null && job.tick(level, budget)) job = null;
    }

    private final class Job {
        final CellConnectionPlan plan;
        final List<Map.Entry<BlockPos, BlockState>> entries;
        final boolean recovering;
        final net.minecraft.world.phys.AABB area;
        boolean applying;
        int cursor;

        Job(CellConnectionPlan plan, boolean recovering) {
            this.plan = plan;
            this.recovering = recovering;
            entries = List.copyOf(plan.blocks().entrySet());
            int minX = Integer.MAX_VALUE,
                    minY = Integer.MAX_VALUE,
                    minZ = Integer.MAX_VALUE,
                    maxX = Integer.MIN_VALUE,
                    maxY = Integer.MIN_VALUE,
                    maxZ = Integer.MIN_VALUE;
            var volume = new ArrayList<>(plan.blocks().keySet());
            volume.addAll(plan.passages());
            for (var p : volume) {
                minX = Math.min(minX, p.getX());
                minY = Math.min(minY, p.getY());
                minZ = Math.min(minZ, p.getZ());
                maxX = Math.max(maxX, p.getX());
                maxY = Math.max(maxY, p.getY());
                maxZ = Math.max(maxZ, p.getZ());
            }
            area =
                    new net.minecraft.world.phys.AABB(
                            minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
        }

        boolean conflict(String message) throws IOException {
            var status = states.get(plan.territory() + ":" + plan.id()).status();
            ledger.connection(
                    plan.territory(),
                    plan.id(),
                    status,
                    CellLedger.Status.CONFLICT,
                    plan.fingerprint(),
                    message);
            states = ledger.connections();
            return true;
        }

        boolean tick(ServerLevel level, int budget) throws IOException {
            for (var entity :
                    level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, area))
                if (!(entity instanceof net.minecraft.server.level.ServerPlayer))
                    for (var entry : entries)
                        if (!entry.getValue().isAir()
                                && entity.getBoundingBox()
                                        .intersects(
                                                new net.minecraft.world.phys.AABB(entry.getKey())))
                            return conflict(
                                    "An entity occupies the reserved connection at "
                                            + entry.getKey().toShortString());
            for (var p : plan.passages())
                if (!IslandTemplate.safeFloor(level.getBlockState(p.below()), level, p.below())
                        || !level.getBlockState(p).isAir()
                        || !level.getBlockState(p.above()).isAir())
                    return conflict("A saved entrance is obstructed at " + p.toShortString());
            while (budget-- > 0) {
                if (cursor == entries.size()) {
                    if (!applying) {
                        if (!recovering)
                            ledger.connection(
                                    plan.territory(),
                                    plan.id(),
                                    CellLedger.Status.QUEUED,
                                    CellLedger.Status.APPLYING,
                                    plan.fingerprint(),
                                    "");
                        states = ledger.connections();
                        applying = true;
                        cursor = 0;
                        continue;
                    }
                    if (!level.getServer().saveEverything(true, true, true))
                        throw new IOException("Cannot flush connected terrain.");
                    ledger.connection(
                            plan.territory(),
                            plan.id(),
                            CellLedger.Status.APPLYING,
                            CellLedger.Status.READY,
                            plan.fingerprint(),
                            "");
                    states = ledger.connections();
                    return true;
                }
                var entry = entries.get(cursor++);
                var actual = level.getBlockState(entry.getKey());
                var expected = entry.getValue();
                if (!applying) {
                    if (!actual.isAir()
                            && (!(recovering || open(entry.getKey())) || actual != expected))
                        return conflict("Unexpected block at " + entry.getKey().toShortString());
                } else if (actual != expected) {
                    if (!actual.isAir())
                        return conflict(
                                "A reserved connection changed at "
                                        + entry.getKey().toShortString());
                    var a = generator.cells().address(entry.getKey().getX(), entry.getKey().getZ());
                    CellWorlds.write(
                            level,
                            new Key(a.territory(), a.cell()),
                            entry.getKey(),
                            expected,
                            () -> level.setBlock(entry.getKey(), expected, Block.UPDATE_CLIENTS));
                    if (level.getBlockState(entry.getKey()) != expected)
                        return conflict(
                                "Connection placement was rejected at "
                                        + entry.getKey().toShortString());
                }
            }
            return false;
        }
    }
}
