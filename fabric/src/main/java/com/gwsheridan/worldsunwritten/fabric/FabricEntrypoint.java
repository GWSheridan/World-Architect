package com.gwsheridan.worldsunwritten.fabric;

import com.gwsheridan.worldsunwritten.WorldsUnwritten;
import com.gwsheridan.worldsunwritten.api.DimensionAdapterRegistry;
import com.gwsheridan.worldsunwritten.api.FeatureAdapters;
import com.gwsheridan.worldsunwritten.api.StructureAdapters;
import com.gwsheridan.worldsunwritten.cells.CellWorlds;
import com.gwsheridan.worldsunwritten.commands.AllCommands;
import com.gwsheridan.worldsunwritten.commands.CommandRegistration;
import com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyCommands;
import com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyIds;
import com.gwsheridan.worldsunwritten.environment.EnvironmentPayload;
import com.gwsheridan.worldsunwritten.environment.WorldEnvironment;
import com.gwsheridan.worldsunwritten.platform.Platform;
import com.gwsheridan.worldsunwritten.platform.ServerEvents;
import com.gwsheridan.worldsunwritten.platform.WorldgenRegistration;
import com.gwsheridan.worldsunwritten.skyblock.IslandProtection;
import com.gwsheridan.worldsunwritten.skyblock.IslandSelections;
import com.gwsheridan.worldsunwritten.structure.PlacementPreviewPayload;
import com.gwsheridan.worldsunwritten.studio.StudioViewPayload;
import com.gwsheridan.worldsunwritten.workbench.WorkbenchEditPayload;
import com.gwsheridan.worldsunwritten.workbench.WorkbenchStudio;
import java.util.function.Supplier;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

public final class FabricEntrypoint implements ModInitializer {
    @Override
    public void onInitialize() {
        Platform.install(FabricPlatform.INSTANCE);
        WorldgenRegistration.register(new Registrations(WorldsUnwritten.ID), false);
        WorldgenRegistration.register(new Registrations(LegacyIds.NAMESPACE), true);
        DimensionAdapterRegistry.initialize();
        FeatureAdapters.initialize();
        StructureAdapters.initialize();

        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
            var registration = new CommandRegistration(dispatcher, context);
            AllCommands.register(registration);
            LegacyCommands.register(registration);
        });
        ServerLifecycleEvents.SERVER_STARTING.register(ServerEvents::starting);
        ServerLifecycleEvents.SERVER_STOPPED.register(CellWorlds::stopped);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            CellWorlds.tick(server);
            server.getPlayerList().getPlayers().forEach(IslandSelections::tick);
        });
        ServerPlayerEvents.JOIN.register(ServerEvents::joined);
        ServerPlayerEvents.LEAVE.register(ServerEvents::left);
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, player, alive) -> WorldEnvironment.respawned(player));
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, from, to) -> WorldEnvironment.dimensionChanged(player));
        PlayerBlockBreakEvents.BEFORE.register((level, actor, pos, state, entity) -> !(actor instanceof ServerPlayer player)
                || !CellWorlds.denied(player, pos) && !IslandProtection.breakDenied(player, pos, state));
        UseBlockCallback.EVENT.register((actor, level, hand, hit) -> {
            if (actor instanceof ServerPlayer player && (CellWorlds.denied(player, hit.getBlockPos())
                    || IslandProtection.interactionDenied(player, hit.getBlockPos()))) return InteractionResult.FAIL;
            return InteractionResult.PASS;
        });

        PayloadTypeRegistry.clientboundPlay().register(EnvironmentPayload.TYPE, EnvironmentPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PlacementPreviewPayload.TYPE, PlacementPreviewPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(StudioViewPayload.TYPE, StudioViewPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(WorkbenchEditPayload.TYPE, WorkbenchEditPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(WorkbenchEditPayload.TYPE,
                (payload, context) -> WorkbenchStudio.handle(context.player(), payload));
    }

    private record Registrations(String namespace) implements WorldgenRegistration.Registrar {
        @Override
        @SuppressWarnings("unchecked")
        public <T> void register(ResourceKey<? extends Registry<T>> key, String name, Supplier<? extends T> value) {
            Registry<T> registry = (Registry<T>) BuiltInRegistries.REGISTRY.getValue(key.identifier());
            Registry.register(registry, Identifier.fromNamespaceAndPath(namespace, name), value.get());
        }
    }
}
