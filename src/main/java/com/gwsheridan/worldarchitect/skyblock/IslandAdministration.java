package com.gwsheridan.worldarchitect.skyblock;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static net.minecraft.commands.arguments.IdentifierArgument.getId;
import static net.minecraft.commands.arguments.IdentifierArgument.id;
import static net.minecraft.commands.arguments.coordinates.BlockPosArgument.getBlockPos;
import static net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos;

import com.gwsheridan.worldarchitect.config.IslandProfile;
import com.gwsheridan.worldarchitect.modpack.WorldBundles;
import com.gwsheridan.worldarchitect.spawn.SafeSpawns;
import com.gwsheridan.worldarchitect.spawn.SpawnPolicies;
import com.gwsheridan.worldarchitect.spawn.SpawnPolicy;
import com.gwsheridan.worldarchitect.spawn.WorldAdministration;
import com.gwsheridan.worldarchitect.spawn.WorldAdministration.Protection;
import com.gwsheridan.worldarchitect.worldgen.VoidGenerator;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.nbt.NbtOps;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class IslandAdministration {
    private IslandAdministration() {}

    public static void register(RegisterCommandsEvent event) {
        var root = literal("wa_island").executes(run(IslandAdministration::self))
                .then(literal("panel").executes(run(c -> AdministrationPanel.open(c.getSource().getPlayerOrException(), 0))))
                .then(literal("home").executes(run(c -> home(c.getSource().getPlayerOrException()))))
                .then(literal("list").requires(Commands.hasPermission(Commands.LEVEL_ADMINS)).executes(run(IslandAdministration::list)))
                .then(literal("inspect").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                        .then(argument("slot", IntegerArgumentType.integer(0, IslandProfile.MAX_SLOTS)).executes(run(IslandAdministration::inspect))))
                .then(literal("reassign").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                        .then(playerArgument("player").then(argument("slot", IntegerArgumentType.integer(1, IslandProfile.MAX_SLOTS))
                                .executes(run(IslandAdministration::reassign)))))
                .then(literal("swap").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                        .then(playerArgument("first").then(playerArgument("second").executes(run(IslandAdministration::swap)))))
                .then(literal("team").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                        .then(literal("assign").then(argument("team", word())
                                .then(argument("slot", IntegerArgumentType.integer(1, IslandProfile.MAX_SLOTS)).executes(run(IslandAdministration::team))))))
                .then(protection());
        event.getDispatcher().register(root);
        var spawn = literal("wa_spawn").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .executes(run(c -> {
                    success(c, WorldAdministration.get(server(c)).toString());
                    return 1;
                }))
                .then(literal("clear").then(argument("event", word()).suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"initial", "respawn", "portal"}, b))
                        .executes(run(c -> clear(c, c.getSource().getLevel().dimension().identifier())))
                        .then(argument("dimension", id()).executes(run(c -> clear(c, getId(c, "dimension")))))));
        var set = literal("set").then(argument("event", word()).suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"initial", "respawn", "portal"}, b))
                .then(policy("fixed", SpawnPolicy.Kind.FIXED))
                .then(policy("island", SpawnPolicy.Kind.ISLAND))
                .then(policy("team", SpawnPolicy.Kind.TEAM))
                .then(policy("marker", SpawnPolicy.Kind.MARKER))
                .then(policy("generated", SpawnPolicy.Kind.GENERATED)));
        spawn.then(set);
        event.getDispatcher().register(spawn);
    }

    private static ArgumentBuilder<CommandSourceStack, ?> policy(String label, SpawnPolicy.Kind kind) {
        var dimension = argument("dimension", id());
        if (kind == SpawnPolicy.Kind.FIXED) {
            dimension.then(argument("position", blockPos()).executes(run(c -> set(c, SpawnPolicy.simple(kind,
                    getId(c, "dimension"), getBlockPos(c, "position"))))));
        } else if (kind == SpawnPolicy.Kind.ISLAND || kind == SpawnPolicy.Kind.TEAM) {
            dimension.executes(run(c -> set(c, SpawnPolicy.simple(kind, getId(c, "dimension"), BlockPos.ZERO))));
        } else if (kind == SpawnPolicy.Kind.GENERATED) {
            dimension.then(argument("template", id()).then(argument("position", blockPos()).then(argument("module", word())
                    .then(argument("marker", word()).executes(run(c -> set(c, markerPolicy(c, kind, Rotation.NONE, Mirror.NONE))))))));
        } else {
            dimension.then(argument("template", id()).then(argument("marker", word()).then(argument("position", blockPos())
                    .executes(run(c -> set(c, markerPolicy(c, kind, Rotation.NONE, Mirror.NONE))))
                    .then(argument("rotation", word()).executes(run(c -> set(c, markerPolicy(c, kind,
                            rotation(c), Mirror.NONE)))).then(argument("mirror", word()).suggests((c, b) ->
                            SharedSuggestionProvider.suggest(new String[]{"none", "left_right", "front_back"}, b))
                            .executes(run(c -> set(c, markerPolicy(c, kind, rotation(c), Mirror.valueOf(getString(c, "mirror").toUpperCase(java.util.Locale.ROOT)))))))))));
        }
        return literal(label).then(dimension);
    }

    private static SpawnPolicy markerPolicy(CommandContext<CommandSourceStack> c, SpawnPolicy.Kind kind, Rotation rotation, Mirror mirror) {
        return new SpawnPolicy(kind, getId(c, "dimension"), getBlockPos(c, "position"), Optional.of(getId(c, "template")),
                kind == SpawnPolicy.Kind.GENERATED ? getString(c, "module") : "", getString(c, "marker"), rotation, mirror);
    }

    private static Rotation rotation(CommandContext<CommandSourceStack> context) {
        return switch (getString(context, "rotation")) {
            case "none" -> Rotation.NONE;
            case "cw90" -> Rotation.CLOCKWISE_90;
            case "cw180" -> Rotation.CLOCKWISE_180;
            case "ccw90" -> Rotation.COUNTERCLOCKWISE_90;
            default -> throw new IllegalArgumentException("Use none, cw90, cw180 or ccw90.");
        };
    }

    private static int set(CommandContext<CommandSourceStack> context, SpawnPolicy policy) throws IOException {
        String phase = phase(context);
        allowPolicyEdit(server(context), phase, policy.dimension());
        SpawnPolicy.CODEC.encodeStart(NbtOps.INSTANCE, policy).getOrThrow(IOException::new);
        var level = server(context).getLevel(ResourceKey.create(Registries.DIMENSION, policy.dimension()));
        if (level == null) {
            throw new IOException("Unknown spawn dimension " + policy.dimension());
        }
        if (policy.kind() == SpawnPolicy.Kind.ISLAND || policy.kind() == SpawnPolicy.Kind.TEAM) {
            if (!(level.getChunkSource().getGenerator() instanceof VoidGenerator generator) || generator.island().isEmpty()) {
                throw new IOException("Island policies require a Skyblock dimension.");
            }
            if (policy.kind() == SpawnPolicy.Kind.TEAM && (level != server(context).overworld() || generator.island().orElseThrow().personalSlots() == 0)) {
                throw new IOException("Team policies require personal Overworld island slots.");
            }
            if (IslandSpawns.position(level, generator.island().orElseThrow(), 0).isEmpty()) {
                throw new IOException("The shared island has no safe spawn.");
            }
        } else if (SpawnPolicies.primary(server(context), policy, new UUID(0, 0), "").isEmpty()) {
            throw new IOException("The requested spawn has no safe floor and headroom.");
        }
        var settings = WorldAdministration.get(server(context)).withPolicy(phase, Optional.of(policy), policy.dimension());
        WorldAdministration.save(server(context), settings);
        success(context, "Saved " + phase + " spawn policy " + policy.kind().getSerializedName() + " in " + policy.dimension() + ".");
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> context, Identifier dimension) {
        String phase = phase(context);
        allowPolicyEdit(server(context), phase, dimension);
        WorldAdministration.save(server(context), WorldAdministration.get(server(context)).withPolicy(phase, Optional.empty(), dimension));
        success(context, "Cleared " + phase + " spawn policy.");
        return 1;
    }

    private static String phase(CommandContext<CommandSourceStack> context) {
        String phase = getString(context, "event");
        if (!java.util.List.of("initial", "respawn", "portal").contains(phase)) {
            throw new IllegalArgumentException("Choose initial, respawn or portal.");
        }
        return phase;
    }

    private static void allowPolicyEdit(MinecraftServer server, String phase, Identifier dimension) {
        WorldBundles.active(server).ifPresent(bundle -> {
            if (!bundle.manifest().canEditDimension(dimension) || !phase.equals("portal")
                    && !bundle.manifest().canEditDimension(LevelStem.OVERWORLD.identifier())) {
                throw new IllegalStateException("The bundled spawn policy is locked.");
            }
        });
    }

    public static IslandProfile profile(MinecraftServer server) {
        if (server.overworld().getChunkSource().getGenerator() instanceof VoidGenerator generator
                && generator.island().filter(island -> island.personalSlots() > 0).isPresent()) {
            return generator.island().orElseThrow();
        }
        throw new IllegalStateException("This world needs personal Overworld Skyblock slots.");
    }

    private static int home(ServerPlayer player) {
        var server = player.level().getServer();
        profile(server);
        var configured = WorldAdministration.get(server).initial().filter(policy -> policy.kind() == SpawnPolicy.Kind.TEAM);
        SpawnPolicy policy = configured.orElseGet(() -> SpawnPolicy.simple(SpawnPolicy.Kind.ISLAND,
                server.overworld().dimension().identifier(), BlockPos.ZERO));
        SafeSpawns.Target target = SpawnPolicies.resolveOrFallback(server, policy, player.getUUID(), player.getPlainTextName());
        var pos = target.center();
        if (!player.teleportTo(target.level(), pos.x, pos.y, pos.z, java.util.Set.of(), target.yaw(), 0.0F, true)) {
            throw new IllegalStateException("The server refused the island teleport.");
        }
        return 1;
    }

    private static int self(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        int capacity = profile(server(context)).personalSlots();
        var team = player.getTeam();
        success(context, "Personal island " + IslandAllocations.assignedSlot(data(context), capacity, player.getUUID())
                + (team == null ? "" : " · Team " + team.getName() + " · Island " + IslandAllocations.teams(data(context), capacity).getOrDefault(team.getName(), 0)));
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        int capacity = profile(server(context)).personalSlots();
        IslandAllocations.players(data(context), capacity).entrySet().stream().sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .limit(32).forEach(entry -> success(context, entry.getKey() + " · island " + entry.getValue()));
        IslandAllocations.teams(data(context), capacity).entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                .limit(32).forEach(entry -> success(context, "Team " + entry.getKey() + " · island " + entry.getValue()));
        return 1;
    }

    private static int inspect(CommandContext<CommandSourceStack> context) {
        var profile = profile(server(context));
        int slot = IntegerArgumentType.getInteger(context, "slot");
        if (slot > profile.personalSlots()) {
            throw new IllegalArgumentException("Island slot exceeds this world's capacity.");
        }
        var players = IslandAllocations.players(data(context), profile.personalSlots());
        var teams = IslandAllocations.teams(data(context), profile.personalSlots());
        String owner = players.entrySet().stream().filter(entry -> entry.getValue() == slot).map(entry -> entry.getKey().toString()).findFirst()
                .orElseGet(() -> teams.entrySet().stream().filter(entry -> entry.getValue() == slot).map(entry -> "team " + entry.getKey()).findFirst()
                        .orElse(slot == 0 ? "shared" : "unassigned"));
        success(context, "Island " + slot + " · " + owner + " · origin " + profile.origin(slot).toShortString());
        return 1;
    }

    private static int reassign(CommandContext<CommandSourceStack> context) {
        int slot = IntegerArgumentType.getInteger(context, "slot");
        IslandAllocations.reassign(data(context), profile(server(context)).personalSlots(), player(context, "player"), slot);
        IslandProtection.revalidate(server(context));
        success(context, "Reassigned player island to slot " + slot + ".");
        return 1;
    }

    private static int swap(CommandContext<CommandSourceStack> context) {
        IslandAllocations.swap(data(context), profile(server(context)).personalSlots(), player(context, "first"), player(context, "second"));
        IslandProtection.revalidate(server(context));
        success(context, "Swapped player island assignments.");
        return 1;
    }

    private static int team(CommandContext<CommandSourceStack> context) {
        String name = getString(context, "team");
        if (server(context).getScoreboard().getPlayerTeam(name) == null) {
            throw new IllegalArgumentException("Create the Minecraft scoreboard team first.");
        }
        int slot = IntegerArgumentType.getInteger(context, "slot");
        IslandAllocations.reassignTeam(data(context), profile(server(context)).personalSlots(), name, slot);
        IslandProtection.revalidate(server(context));
        success(context, "Assigned team " + name + " to island " + slot + ".");
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> protection() {
        return literal("protection").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .then(argument("setting", word()).suggests((c, b) -> SharedSuggestionProvider.suggest(
                        new String[]{"breaking", "placement", "containers", "shared"}, b))
                        .then(argument("enabled", BoolArgumentType.bool()).executes(run(IslandAdministration::protect))))
                .then(literal("container").then(argument("action", word()).suggests((c, b) ->
                        SharedSuggestionProvider.suggest(new String[]{"add", "remove"}, b))
                        .then(argument("block", id()).executes(run(IslandAdministration::container)))));
    }

    private static int protect(CommandContext<CommandSourceStack> context) {
        profile(server(context));
        var settings = WorldAdministration.get(server(context));
        var old = settings.protection();
        String setting = getString(context, "setting");
        if (!java.util.List.of("breaking", "placement", "containers", "shared").contains(setting)) {
            throw new IllegalArgumentException("Choose breaking, placement, containers or shared.");
        }
        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        var protection = new Protection(setting.equals("breaking") ? enabled : old.breaking(),
                setting.equals("placement") ? enabled : old.placement(), setting.equals("containers") ? enabled : old.containers(),
                setting.equals("shared") ? enabled : old.shared(), old.containerBlocks());
        WorldAdministration.save(server(context), settings.withProtection(protection));
        IslandProtection.revalidate(server(context));
        success(context, "Island protection " + setting + " set to " + enabled + ".");
        return 1;
    }

    private static int container(CommandContext<CommandSourceStack> context) {
        var id = getId(context, "block");
        if (!BuiltInRegistries.BLOCK.containsKey(id)) {
            throw new IllegalArgumentException("Unknown container block " + id);
        }
        var settings = WorldAdministration.get(server(context));
        var old = settings.protection();
        var blocks = new ArrayList<>(old.containerBlocks());
        switch (getString(context, "action")) {
            case "add" -> { if (!blocks.contains(id)) blocks.add(id); }
            case "remove" -> blocks.remove(id);
            default -> throw new IllegalArgumentException("Choose add or remove.");
        }
        WorldAdministration.save(server(context), settings.withProtection(new Protection(old.breaking(), old.placement(), old.containers(), old.shared(), blocks)));
        IslandProtection.revalidate(server(context));
        success(context, "Updated protected container blocks.");
        return 1;
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> playerArgument(String name) {
        return argument(name, word()).suggests((c, b) -> SharedSuggestionProvider.suggest(server(c).getPlayerNames(), b));
    }

    private static UUID player(CommandContext<CommandSourceStack> context, String argument) {
        String input = getString(context, argument);
        var player = server(context).getPlayerList().getPlayerByName(input);
        if (player != null) {
            return player.getUUID();
        }
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Use an online player name or a saved player's UUID.");
        }
    }

    private static java.nio.file.Path data(CommandContext<CommandSourceStack> context) {
        return server(context).getWorldPath(LevelResource.DATA);
    }

    private static MinecraftServer server(CommandContext<CommandSourceStack> context) {
        return context.getSource().getServer();
    }

    private static void success(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().sendSuccess(() -> Component.literal(message), false);
    }

    private static Command<CommandSourceStack> run(Action action) {
        return context -> {
            try {
                return action.execute(context);
            } catch (IOException | RuntimeException e) {
                context.getSource().sendFailure(Component.literal(e.getMessage() == null ? "World Architect administration failed." : e.getMessage()));
                return 0;
            }
        };
    }

    @FunctionalInterface
    private interface Action {
        int execute(CommandContext<CommandSourceStack> context) throws IOException, CommandSyntaxException;
    }

}
