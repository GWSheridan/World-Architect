package com.gwsheridan.worldsunwritten.compatibility.legacy;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

import com.gwsheridan.worldsunwritten.platform.NeoForgeRegistrations;
import com.gwsheridan.worldsunwritten.platform.WorldgenRegistration;
@Mod(LegacyIds.NAMESPACE)
public final class LegacySupport {
    public LegacySupport(IEventBus bus) {
        WorldgenRegistration.register(new NeoForgeRegistrations(bus, LegacyIds.NAMESPACE), true);
        bus.addListener(com.gwsheridan.worldarchitect.api.DimensionAdapterRegistry::registerRegistry);
        bus.addListener(com.gwsheridan.worldarchitect.api.FeatureAdapters::registerRegistry);
        bus.addListener(com.gwsheridan.worldarchitect.api.StructureAdapters::registerRegistry);
    }
}
