package com.gwsheridan.worldsunwritten.rules;

import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.arguments.IdentifierArgument.id;
import static net.minecraft.commands.arguments.IdentifierArgument.getId;
import static net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos;
import static net.minecraft.commands.arguments.coordinates.BlockPosArgument.getBlockPos;

import com.gwsheridan.worldsunwritten.worldgen.ManagedGenerator;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import com.gwsheridan.worldsunwritten.commands.CommandRegistration;

public final class RuleCommands {
    private RuleCommands() {}
    public static void register(CommandRegistration event) {
        com.gwsheridan.worldsunwritten.commands.ModCommands.register(event,literal("rules").executes(c -> {
            c.getSource().sendSuccess(() -> Component.literal("Use /worldsunwritten rules <dimension> [explain <x y z>]. Saved rules are immutable; edit a new-world draft to change generation."), false);
            return 1;
        }).then(argument("dimension", id()).suggests((c, b) -> SharedSuggestionProvider.suggestResource(c.getSource().getServer().levelKeys().stream().map(ResourceKey::identifier), b))
                .executes(c -> report(c, false)).then(literal("explain").then(argument("position", blockPos()).executes(c -> report(c, true))))));
    }

    private static int report(CommandContext<CommandSourceStack> c, boolean explain) {
        var level = c.getSource().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, getId(c, "dimension")));
        if (level == null || !(level.getChunkSource().getGenerator() instanceof ManagedGenerator generator)) {
            c.getSource().sendFailure(Component.literal("Choose an installed managed dimension.")); return 0;
        }
        var rules = generator.dimensionProfile().map(profile -> profile.rules()).orElse(RuleSet.EMPTY);
        long matched = 0;
        if (explain) {
            var pos = getBlockPos(c, "position");
            var biome = level.uncachedBiomeResolver().getNoiseBiome(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(pos.getY()), QuartPos.fromBlock(pos.getZ()));
            matched = rules.matches(new RuleSet.Context(level.dimension().identifier(), biome.unwrapKey().orElseThrow().identifier(), pos.getX(), pos.getY(), pos.getZ(), level.getChunkSource().randomState().seed(), RuleDependencies.loaded(),generator instanceof com.gwsheridan.worldsunwritten.worldgen.SolidColumnGenerator solid?solid.terrainRegions():java.util.Optional.empty()));
        }
        c.getSource().sendSuccess(() -> Component.literal("Rule engine v"+rules.version()+" · " + level.dimension().identifier() + " · " + rules.rules().size() + "/32 rules · material → feature → template → spawn"), false);
        for (int i = 0; i < rules.ordered().size(); i++) {
            var rule = rules.ordered().get(i);
            String result = explain ? ((matched & 1L << i) == 0 ? " · conditions fail" : " · conditions match") : "";
            int number = i + 1;
            c.getSource().sendSuccess(() -> Component.literal(number + ". " + rule.action().type().phase() + " · priority " + rule.priority() + " · " + rule.id() + result), false);
        }
        return rules.rules().size();
    }
}
