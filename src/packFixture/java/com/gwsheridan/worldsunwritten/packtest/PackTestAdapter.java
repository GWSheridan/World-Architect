package com.gwsheridan.worldsunwritten.packtest;

import com.gwsheridan.worldsunwritten.api.DimensionAdapter;
import com.gwsheridan.worldsunwritten.api.DimensionAdapterRegistry;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;

final class PackTestAdapter implements DimensionAdapter {
    static final Identifier ID = Identifier.parse("wa_pack_test:external_dimension");

    @Override
    public int version() {
        return 1;
    }

    @Override
    public Support inspect(ResourceKey<LevelStem> dimension, LevelStem original) {
        return new Support(Set.of(TerrainMode.SINGLE_BLOCK), Optional.empty(), Component.empty());
    }

    static void verifyRegistration() {
        if (DimensionAdapterRegistry.get(ID).filter(PackTestAdapter.class::isInstance).isEmpty()) {
            throw new IllegalStateException("Pack test dimension adapter did not register");
        }
    }
}
