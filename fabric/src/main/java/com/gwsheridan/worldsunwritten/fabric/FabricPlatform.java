package com.gwsheridan.worldsunwritten.fabric;

import com.gwsheridan.worldsunwritten.compatibility.mixin.StructureSettingsAccess;
import com.gwsheridan.worldsunwritten.platform.DeliveryData;
import com.gwsheridan.worldsunwritten.platform.AdapterLookup;
import com.gwsheridan.worldsunwritten.platform.Platform;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.Structure;

enum FabricPlatform implements Platform {
    INSTANCE;

    @Override
    public String loaderId() {
        return "fabric";
    }

    @Override
    public Path configDirectory() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public Map<String, String> loadedMods() {
        var mods = new TreeMap<String, String>();
        for (var container : FabricLoader.getInstance().getAllMods()) {
            var metadata = container.getMetadata();
            String version = metadata.getVersion().getFriendlyString();
            mods.put(metadata.getId(), version);
            metadata.getProvides().forEach(id -> mods.putIfAbsent(id, version));
        }
        return mods;
    }

    @Override
    public Collection<String> builtInDataPacks() {
        var packs = new java.util.HashSet<>(java.util.Set.of("vanilla", "fabric"));
        for (String id : loadedMods().keySet()) {
            packs.add(id);
            packs.add("mod/" + id);
        }
        return packs;
    }

    @Override
    public Structure.StructureSettings structureSettings(Structure structure) {
        return ((StructureSettingsAccess) structure).worldsunwritten$settings();
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }

    @Override
    public CompoundTag deliveryData(ServerPlayer player) {
        return ((DeliveryData) player).worldsunwritten$deliveryData();
    }

    @Override
    public String deliveryDataTag() {
        return DeliveryData.KEY;
    }

    @Override
    public <T> AdapterLookup<T> createAdapterRegistry(ResourceKey<Registry<T>> key) {
        return AdapterLookup.from(FabricRegistryBuilder.create(key).buildAndRegister());
    }
}
