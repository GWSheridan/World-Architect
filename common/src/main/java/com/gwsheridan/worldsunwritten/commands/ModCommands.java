package com.gwsheridan.worldsunwritten.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import com.gwsheridan.worldsunwritten.commands.CommandRegistration;

/** One current command root; group requirements remain attached to their original handlers. */
public final class ModCommands {
    private ModCommands() {}
    public static void register(CommandRegistration event, LiteralArgumentBuilder<CommandSourceStack> group) {
        event.dispatcher().register(Commands.literal("worldsunwritten").then(group));
    }
}
