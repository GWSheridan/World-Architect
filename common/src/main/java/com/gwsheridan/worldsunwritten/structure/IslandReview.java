package com.gwsheridan.worldsunwritten.structure;

import com.gwsheridan.worldsunwritten.config.IslandTemplate;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class IslandReview {
    private IslandReview() {}

    public static List<String> inspect(IslandBuild source, HolderLookup.Provider registries) throws IOException {
        var island = source.checked(registries);
        var states = new HashMap<BlockPos, BlockState>();
        island.cells().forEach(cell -> states.put(cell.pos(), cell.state()));
        var reached = new HashSet<BlockPos>();
        var queue = new ArrayDeque<BlockPos>();
        reached.add(island.spawn());
        queue.add(island.spawn());
        while (!queue.isEmpty()) {
            BlockPos position = queue.removeFirst();
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                var adjacent = position.relative(direction);
                for (int step = -1; step <= 1; step++) {
                    var next = adjacent.above(step);
                    if (safe(states, next) && (step < 1 || !states.containsKey(position.above(2)))
                            && (step >= 0 || !states.containsKey(adjacent.above()))
                            && reached.add(next)) queue.addLast(next);
                }
            }
        }
        var rows = new ArrayList<String>();
        rows.add("Spawn marker " + island.spawnMarker() + " · " + island.spawn().toShortString());
        var ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        var totals = new TreeMap<String, Integer>();
        for (var cell : island.cells()) {
            if (!IslandBuild.container(cell.state())) continue;
            var items = cell.data().orElseGet(CompoundTag::new).getListOrEmpty("Items");
            if (items.isEmpty()) continue;
            boolean accessible = false;
            for (Direction direction : Direction.Plane.HORIZONTAL)
                for (int dy = -1; dy <= 1; dy++)
                    accessible |= reached.contains(cell.pos().relative(direction).above(dy));
            var lid = states.get(cell.pos().above());
            if (!cell.state().is(Blocks.BARREL) && lid != null && lid.isRedstoneConductor(EmptyBlockGetter.INSTANCE, cell.pos().above()))
                rows.add("Chest lid blocked at " + cell.pos().toShortString());
            if (!accessible)
                rows.add("No walking route from spawn to supplies at " + cell.pos().toShortString());
            var supplies = new ArrayList<String>();
            for (var item : items) {
                var stack = ItemStack.CODEC.parse(ops, item).getOrThrow(IOException::new);
                String name = stack.getHoverName().getString();
                totals.merge(name, stack.getCount(), Integer::sum);
                supplies.add(name + " × " + stack.getCount());
            }
            rows.add(cell.pos().toShortString() + " · " + String.join(", ", supplies));
        }
        rows.add(totals.isEmpty() ? "No starter supplies configured." : "Total supplies");
        totals.forEach((name, amount) -> rows.add(name + " × " + amount));
        return List.copyOf(rows);
    }

    private static boolean safe(Map<BlockPos, BlockState> states, BlockPos pos) {
        var floor = states.get(pos.below());
        return floor != null && !states.containsKey(pos) && !states.containsKey(pos.above())
                && IslandTemplate.safeFloor(floor, EmptyBlockGetter.INSTANCE, pos.below());
    }
}
