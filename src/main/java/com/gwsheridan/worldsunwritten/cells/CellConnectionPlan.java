package com.gwsheridan.worldsunwritten.cells;

import com.gwsheridan.worldsunwritten.config.IslandTemplate;
import com.gwsheridan.worldsunwritten.worldgen.CellGenerator;
import com.gwsheridan.worldsunwritten.worldgen.CellGenerator.Key;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/**
 * A bounded immutable corridor; occupied terrain and player builds are never excavation targets.
 */
public record CellConnectionPlan(
        String id,
        int territory,
        Map<BlockPos, BlockState> blocks,
        List<BlockPos> passages,
        String problem,
        String fingerprint) {
    public static final int MAX_POSITIONS = 65536;

    public CellConnectionPlan {
        blocks = Collections.unmodifiableMap(new LinkedHashMap<>(blocks));
        passages = List.copyOf(passages);
    }

    public static CellConnectionPlan create(
            CellGenerator generator,
            CellCampaign.Connection link,
            int slot,
            LevelHeightAccessor height,
            long seed) {
        var blocks = new LinkedHashMap<BlockPos, BlockState>();
        var passages = new LinkedHashSet<BlockPos>();
        try {
            var from = port(generator, link.from(), slot, link.fromPort(), link.floor(), seed);
            var to = port(generator, link.to(), slot, link.toPort(), link.floor(), seed);
            int dx = to.getX() - from.getX(),
                    dz = to.getZ() - from.getZ(),
                    steps = Math.max(Math.abs(dx), Math.abs(dz)),
                    half = link.width() / 2;
            if (steps < 1 || steps > 256 || Math.abs(to.getY() - from.getY()) > steps)
                throw new IllegalArgumentException(
                        "Connection length or slope exceeds the cell limits.");
            for (int n = 0; n <= steps; n++) {
                int x = from.getX() + (int) Math.round(dx * n / (double) steps),
                        z = from.getZ() + (int) Math.round(dz * n / (double) steps),
                        floor =
                                from.getY()
                                        - 1
                                        + (int)
                                                Math.round(
                                                        (to.getY() - from.getY())
                                                                * n
                                                                / (double) steps);
                for (int side = -half; side <= half; side++) {
                    int wx = x + (Math.abs(dx) < Math.abs(dz) ? side : 0),
                            wz = z + (Math.abs(dx) >= Math.abs(dz) ? side : 0);
                    int roof = link.kind().equals("tunnel") ? 4 : 2;
                    if (floor < height.getMinY()
                            || floor + roof > height.getMaxY()
                            || generator.nativeReservation(wx, wz))
                        throw new IllegalArgumentException(
                                "Connection intersects the native End reservation or dimension"
                                        + " height.");
                    var address = generator.cells().address(wx, wz);
                    if (address == null || address.territory() != slot)
                        throw new IllegalArgumentException(
                                "Connection leaves its physical territory.");
                    boolean inside =
                            generator
                                    .cells()
                                    .layout()
                                    .contains(address.cell(), address.localX(), address.localZ());
                    if (inside
                            && !address.cell().equals(link.from())
                            && !address.cell().equals(link.to()))
                        throw new IllegalArgumentException("Connection crosses an unrelated cell.");
                    var original = generator.previewColumn(wx, wz, height, seed);
                    boolean air = true;
                    for (int y = floor; y <= floor + roof; y++) air &= original.getBlock(y).isAir();
                    if (!air) {
                        var p = new BlockPos(wx, floor + 1, wz);
                        if (!IslandTemplate.safeFloor(
                                        original.getBlock(floor),
                                        net.minecraft.world.level.EmptyBlockGetter.INSTANCE,
                                        p.below())
                                || !original.getBlock(floor + 1).isAir()
                                || !original.getBlock(floor + 2).isAir())
                            throw new IllegalArgumentException(
                                    "A connection meets occupied terrain; move its ports or"
                                            + " floor.");
                        passages.add(p);
                        continue;
                    }
                    for (int y = floor; y <= floor + roof; y++) {
                        boolean solid =
                                y == floor
                                        || link.kind().equals("tunnel")
                                                && (y == floor + roof
                                                        || Math.abs(side) == half && half > 0)
                                        || link.kind().equals("bridge")
                                                && half > 0
                                                && Math.abs(side) == half
                                                && y == floor + 1;
                        var state = solid ? link.material() : Blocks.AIR.defaultBlockState();
                        var pos = new BlockPos(wx, y, wz);
                        var old = blocks.putIfAbsent(pos, state);
                        if (old != null && old != state)
                            throw new IllegalArgumentException(
                                    "Connection slope overlaps its own headroom.");
                        if (blocks.size() > MAX_POSITIONS)
                            throw new IllegalArgumentException(
                                    "Connection exceeds its position budget.");
                    }
                }
            }
            StringBuilder content = new StringBuilder();
            blocks.forEach(
                    (p, s) -> content.append(p.toShortString()).append('=').append(s).append('\n'));
            passages.forEach(
                    p -> content.append("passage=").append(p.toShortString()).append('\n'));
            return new CellConnectionPlan(
                    link.id(), slot, blocks, List.copyOf(passages), "", hash(content.toString()));
        } catch (IllegalArgumentException error) {
            return new CellConnectionPlan(
                    link.id(),
                    slot,
                    Map.of(),
                    List.of(),
                    error.getMessage(),
                    hash(
                            "invalid:"
                                    + CellCampaign.canonical(
                                            CellCampaign.Connection.CODEC
                                                    .encodeStart(
                                                            com.mojang.serialization.JsonOps
                                                                    .INSTANCE,
                                                            link)
                                                    .getOrThrow())));
        }
    }

    private static BlockPos port(
            CellGenerator generator,
            com.gwsheridan.worldsunwritten.api.CellLayout.Cell cell,
            int slot,
            String name,
            int floor,
            long seed) {
        var key = new Key(slot, cell);
        var tile = generator.cells().tile(seed, slot, cell);
        if (!name.isEmpty()) {
            var build =
                    generator
                            .build(tile, key, seed)
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "A connection port needs an embedded"
                                                            + " template."));
            var marker = build.markers().get(name);
            if (marker == null)
                throw new IllegalArgumentException("Missing connection port " + name);
            return generator.origin(tile, key, seed).offset(marker);
        }
        var c = generator.cells().layout().centre(cell);
        return generator
                .cells()
                .offset(slot)
                .offset((int) Math.floor(c.x()), floor + 1, (int) Math.floor(c.z()));
    }

    private static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }
}
