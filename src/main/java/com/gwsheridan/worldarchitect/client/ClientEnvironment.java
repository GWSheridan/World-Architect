package com.gwsheridan.worldarchitect.client;

import com.gwsheridan.worldarchitect.environment.EnvironmentPayload;
import com.gwsheridan.worldarchitect.environment.EnvironmentProfile;
import com.gwsheridan.worldarchitect.environment.WorldEnvironment;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

public final class ClientEnvironment {
    private static final Map<Identifier, EnvironmentProfile> PROFILES = new HashMap<>();
    private ClientEnvironment() {}

    public static void register(RegisterClientPayloadHandlersEvent event) {
        WorldEnvironment.clientProfiles(level -> PROFILES.getOrDefault(level.dimension().identifier(), EnvironmentProfile.DEFAULT));
        event.register(EnvironmentPayload.TYPE, (payload, context) -> {
            PROFILES.put(payload.dimension(), payload.profile());
            var level = Minecraft.getInstance().level;
            if (level != null) level.environmentAttributes().invalidateTickCache();
        });
    }

    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { PROFILES.clear(); }
}
