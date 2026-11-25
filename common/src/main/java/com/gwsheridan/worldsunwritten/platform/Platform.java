package com.gwsheridan.worldsunwritten.platform;

import com.gwsheridan.worldsunwritten.api.DimensionAdapter;
import com.gwsheridan.worldsunwritten.api.FeatureAdapter;
import com.gwsheridan.worldsunwritten.api.StructureAdapter;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

public interface Platform {
    String loaderId();

    Path configDirectory();

    Map<String, String> loadedMods();

    Collection<String> builtInDataPacks();

    default List<String> childDataPacks(Pack parent) {
        return List.of();
    }

    Structure.StructureSettings structureSettings(Structure structure);

    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    void sendToServer(CustomPacketPayload payload);

    CompoundTag deliveryData(ServerPlayer player);

    String deliveryDataTag();

    <T> AdapterLookup<T> createAdapterRegistry(ResourceKey<Registry<T>> key);

    default Optional<DimensionAdapter> dimensionAdapter(Identifier id, Optional<DimensionAdapter> current) {
        return current;
    }

    default Optional<FeatureAdapter> featureAdapter(Identifier id, Optional<FeatureAdapter> current) {
        return current;
    }

    default Optional<StructureAdapter> structureAdapter(Identifier id, Optional<StructureAdapter> current) {
        return current;
    }

    default void validateAdapters() {}

    static Platform get() {
        return Objects.requireNonNull(Installed.platform, "Worlds Unwritten platform has not initialized");
    }

    static void install(Platform platform) {
        if (Installed.platform != null && Installed.platform != platform) {
            throw new IllegalStateException("Worlds Unwritten platform already initialized");
        }
        Installed.platform = Objects.requireNonNull(platform);
    }

    final class Installed {
        private static volatile Platform platform;

        private Installed() {}
    }
}
