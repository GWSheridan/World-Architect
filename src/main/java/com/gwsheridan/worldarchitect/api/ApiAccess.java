package com.gwsheridan.worldarchitect.api;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;

final class ApiAccess {
    private ApiAccess() {}
    static void thread(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("World Architect operations require the server thread.");
    }
    static void admin(CommandSourceStack source) {
        thread(source.getServer());
        if (!Commands.hasPermission(Commands.LEVEL_ADMINS).test(source)) throw new SecurityException("World Architect administration requires permission level 3.");
    }
}
