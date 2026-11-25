package com.gwsheridan.worldsunwritten.platform;

import com.gwsheridan.worldsunwritten.api.DimensionAdapter;
import com.gwsheridan.worldsunwritten.api.DimensionAdapterRegistry;
import com.gwsheridan.worldsunwritten.api.FeatureAdapter;
import com.gwsheridan.worldsunwritten.api.FeatureAdapters;
import com.gwsheridan.worldsunwritten.api.StructureAdapter;
import com.gwsheridan.worldsunwritten.api.StructureAdapters;
import com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyAdapters;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.RegistryBuilder;

public enum NeoForgePlatform implements Platform {
    INSTANCE;

    private final List<Registry<?>> adapterRegistries = new java.util.ArrayList<>();

    @Override
    public String loaderId() {
        return "neoforge";
    }

    @Override
    public Path configDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public Map<String, String> loadedMods() {
        var versions = new TreeMap<String, String>();
        ModList.get().getMods().forEach(mod -> versions.put(mod.getModId(), mod.getVersion().toString()));
        return versions;
    }

    @Override
    public Collection<String> builtInDataPacks() {
        return CommonHooks.getModDataPacksWithVanilla();
    }

    @Override
    public List<String> childDataPacks(Pack parent) {
        return parent.getChildren().stream().map(Pack::getId).toList();
    }

    @Override
    public Structure.StructureSettings structureSettings(Structure structure) {
        return structure.getModifiedStructureSettings();
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }

    @Override
    public CompoundTag deliveryData(ServerPlayer player) {
        return player.getPersistentData();
    }

    @Override
    public String deliveryDataTag() {
        return "NeoForgeData";
    }

    @Override
    public <T> AdapterLookup<T> createAdapterRegistry(ResourceKey<Registry<T>> key) {
        var registry = new RegistryBuilder<>(key).create();
        adapterRegistries.add(registry);
        return AdapterLookup.from(registry);
    }

    void registerAdapters(net.neoforged.neoforge.registries.NewRegistryEvent event) {
        DimensionAdapterRegistry.initialize();
        FeatureAdapters.initialize();
        StructureAdapters.initialize();
        adapterRegistries.forEach(event::register);
    }

    @Override
    public Optional<DimensionAdapter> dimensionAdapter(Identifier id, Optional<DimensionAdapter> current) {
        return LegacyAdapters.dimension(id, current);
    }

    @Override
    public Optional<FeatureAdapter> featureAdapter(Identifier id, Optional<FeatureAdapter> current) {
        return LegacyAdapters.feature(id, current);
    }

    @Override
    public Optional<StructureAdapter> structureAdapter(Identifier id, Optional<StructureAdapter> current) {
        return LegacyAdapters.structure(id, current);
    }

    @Override
    public void validateAdapters() {
        LegacyAdapters.validate();
    }
}
