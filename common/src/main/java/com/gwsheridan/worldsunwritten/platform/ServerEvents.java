package com.gwsheridan.worldsunwritten.platform;

import com.gwsheridan.worldsunwritten.cells.CellWorlds;
import com.gwsheridan.worldsunwritten.compatibility.SaveProtection;
import com.gwsheridan.worldsunwritten.environment.WorldEnvironment;
import com.gwsheridan.worldsunwritten.skyblock.IslandCaptures;
import com.gwsheridan.worldsunwritten.skyblock.IslandProtection;
import com.gwsheridan.worldsunwritten.skyblock.IslandSelections;
import com.gwsheridan.worldsunwritten.spawn.WorldAdministration;
import com.gwsheridan.worldsunwritten.structure.SiteStudio;
import com.gwsheridan.worldsunwritten.structure.StructureCommands;
import com.gwsheridan.worldsunwritten.workbench.WorkbenchStudio;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class ServerEvents {
    private ServerEvents() {}

    public static void starting(MinecraftServer server) {
        SaveProtection.recordNewWorld(server);
        WorldAdministration.load(server);
        WorldEnvironment.validateSaved(server);
    }

    public static void joined(ServerPlayer player) {
        WorldEnvironment.login(player);
        IslandSelections.login(player);
    }

    public static void left(ServerPlayer player) {
        WorkbenchStudio.logout(player);
        CellWorlds.logout(player);
        SiteStudio.logout(player);
        IslandSelections.logout(player);
        IslandCaptures.logout(player);
        IslandProtection.logout(player);
        StructureCommands.onLogout(player);
    }
}
