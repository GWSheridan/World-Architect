package com.gwsheridan.worldsunwritten.testing;

import com.gwsheridan.worldsunwritten.api.CellLayout;
import com.gwsheridan.worldsunwritten.api.CellLayout.Cell;
import com.gwsheridan.worldsunwritten.cells.*;
import com.gwsheridan.worldsunwritten.worldgen.CellGenerator;

import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.nio.file.*;
import java.util.*;

/** Abrupt-exit fixtures. The gameplay mod contains no fault injection switches. */
final class CampaignRecoveryAcceptance {
    private static final UUID PLAYER = UUID.fromString("6cb906ad-58df-4488-adf8-644c730db36b");

    static void register(RegisterCommandsEvent event) {
        event.getDispatcher()
                .register(
                        Commands.literal("wa_campaign_recovery")
                                .requires(
                                        s ->
                                                s.getEntity() == null
                                                        && Commands.hasPermission(
                                                                        Commands.LEVEL_OWNERS)
                                                                .test(s))
                                .then(
                                        Commands.argument(
                                                        "mode",
                                                        com.mojang.brigadier.arguments
                                                                .StringArgumentType.word())
                                                .then(
                                                        Commands.argument(
                                                                        "stage",
                                                                        com.mojang.brigadier
                                                                                .arguments
                                                                                .IntegerArgumentType
                                                                                .integer(1, 2))
                                                                .executes(
                                                                        c -> {
                                                                            try {
                                                                                check(
                                                                                        c.getSource()
                                                                                                .getServer(),
                                                                                        com.mojang
                                                                                                .brigadier
                                                                                                .arguments
                                                                                                .StringArgumentType
                                                                                                .getString(
                                                                                                        c,
                                                                                                        "mode"),
                                                                                        com.mojang
                                                                                                .brigadier
                                                                                                .arguments
                                                                                                .IntegerArgumentType
                                                                                                .getInteger(
                                                                                                        c,
                                                                                                        "stage"));
                                                                                return 1;
                                                                            } catch (Throwable e) {
                                                                                e.printStackTrace();
                                                                                throw new IllegalStateException(
                                                                                        "WU21_RECOVERY_FAILED",
                                                                                        e);
                                                                            }
                                                                        }))));
    }

    private static void require(boolean value, String problem) {
        if (!value) throw new AssertionError(problem);
    }

    static CellWorldProfile profile(MinecraftServer server) throws Exception {
        var base = CampaignRecipes.overworld("Hex Frontier", server.registryAccess());
        var start =
                base.tiles().stream()
                        .filter(t -> t.id().equals(base.start()))
                        .findFirst()
                        .orElseThrow();
        start =
                new CellWorldProfile.Tile(
                        start.id(),
                        start.title(),
                        1,
                        start.biome(),
                        start.kind(),
                        start.terrain(),
                        start.content(),
                        start.template());
        var delivery =
                new CellCampaign.Delivery(
                        "delivery",
                        net.minecraft.resources.Identifier.parse("minecraft:oak_log"),
                        8);
        var goal =
                new GrowthRules.Goal(
                        "delivery",
                        "Supply timber",
                        GrowthRules.Trigger.SIGNAL,
                        delivery.signal(),
                        List.of(),
                        false,
                        List.of(),
                        2);
        var link =
                new CellCampaign.Connection(
                        "route",
                        Cell.ORIGIN,
                        new Cell(1, 0),
                        "path",
                        71,
                        1,
                        Blocks.OAK_PLANKS.defaultBlockState(),
                        "east",
                        "west");
        return new CellWorldProfile(
                        new CellLayout(CellLayout.Shape.SQUARE, 32, 6, 0),
                        0,
                        start.id(),
                        List.of(start),
                        List.of(),
                        base.border(),
                        false,
                        new GrowthRules(
                                GrowthRules.Scope.SHARED, 2, 64, 16384, true, List.of(goal)),
                        Optional.of(
                                new CellCampaign(
                                        List.of(),
                                        Map.of(),
                                        List.of(delivery),
                                        List.of(link),
                                        List.of())))
                .validate()
                .getOrThrow();
    }

    private static Object invoke(String method, Class<?> parameter, Object argument)
            throws Exception {
        var call = CellDeliveries.class.getDeclaredMethod(method, parameter);
        call.setAccessible(true);
        return call.invoke(null, argument);
    }

