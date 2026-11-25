package com.gwsheridan.worldsunwritten.forge;

import com.gwsheridan.worldsunwritten.WorldsUnwritten;
import com.gwsheridan.worldsunwritten.api.DimensionAdapterRegistry;
import com.gwsheridan.worldsunwritten.api.FeatureAdapters;
import com.gwsheridan.worldsunwritten.api.StructureAdapters;
import com.gwsheridan.worldsunwritten.commands.AllCommands;
import com.gwsheridan.worldsunwritten.commands.CommandRegistration;
import com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyCommands;
import com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyIds;
import com.gwsheridan.worldsunwritten.modpack.ModpackRuntime;
import com.gwsheridan.worldsunwritten.platform.Platform;
import com.gwsheridan.worldsunwritten.platform.WorldgenRegistration;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.NewRegistryEvent;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.RegistryBuilder;

@Mod(WorldsUnwritten.ID)
public final class ForgeEntrypoint {
    public ForgeEntrypoint(FMLJavaModLoadingContext context) {
        Platform.install(ForgePlatform.INSTANCE);
        NewRegistryEvent.BUS.addListener(event -> {
            createRegistry(event, DimensionAdapterRegistry.KEY);
            createRegistry(event, FeatureAdapters.KEY);
            createRegistry(event, StructureAdapters.KEY);
        });
        RegisterEvent.getBus(context.getModBusGroup()).addListener(event ->
                WorldgenRegistration.register(new Registration(event, WorldsUnwritten.ID), false));
        RegisterCommandsEvent.BUS.addListener(event -> {
            var registration = new CommandRegistration(event.getDispatcher(), event.getBuildContext());
            AllCommands.register(registration);
            LegacyCommands.register(registration);
        });
        AddPackFindersEvent.BUS.addListener(event -> {
            if (event.getPackType() == PackType.SERVER_DATA) ModpackRuntime.bundlePackSource().ifPresent(event::addRepositorySource);
        });
        ForgeEvents.register();
        ForgePayloads.register();
        if (FMLEnvironment.dist.isClient()) ForgeClient.initialize();
    }

    private static <T> void createRegistry(NewRegistryEvent event, ResourceKey<Registry<T>> key) {
        event.create(RegistryBuilder.<T>of(key.identifier()).disableSaving().disableSync());
    }

    @Mod(LegacyIds.NAMESPACE)
    public static final class Legacy {
        public Legacy(FMLJavaModLoadingContext context) {
            RegisterEvent.getBus(context.getModBusGroup()).addListener(event ->
                    WorldgenRegistration.register(new Registration(event, LegacyIds.NAMESPACE), true));
        }
    }

    private record Registration(RegisterEvent event, String namespace) implements WorldgenRegistration.Registrar {
        @Override
        public <T> void register(ResourceKey<? extends Registry<T>> key, String name, Supplier<? extends T> value) {
            event.register(key, Identifier.fromNamespaceAndPath(namespace, name), value::get);
        }
    }
}
