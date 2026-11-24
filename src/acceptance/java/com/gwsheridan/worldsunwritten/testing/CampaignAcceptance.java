package com.gwsheridan.worldsunwritten.testing;

import com.gwsheridan.worldsunwritten.api.*;
import com.gwsheridan.worldsunwritten.api.CellLayout.Cell;
import com.gwsheridan.worldsunwritten.cells.*;
import com.gwsheridan.worldsunwritten.modpack.*;
import com.gwsheridan.worldsunwritten.worldgen.CellGenerator;
import com.mojang.serialization.JsonOps;

import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.*;

/** Disposable-server coverage. No fixtures or fault controls are included in the gameplay JAR. */
final class CampaignAcceptance {
    static void register(RegisterCommandsEvent event) {
        var root =
                Commands.literal("wa_campaign_test")
                        .requires(Commands.hasPermission(Commands.LEVEL_OWNERS));
        for (String action :
                List.of(
                        "export",
                        "model",
                        "audit",
                        "players",
                        "delivery",
                        "work",
                        "restart",
                        "dragon"))
            root.then(
                    Commands.literal(action)
                            .executes(
                                    c -> {
                                        try {
                                            switch (action) {
                                                case "export" -> export(c.getSource().getServer());
                                                case "model" -> model(c.getSource().getServer());
                                                case "audit" -> audit(c.getSource().getServer());
                                                default ->
                                                        players(c.getSource().getServer(), action);
                                            }
                                            return 1;
                                        } catch (Exception | AssertionError e) {
                                            throw new IllegalStateException(
                                                    "WU21_CAMPAIGN_FAILED " + action, e);
                                        }
                                    }));
        root.then(
                Commands.literal("gateway")
                        .then(
                                Commands.argument(
                                                "index",
                                                com.mojang.brigadier.arguments.IntegerArgumentType
                                                        .integer(0, 19))
                                        .executes(
                                                c -> {
                                                    try {
                                                        gateway(
                                                                c.getSource().getServer(),
                                                                com.mojang.brigadier.arguments
                                                                        .IntegerArgumentType
                                                                        .getInteger(c, "index"));
                                                        return 1;
                                                    } catch (Exception | AssertionError e) {
                                                        throw new IllegalStateException(
                                                                "WU21_CAMPAIGN_FAILED gateway", e);
                                                    }
                                                })));
        event.getDispatcher().register(root);
    }

    private static void require(boolean value, String error) {
        if (!value) throw new AssertionError(error);
    }

    private static void gateway(MinecraftServer server, int index) throws Exception {
        var end = server.getLevel(net.minecraft.world.level.Level.END);
        var state = CellWorlds.state(end);
        var entry = new ArrayList<>(CellEndReservations.arrivalsByGateway().entrySet()).get(index);
        int credits = state.ledger.credits(0);
        var ready = state.ledger.ready();
        var origin =
                new net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity(
                        entry.getKey(), Blocks.END_GATEWAY.defaultBlockState());
        var result = CellEndArrivals.arrive(end, entry.getKey(), origin);
        require(
                result != null && Math.max(Math.abs(result.x), Math.abs(result.z)) > 500,
                "Gateway did not reach its declared island");
        require(
                com.gwsheridan.worldsunwritten.spawn.SafeSpawns.safe(
                        end, BlockPos.containing(result)),
                "Gateway destination is unsafe");
        require(
                credits == state.ledger.credits(0) && ready.equals(state.ledger.ready()),
                "Gateway travel awarded cell progress");
        System.out.println("WU21_GATEWAY_PASS index=" + index);
    }

