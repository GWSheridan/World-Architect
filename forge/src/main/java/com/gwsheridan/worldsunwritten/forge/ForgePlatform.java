package com.gwsheridan.worldsunwritten.forge;

import com.gwsheridan.worldsunwritten.platform.AdapterLookup;
import com.gwsheridan.worldsunwritten.platform.DeliveryData;
import com.gwsheridan.worldsunwritten.platform.Platform;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.RegistryManager;
import net.minecraftforge.resource.ResourcePackLoader;

enum ForgePlatform implements Platform {
    INSTANCE;

    @Override
    public String loaderId() {
        return "forge";
    }

    @Override
    public Path configDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public Map<String, String> loadedMods() {
        var result = new TreeMap<String, String>();
        ModList.getMods().forEach(mod -> result.put(mod.getModId(), mod.getVersion().toString()));
        return result;
    }

    @Override
    public Collection<String> builtInDataPacks() {
        var accepted = new java.util.HashSet<>(ResourcePackLoader.getPackNames());
        accepted.add("vanilla");
        return accepted;
    }

    @Override
    public Structure.StructureSettings structureSettings(Structure structure) {
        return structure.getModifiedStructureSettings();
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ForgePayloads.channel().send(payload, PacketDistributor.PLAYER.with(player));
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ForgePayloads.channel().send(payload, PacketDistributor.SERVER.noArg());
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
        return new AdapterLookup<>(id -> Optional.ofNullable(RegistryManager.ACTIVE.getRegistry(key).getValue(id)),
                () -> RegistryManager.ACTIVE.getRegistry(key).getKeys());
    }
}
