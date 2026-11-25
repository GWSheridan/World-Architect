package com.gwsheridan.worldsunwritten.api;

import java.util.List;
import net.minecraft.core.BlockPos;

/** Read-only geometry shared by legacy islands and versioned Skyblock catalogs. */
public interface IslandLayout {
    int originY();
    int spacing();
    int personalSlots();
    int slotAt(int x, int z);
    BlockPos origin(int slot);
    BlockPos spawn(int slot);
    List<BlockPos> floors(int slot);
    default String type(int slot) { return "classic"; }
}