    private static void model(MinecraftServer server) throws Exception {
        int loaded = server.overworld().getChunkSource().getLoadedChunksCount();
        var original = CellRecipes.create("Expanding One Chunk", server.registryAccess());
        var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        var old = CellWorldProfile.CODEC.encodeStart(ops, original).getOrThrow();
        require(
                !old.getAsJsonObject().has("campaign"),
                "Version-one profiles acquired campaign data");
        var canvas = new CellCanvas(original, server.getWorldGenSettings().options().seed());
        canvas.select(List.of(new Cell(1, 0), new Cell(2, 0)));
        canvas.paint("desert");
        canvas.copy(new Cell(1, 0));
        canvas.transformClipboard(false);
        canvas.paste(new Cell(0, 1));
        canvas.group("resource_route");
        var edited = canvas.profile();
        require(
                edited.version() == 2
                        && edited.campaign().orElseThrow().groups().getFirst().cells().size() == 2,
                "Canvas group was not saved");
        canvas.undo();
        canvas.redo();
        require(edited.equals(canvas.profile()), "Canvas history changed the layout");
        var encoded = CellWorldProfile.CODEC.encodeStart(ops, edited).getOrThrow();
        var decoded = CellWorldProfile.CODEC.parse(ops, encoded).getOrThrow();
        require(
                CellCampaign.canonical(
                                CellWorldProfile.CODEC.encodeStart(ops, decoded).getOrThrow())
                        .equals(CellCampaign.canonical(encoded)),
                "Campaign JSON definitions changed in codec round trip");
        var nbt = RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, server.registryAccess());
        var typed = CellWorldProfile.CODEC.encodeStart(nbt, edited).getOrThrow();
        require(
                edited.equals(CellWorldProfile.CODEC.parse(nbt, typed).getOrThrow()),
                "Typed campaign metadata changed in NBT round trip");
        for (var name : CampaignRecipes.NAMES) {
            var profile = CampaignRecipes.overworld(name, server.registryAccess());
            require(profile.validate().isSuccess(), "Campaign starter is invalid");
            var height = net.minecraft.world.level.LevelHeightAccessor.create(-64, 384);
            var generator =
                    new CellGenerator(
                            new com.gwsheridan.worldsunwritten.config.DimensionProfile(
                                    -64, 384, Optional.empty()),
                            profile);
            var occupied =
                    new HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
            for (var link : profile.campaign().orElseThrow().connections()) {
                var plan = CellConnectionPlan.create(generator, link, 0, height, 194723);
                require(
                        plan.problem().isEmpty(),
                        name + " connection " + link.id() + ": " + plan.problem());
                for (var e : plan.blocks().entrySet()) {
                    var before = occupied.putIfAbsent(e.getKey(), e.getValue());
                    require(
                            before == null || before == e.getValue(),
                            "Campaign connectors disagree at " + e.getKey());
                }
            }
            for (var site : profile.campaign().orElseThrow().sites())
                for (long seed = 0; seed < 8; seed++) {
                    var build =
                            com.gwsheridan.worldsunwritten.structure.CellSites.plan(
                                    site.assembly(), seed);
                    require(build.isPresent(), "Starter site has no complete layout");
                    build.get().checked(server.registryAccess());
                }
        }
        require(
                loaded == server.overworld().getChunkSource().getLoadedChunksCount(),
                "Detached campaign planning loaded chunks");
        System.out.println("WU21_CAMPAIGN_MODEL_PASS");
    }

    private static void export(MinecraftServer server) throws Exception {
        model(server);
        var dimensions =
                server.registryAccess()
                        .lookupOrThrow(Registries.WORLD_PRESET)
                        .getOrThrow(WorldPresets.NORMAL)
                        .value()
                        .createWorldDimensions();
        for (var name : CampaignRecipes.NAMES) {
            String slug = name.equals("Hex Frontier") ? "hex_frontier" : "one_chunk_to_the_end";
            var draft =
                    PresetStudio.capture(
                            PresetStudio.defaults(
                                    slug,
                                    "2.1.0",
                                    new net.minecraft.world.level.levelgen.WorldOptions(
                                            194723, true, false)),
                            dimensions,
                            server.registryAccess());
            draft = CampaignRecipes.apply(draft, name, server.registryAccess());
            PresetStudio.save(ModpackRuntime.directory(), slug, draft, server.registryAccess());
            System.out.println(
                    "WU21_CAMPAIGN_BUNDLE "
                            + slug
                            + " "
                            + PresetStudio.export(
                                    ModpackRuntime.directory(),
                                    slug,
                                    "2.1.0",
                                    server.registryAccess()));
        }
        var recovery =
                PresetStudio.capture(
                        PresetStudio.defaults(
                                "campaign_recovery",
                                "2.1.0",
                                new net.minecraft.world.level.levelgen.WorldOptions(
                                        194723, true, false)),
                        dimensions,
                        server.registryAccess());
        recovery = CampaignRecipes.apply(recovery, "One Chunk to the End", server.registryAccess());
        var overworld =
                dimensions
                        .dimensions()
                        .get(net.minecraft.world.level.dimension.LevelStem.OVERWORLD);
        recovery =
                ModpackProject.dimension(
                        recovery,
                        net.minecraft.world.level.dimension.LevelStem.OVERWORLD,
                        new net.minecraft.world.level.dimension.LevelStem(
                                overworld.type(),
                                new CellGenerator(
                                        new com.gwsheridan.worldsunwritten.config.DimensionProfile(
                                                -64, 384, Optional.empty()),
                                        CampaignRecoveryAcceptance.profile(server))),
                        server.registryAccess());
        PresetStudio.save(
                ModpackRuntime.directory(), "campaign_recovery", recovery, server.registryAccess());
        System.out.println(
                "WU21_RECOVERY_BUNDLE "
                        + PresetStudio.export(
                                ModpackRuntime.directory(),
                                "campaign_recovery",
                                "2.1.0",
                                server.registryAccess()));
        System.out.println("WU21_CAMPAIGN_EXPORT_PASS");
    }

    private static void audit(MinecraftServer server) throws Exception {
        int campaigns = 0;
        for (var level : server.getAllLevels())
            if (level.getChunkSource().getGenerator() instanceof CellGenerator generator
                    && generator.cells().version() == 2) {
                campaigns++;
                var state = CellWorlds.state(level);
                require(state != null, "Progressive campaign has no server ledger");
                require(
                        state.ledger.status(new CellGenerator.Key(0, Cell.ORIGIN))
                                == CellLedger.Status.READY,
                        "Campaign starting cell is unavailable");
                if (generator.campaignEnd()) {
                    require(
                            generator.alwaysOpenCells().stream()
                                    .allMatch(
                                            c ->
                                                    state.ledger.status(new CellGenerator.Key(0, c))
                                                            == CellLedger.Status.READY),
                            "Native arena became locked");
                    for (var p : CellEndReservations.arrivalsByGateway().values()) {
                        require(
                                level.getBlockState(p).is(Blocks.END_STONE),
                                "Gateway landing platform is missing");
                        require(
                                !CellWorlds.locked(level, p.above()),
                                "Native arrival was charged as a locked cell");
                    }
                    require(level.getDragonFight() != null, "Native dragon fight is missing");
                }
            }
        require(campaigns > 0, "No version-two campaign is installed");
        System.out.println("WU21_CAMPAIGN_AUDIT_PASS dimensions=" + campaigns);
    }

    private static int itemCount(net.minecraft.server.level.ServerPlayer player) {
        return player.getInventory().getNonEquipmentItems().stream()
                .filter(s -> s.is(net.minecraft.world.item.Items.OAK_LOG))
                .mapToInt(net.minecraft.world.item.ItemStack::getCount)
                .sum();
    }

    private static void players(MinecraftServer server, String action) throws Exception {
        var level = server.overworld();
        var state = CellWorlds.state(level);
        require(
                state != null && state.generator.cells().version() == 2,
                "Campaign journal missing");
        var players = server.getPlayerList().getPlayers();
        require(players.size() == 4, "Four connected real clients are required");
        for (var player : players) {
            require(
                    !server.getPlayerList().isOp(player.nameAndId()),
                    "Acceptance players must not be operators");
            require(
                    CellProgressionApi.progress(level, player).territory() == 0,
                    "Shared campaign allocated an owner slot");
        }
        if (action.equals("players")) {
            require(
                    players.stream().mapToInt(CampaignAcceptance::itemCount).sum() == 48,
                    "Supply each of the four players with exactly twelve oak logs");
            require(
                    state.ledger.completed(0).contains("wood"),
                    "Native advancement did not complete the first objective");
            for (var player : players) {
                int credits = state.ledger.credits(0);
                require(
                        server.getCommands()
                                        .getDispatcher()
                                        .execute(
                                                "worldsunwritten cells choose_confirm"
                                                        + " minecraft:the_end 0 1 0 1",
                                                player.createCommandSourceStack())
                                == 0,
                        "A stale dimension confirmation spent credits");
                require(
                        server.getCommands()
                                        .getDispatcher()
                                        .execute(
                                                "worldsunwritten cells submit_confirm"
                                                        + " minecraft:overworld 0 delivery"
                                                        + " minecraft:diamond 8",
                                                player.createCommandSourceStack())
                                == 0,
                        "A forged delivery item was accepted");
                require(itemCount(player) == 12, "A refused confirmation consumed inventory");
                try {
                    CellProgressionApi.signal(
                            level,
                            player,
                            net.minecraft.resources.Identifier.parse(
                                    "worldsunwritten:delivery/delivery"));
                    throw new AssertionError("An external signal forged a delivery");
                } catch (IllegalArgumentException expected) {
                }
                require(credits == state.ledger.credits(0), "A rejected signal changed credits");
                try {
                    server.getCommands()
                            .getDispatcher()
                            .execute(
                                    "worldsunwritten cells admin unlock 0 2 2",
                                    player.createCommandSourceStack());
                    throw new AssertionError("A regular player changed cell administration");
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) {
                }
            }
            var key = new CellGenerator.Key(0, Cell.ORIGIN);
            var marker = state.generator.spawn(key, level).above(7);
            require(
                    level.setBlock(marker, Blocks.EMERALD_BLOCK.defaultBlockState(), 2),
                    "Could not save the player build marker");
        } else if (action.equals("delivery") || action.equals("restart")) {
            require(
                    players.stream().mapToInt(CampaignAcceptance::itemCount).sum() == 40,
                    "Concurrent submissions consumed the wrong number of logs");
            require(
                    state.ledger.completed(0).contains("delivery"),
                    "Verified delivery did not pay");
            require(
                    state.ledger.pendingDeliveries().isEmpty(),
                    "A normal delivery remained pending");
            int paid = 0;
            for (var p : players) paid += state.ledger.deliveries(p.getUUID()).size();
            require(paid == 1, "The shared delivery paid more than once");
            for (var player : players) {
                int count = itemCount(player), credits = state.ledger.credits(0);
                try {
                    CellProgressionApi.submit(level, player, "delivery");
                    throw new AssertionError("A duplicate delivery succeeded");
                } catch (IllegalArgumentException expected) {
                }
                require(
                        count == itemCount(player) && credits == state.ledger.credits(0),
                        "A duplicate delivery consumed items or paid credits");
            }
            var marker =
                    state.generator.spawn(new CellGenerator.Key(0, Cell.ORIGIN), level).above(7);
            require(
                    level.getBlockState(marker).is(Blocks.EMERALD_BLOCK),
                    "A saved player build changed");
        } else if (action.equals("work")) {
            if (!state.ledger.pending().isEmpty()) {
                System.out.println("WU21_WORK_WAIT cells=" + state.ledger.pending().size());
                return;
            }
            require(
                    state.ledger.cells().values().stream()
                            .noneMatch(v -> v == CellLedger.Status.CONFLICT),
                    "A campaign cell conflicted");
            int ready = 0;
            for (var link : state.generator.cells().campaign().orElseThrow().connections())
                if (state.ledger.status(new CellGenerator.Key(0, link.from()))
                                == CellLedger.Status.READY
                        && state.ledger.status(new CellGenerator.Key(0, link.to()))
                                == CellLedger.Status.READY) {
                    var row = state.ledger.connections().get("0:" + link.id());
                    if (row == null
                            || row.status() == CellLedger.Status.QUEUED
                            || row.status() == CellLedger.Status.APPLYING) {
                        System.out.println("WU21_WORK_WAIT connection=" + link.id());
                        return;
                    }
                    require(
                            row.status() == CellLedger.Status.READY,
                            "A connector conflicted: " + row.problem());
                    var plan =
                            CellConnectionPlan.create(
                                    state.generator, link, 0, level, level.getSeed());
                    for (var e : plan.blocks().entrySet())
                        require(
                                level.getBlockState(e.getKey()) == e.getValue(),
                                "A ready connection changed at " + e.getKey());
                    ready++;
                }
            for (var key : state.ledger.ready()) {
                if (key.territory() != 0) continue;
                var tile = state.generator.cells().tile(level.getSeed(), 0, key.cell());
                var build = state.generator.build(tile, key);
                if (build.isEmpty()) continue;
                var origin = state.generator.origin(tile, key);
                if (tile.id().equals("portal")) {
                    var frames =
                            build.orElseThrow().cells().stream()
                                    .filter(c -> c.state().is(Blocks.END_PORTAL_FRAME))
                                    .toList();
                    require(frames.size() == 12, "The campaign portal needs twelve frames");
                    for (var frame : frames)
                        level.setBlock(
                                origin.offset(frame.pos()),
                                frame.state()
                                        .setValue(
                                                net.minecraft.world.level.block.EndPortalFrameBlock
                                                        .HAS_EYE,
                                                true),
                                2);
                    try {
                        require(
                                net.minecraft.world.level.block.EndPortalFrameBlock
                                                .getOrCreatePortalShape()
                                                .find(level, origin.offset(frames.getFirst().pos()))
                                        != null,
                                "Campaign portal frame facings do not match the native activation"
                                    + " pattern");
                    } finally {
                        for (var frame : frames)
                            level.setBlock(origin.offset(frame.pos()), frame.state(), 2);
                    }
                }
                require(
                        com.gwsheridan.worldsunwritten.spawn.SafeSpawns.safe(
                                level, state.generator.spawn(key, level)),
                        "Unsafe saved campaign marker");
                for (var cell : build.get().cells())
                    if (cell.data().isPresent()) {
                        var entity = level.getBlockEntity(origin.offset(cell.pos()));
                        require(entity != null, "Campaign metadata missing");
                        if (tile.id().equals("ruins")) {
                            require(
                                    entity instanceof net.minecraft.world.Container
                                            && ((net.minecraft.world.Container) entity).isEmpty(),
                                    "Procedural containers retained inventory");
                            require(
                                    entity.saveWithoutMetadata(server.registryAccess())
                                            .get("CustomName")
                                            .equals(cell.data().orElseThrow().get("CustomName")),
                                    "Procedural decorative metadata changed");
                        }
                    }
            }
            System.out.println("WU21_WORK_READY connections=" + ready);
            return;
        } else if (action.equals("dragon")) {
            var end = server.getLevel(net.minecraft.world.level.Level.END);
            var progress = CellWorlds.state(end);
            require(
                    progress.ledger.completed(0).contains("dragon"),
                    "Native dragon kill did not pay its objective");
            require(
                    progress.ledger.credits(0) == 10,
                    "Repeated dragon kills or gateway travel changed the shared reward");
            for (var player : players)
                try {
                    CellProgressionApi.signal(end, player, CellWorlds.DRAGON_DEFEATED);
                    throw new AssertionError("Quest API forged a dragon kill");
                } catch (IllegalArgumentException expected) {
                }
        }
        System.out.println("WU21_" + action.toUpperCase(Locale.ROOT) + "_PASS");
    }
}
