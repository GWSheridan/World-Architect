package com.gwsheridan.worldarchitect.environment;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.arguments.IdentifierArgument.getId;
import static net.minecraft.commands.arguments.IdentifierArgument.id;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;

import com.google.gson.JsonObject;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.serialization.JsonOps;
import com.gwsheridan.worldarchitect.modpack.WorldBundles;
import com.gwsheridan.worldarchitect.spawn.WorldAdministration;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class EnvironmentCommands {
    private EnvironmentCommands() {}

    public static void register(RegisterCommandsEvent event) {
        var dimension = argument("dimension", id()).suggests((c, b) -> SharedSuggestionProvider.suggestResource(c.getSource().getServer().levelKeys().stream().map(ResourceKey::identifier), b))
                .executes(run(EnvironmentCommands::status));
        var set = literal("set").requires(Commands.hasPermission(Commands.LEVEL_ADMINS));
        set.then(literal("time")
                .then(literal("vanilla").executes(run(c -> change(c, j -> j.remove("time")))))
                .then(literal("fixed").then(argument("ticks", IntegerArgumentType.integer(0, 23999)).executes(run(c -> time(c, "fixed")))))
                .then(literal("offset").then(argument("ticks", IntegerArgumentType.integer(0, 23999)).executes(run(c -> time(c, "offset"))))));
        set.then(literal("weather").then(argument("weather", word()).suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"vanilla", "clear", "rain", "thunder"}, b))
                .executes(run(c -> change(c, j -> j.addProperty("weather", getString(c, "weather")))))));
        set.then(literal("mobs").then(argument("category", word()).suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"monsters", "creatures", "spawners"}, b))
                .then(argument("enabled", BoolArgumentType.bool()).executes(run(c -> {
                    String key = getString(c, "category");
                    if (!java.util.Set.of("monsters", "creatures", "spawners").contains(key)) throw new IllegalArgumentException("Choose monsters, creatures or spawners.");
                    return change(c, j -> j.addProperty(key, BoolArgumentType.getBool(c, "enabled")));
                })))));
        set.then(literal("portal").then(argument("entry", word()).suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"all", "players", "operators", "none"}, b))
                .executes(run(c -> change(c, j -> j.addProperty("portal", getString(c, "entry")))))));
        set.then(literal("fog_color").then(argument("color", word()).executes(run(c -> color(c, "fog_color")))));
        set.then(literal("sky_color").then(argument("color", word()).executes(run(c -> color(c, "sky_color")))));
        set.then(literal("cloud_color").then(argument("color", word()).executes(run(c -> color(c, "cloud_color")))));
        set.then(literal("fog").then(literal("vanilla").executes(run(c -> change(c, j -> visual(j).remove("fog")))))
                .then(argument("start", FloatArgumentType.floatArg(0, 4096)).then(argument("end", FloatArgumentType.floatArg(0.01F, 4096)).executes(run(c -> change(c, j -> {
                    var fog = new JsonObject(); fog.addProperty("start", FloatArgumentType.getFloat(c, "start")); fog.addProperty("end", FloatArgumentType.getFloat(c, "end")); visual(j).add("fog", fog);
                }))))));
        set.then(literal("clouds").then(argument("clouds", word()).suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"vanilla", "hidden", "shown"}, b))
                .executes(run(c -> change(c, j -> visual(j).addProperty("clouds", getString(c, "clouds")))))));
        set.then(literal("cloud_height").then(literal("vanilla").executes(run(c -> change(c, j -> visual(j).remove("cloud_height")))))
                .then(argument("height", FloatArgumentType.floatArg(-2032, 4096)).executes(run(c -> change(c, j -> visual(j).addProperty("cloud_height", FloatArgumentType.getFloat(c, "height")))))));
        dimension.then(set);
        dimension.then(literal("reset").requires(Commands.hasPermission(Commands.LEVEL_ADMINS)).executes(run(c -> save(c, EnvironmentProfile.DEFAULT))));
        var editor = literal("edit").requires(Commands.hasPermission(Commands.LEVEL_ADMINS)).executes(run(c -> {
            ServerLevel level = level(c);
            EnvironmentDialog.show(c.getSource().getPlayerOrException(), level, WorldEnvironment.profile(level), "overview");
            return 1;
        })).then(argument("section", word()).suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"time", "mobs", "portal", "fog", "sky"}, b)).executes(run(c -> {
            ServerLevel level = level(c);
            EnvironmentDialog.show(c.getSource().getPlayerOrException(), level, WorldEnvironment.profile(level), getString(c, "section"));
            return 1;
        })));
        dimension.then(editor);
        event.getDispatcher().register(literal("wa_environment").then(dimension).executes(c -> {
            c.getSource().sendSuccess(() -> Component.literal("Use /wa_environment <dimension> for settings and capability diagnostics; level 3 administrators can use edit, set and reset."), false);
            return 1;
        }));
    }

    private static int time(CommandContext<CommandSourceStack> c, String mode) {
        return change(c, j -> { var time = new JsonObject(); time.addProperty("mode", mode); time.addProperty("ticks", IntegerArgumentType.getInteger(c, "ticks")); j.add("time", time); });
    }

    private static int color(CommandContext<CommandSourceStack> c, String key) {
        String value = getString(c, "color");
        return change(c, j -> {
            if (value.equals("vanilla")) visual(j).remove(key);
            else {
                if (!value.matches("[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Use six hexadecimal RGB digits, such as 33aaff, or vanilla.");
                visual(j).addProperty(key, Integer.parseInt(value, 16));
            }
        });
    }

    private static JsonObject visual(JsonObject root) {
        if (!root.has("visual")) root.add("visual", new JsonObject());
        return root.getAsJsonObject("visual");
    }

    private static int change(CommandContext<CommandSourceStack> c, Consumer<JsonObject> edit) {
        var json = EnvironmentProfile.CODEC.encodeStart(JsonOps.INSTANCE, WorldEnvironment.profile(level(c))).getOrThrow().getAsJsonObject();
        edit.accept(json);
        return save(c, EnvironmentProfile.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(IllegalArgumentException::new));
    }

    private static int save(CommandContext<CommandSourceStack> c, EnvironmentProfile profile) {
        ServerLevel level = level(c);
        WorldBundles.active(level.getServer()).ifPresent(bundle -> {
            if (!bundle.manifest().canEditDimension(level.dimension().identifier())) throw new IllegalStateException("This dimension is locked by the active preset bundle.");
        });
        WorldEnvironment.validate(level.dimension().identifier(), level.dimensionType(), profile);
        WorldAdministration.save(level.getServer(), WorldAdministration.get(level.getServer()).withEnvironment(level.dimension().identifier(), profile));
        WorldEnvironment.changed(level);
        c.getSource().sendSuccess(() -> Component.literal("Saved environment for " + level.dimension().identifier()), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        var level = level(c);
        c.getSource().sendSuccess(() -> Component.literal(level.dimension().identifier() + " · " + EnvironmentProfile.CODEC.encodeStart(JsonOps.INSTANCE, WorldEnvironment.profile(level)).getOrThrow()
                + "\nClock ticks: " + level.getDefaultClockTime() + " · Rain: " + level.getRainLevel(1) + " · Thunder: " + level.getThunderLevel(1)
                + "\n" + WorldEnvironment.capabilities(level.dimension().identifier(), level.dimensionType()).description()), false);
        return 1;
    }

    private static ServerLevel level(CommandContext<CommandSourceStack> c) {
        var level = c.getSource().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, getId(c, "dimension")));
        if (level == null) throw new IllegalArgumentException("Unknown environment dimension " + getId(c, "dimension"));
        return level;
    }

    private static Command<CommandSourceStack> run(Command<CommandSourceStack> command) {
        return c -> {
            try { return command.run(c); }
            catch (RuntimeException e) { c.getSource().sendFailure(Component.literal(e.getMessage() == null ? "Cannot update the environment." : e.getMessage())); return 0; }
        };
    }
}
