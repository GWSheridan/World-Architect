package com.gwsheridan.worldsunwritten.client;

import com.gwsheridan.worldsunwritten.environment.EnvironmentPayload;
import com.gwsheridan.worldsunwritten.environment.EnvironmentProfile;
import com.gwsheridan.worldsunwritten.environment.WorldEnvironment;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public final class ClientEnvironment {
    private static final Map<Identifier, EnvironmentProfile> PROFILES = new HashMap<>();
    private ClientEnvironment() {}

    public static void initialize() {
        WorldEnvironment.clientProfiles(level -> PROFILES.getOrDefault(level.dimension().identifier(), EnvironmentProfile.DEFAULT));
    }

    public static void receive(EnvironmentPayload payload) {
            PROFILES.put(payload.dimension(), payload.profile());
            var level = Minecraft.getInstance().level;
            if (level != null) level.environmentAttributes().invalidateTickCache();
    }

    public static void logout() { PROFILES.clear(); }
}
