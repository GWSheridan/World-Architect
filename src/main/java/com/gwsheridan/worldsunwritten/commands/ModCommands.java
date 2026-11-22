package com.gwsheridan.worldsunwritten.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** One current command root; group requirements remain attached to their original handlers. */
public final class ModCommands {
    private ModCommands() {}
    public static void register(RegisterCommandsEvent event, LiteralArgumentBuilder<CommandSourceStack> group) {
        event.getDispatcher().register(Commands.literal("worldsunwritten").then(group));
    }
}
