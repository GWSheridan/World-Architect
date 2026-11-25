package com.gwsheridan.worldsunwritten.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;

public record CommandRegistration(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {}