    private static void check(MinecraftServer server, String mode, int stage) throws Exception {
        var level = server.overworld();
        var state = CellWorlds.state(level);
        require(
                state != null && state.generator.cells().version() == 2,
                "Recovery fixture requires a campaign");
        if (mode.startsWith("delivery_")) {
            if (stage == 1) {
                var player =
                        net.neoforged.neoforge.common.util.FakePlayerFactory.get(
                                level,
                                new com.mojang.authlib.GameProfile(PLAYER, "ReceiptFixture"));
                player.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 12));
                var saved =
                        (CompoundTag)
                                invoke(
                                        "save",
                                        net.minecraft.server.level.ServerPlayer.class,
                                        player);
                String fingerprint = (String) invoke("inventory", CompoundTag.class, saved);
                var claim = state.ledger.beginDelivery(0, "delivery", PLAYER, fingerprint);
                if (!mode.equals("delivery_prepared")) {
                    var receipt = new CompoundTag();
                    receipt.putString("transaction", claim.transaction());
                    receipt.putString("dimension", level.dimension().identifier().toString());
                    player.getPersistentData().put("worldsunwritten_delivery", receipt);
                    player.getInventory().getItem(0).shrink(8);
                    invoke("save", net.minecraft.server.level.ServerPlayer.class, player);
                    if (mode.equals("delivery_rewarded"))
                        state.ledger.observe(
                                0, GrowthRules.Trigger.SIGNAL, "worldsunwritten:delivery/delivery");
                    if (mode.equals("delivery_paid") || mode.equals("delivery_cleared"))
                        state.ledger.completeDelivery(claim);
                    if (mode.equals("delivery_cleared")) {
                        player.getPersistentData().remove("worldsunwritten_delivery");
                        invoke("save", net.minecraft.server.level.ServerPlayer.class, player);
                    }
                }
                halt(mode);
            } else {
                if (!state.ledger.pendingDeliveries().isEmpty()) {
                    System.out.println("WU21_RECOVERY_WAIT " + mode);
                    return;
                }
                var file =
                        server.getWorldPath(
                                        net.minecraft.world.level.storage.LevelResource
                                                .PLAYER_DATA_DIR)
                                .resolve(PLAYER + ".dat");
                var saved = NbtIo.readCompressed(file, NbtAccounter.create(16 * 1024 * 1024));
                int logs = 0;
                for (var value : saved.getListOrEmpty("Inventory")) {
                    var stack =
                            ItemStack.CODEC
                                    .parse(
                                            net.minecraft.resources.RegistryOps.create(
                                                    NbtOps.INSTANCE, server.registryAccess()),
                                            value)
                                    .getOrThrow();
                    if (stack.is(Items.OAK_LOG)) logs += stack.getCount();
                }
                boolean consumed = !mode.equals("delivery_prepared");
                require(logs == (consumed ? 4 : 12), "Crash recovery lost or duplicated items");
                require(
                        state.ledger.completed(0).contains("delivery") == consumed
                                && state.ledger.credits(0) == (consumed ? 2 : 0),
                        "Crash recovery lost or duplicated a reward");
                require(
                        state.ledger.pendingDeliveries().isEmpty(),
                        "Offline pending claim did not reconcile");
            }
        } else if (mode.startsWith("connection_")) {
            var link = state.generator.cells().campaign().orElseThrow().connections().getFirst();
            var plan = CellConnectionPlan.create(state.generator, link, 0, level, level.getSeed());
            require(plan.problem().isEmpty(), plan.problem());
            var key = new CellGenerator.Key(0, link.to());
            if (stage == 1) {
                state.ledger.queue(key);
                var type = Class.forName("com.gwsheridan.worldsunwritten.cells.CellActivation");
                var constructor =
                        type.getDeclaredConstructor(
                                net.minecraft.server.level.ServerLevel.class,
                                CellGenerator.class,
                                CellLedger.class,
                                CellGenerator.Key.class);
                constructor.setAccessible(true);
                var activation = constructor.newInstance(level, state.generator, state.ledger, key);
                var tick = type.getDeclaredMethod("tick", int.class);
                tick.setAccessible(true);
                while (!(Boolean) tick.invoke(activation, 16384)) {}
                require(
                        state.ledger.status(key) == CellLedger.Status.READY,
                        "Recovery endpoint did not activate");
                state.ledger.connection(
                        0, link.id(), null, CellLedger.Status.QUEUED, plan.fingerprint(), "");
                if (!mode.equals("connection_queued")) {
                    state.ledger.connection(
                            0,
                            link.id(),
                            CellLedger.Status.QUEUED,
                            CellLedger.Status.APPLYING,
                            plan.fingerprint(),
                            "");
                    int n = 0;
                    for (var e : plan.blocks().entrySet()) {
                        if (!mode.equals("connection_flushed") && n++ >= 3) break;
                        level.getChunkAt(e.getKey()).setBlockState(e.getKey(), e.getValue(), 2);
                    }
                    if (mode.equals("connection_conflict")) {
                        var pos = plan.blocks().keySet().stream().skip(6).findFirst().orElseThrow();
                        level.getChunkAt(pos)
                                .setBlockState(pos, Blocks.GOLD_BLOCK.defaultBlockState(), 2);
                    }
                }
                require(
                        server.saveEverything(true, true, true),
                        "Cannot flush partial connection fixture");
                halt(mode);
            } else {
                var row = state.ledger.connections().get("0:" + link.id());
                if (row == null
                        || row.status() == CellLedger.Status.QUEUED
                        || row.status() == CellLedger.Status.APPLYING) {
                    System.out.println("WU21_RECOVERY_WAIT " + mode);
                    return;
                }
                if (mode.equals("connection_conflict")) {
                    require(
                            row.status() == CellLedger.Status.CONFLICT,
                            "Unexpected build did not stop recovery");
                    var pos = plan.blocks().keySet().stream().skip(6).findFirst().orElseThrow();
                    require(
                            level.getBlockState(pos).is(Blocks.GOLD_BLOCK),
                            "Recovery overwrote a player build");
                } else {
                    require(row.status() == CellLedger.Status.READY, "Connection did not recover");
                    for (var e : plan.blocks().entrySet())
                        require(
                                level.getBlockState(e.getKey()) == e.getValue(),
                                "Partial corridor was not repaired");
                }
            }
        } else if (mode.startsWith("gateway_")) {
            var end = server.getLevel(net.minecraft.world.level.Level.END);
            var entry = CellEndReservations.arrivalsByGateway().entrySet().iterator().next();
            var frame = CellEndArrivals.frame(entry.getValue().offset(-2, 10, -2));
            var obstacle = entry.getValue().offset(-2, 10, -2);
            if (stage == 1) {
                int n = 0;
                for (var e : frame.entrySet()) {
                    if (n++ >= 5) break;
                    end.getChunkAt(e.getKey()).setBlockState(e.getKey(), e.getValue(), 2);
                }
                if (mode.equals("gateway_blocked"))
                    require(
                            end.setBlock(obstacle, Blocks.GOLD_BLOCK.defaultBlockState(), 2)
                                    && end.getBlockState(obstacle).is(Blocks.GOLD_BLOCK),
                            "Could not place the gateway obstruction fixture");
                require(server.saveEverything(true, true, true), "Cannot flush gateway fixture");
                halt(mode);
            } else {
                if (mode.equals("gateway_blocked"))
                    require(
                            !end.hasChunk(obstacle.getX() >> 4, obstacle.getZ() >> 4),
                            "The obstruction test must start with its saved chunk unloaded");
                var progress = CellWorlds.state(end);
                var before = progress.ledger.ready();
                var origin =
                        new net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity(
                                entry.getKey(), Blocks.END_GATEWAY.defaultBlockState());
                var result = CellEndArrivals.arrive(end, entry.getKey(), origin);
                require(
                        result != null
                                && com.gwsheridan.worldsunwritten.spawn.SafeSpawns.safe(
                                        end, BlockPos.containing(result)),
                        "Gateway recovery has no safe destination");
                if (mode.equals("gateway_blocked")) {
                    require(
                            Math.max(Math.abs(result.x), Math.abs(result.z)) < 256,
                            "Blocked gateway did not fall back to the native arena");
                    require(
                            end.getBlockState(obstacle).is(Blocks.GOLD_BLOCK),
                            "Gateway recovery overwrote a build");
                } else {
                    require(
                            Math.max(Math.abs(result.x), Math.abs(result.z)) > 500,
                            "Partial gateway did not recover its outer destination");
                    for (var e : frame.entrySet())
                        require(
                                end.getBlockState(e.getKey()) == e.getValue(),
                                "Native frame recovery was incomplete");
                }
                require(
                        progress.ledger.credits(0) == 0 && progress.ledger.ready().equals(before),
                        "Gateway recovery awarded cells");
            }
        } else throw new IllegalArgumentException("Unknown recovery fixture");
        System.out.println("WU21_RECOVERY_PASS " + mode);
    }

    private static void halt(String mode) {
        System.out.println("WU21_RECOVERY_CHECKPOINT " + mode);
        System.out.flush();
        Runtime.getRuntime().halt(73);
    }
}
