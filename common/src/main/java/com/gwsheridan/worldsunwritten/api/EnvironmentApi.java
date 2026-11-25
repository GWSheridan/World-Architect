package com.gwsheridan.worldsunwritten.api;

import com.gwsheridan.worldsunwritten.environment.EnvironmentProfile;
import com.gwsheridan.worldsunwritten.environment.WorldEnvironment;
import com.gwsheridan.worldsunwritten.modpack.WorldBundles;
import com.gwsheridan.worldsunwritten.spawn.WorldAdministration;
import com.mojang.serialization.JsonOps;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

public final class EnvironmentApi {
    private EnvironmentApi() {}
    public static EnvironmentProfile profile(ServerLevel level) { ApiAccess.thread(level.getServer()); return WorldEnvironment.profile(level); }
    public static WorldEnvironment.Capabilities capabilities(ServerLevel level) { ApiAccess.thread(level.getServer()); return WorldEnvironment.capabilities(level.dimension().identifier(), level.dimensionType()); }
    /** Custom portal integrations call this before teleporting into the destination. */
    public static boolean canEnter(ServerLevel destination, Entity entity) { ApiAccess.thread(destination.getServer()); return WorldEnvironment.canEnterPortal(destination, entity); }
    public static void set(CommandSourceStack source, Identifier dimension, EnvironmentProfile profile) {
        ApiAccess.admin(source);
        var level = SpawnApi.level(source.getServer(), dimension);
        WorldBundles.active(source.getServer()).ifPresent(bundle -> {
            if (!bundle.manifest().canEditDimension(dimension)) throw new IllegalStateException("This dimension is locked by the active preset bundle.");
        });
        profile = EnvironmentProfile.CODEC.parse(JsonOps.INSTANCE, EnvironmentProfile.CODEC.encodeStart(JsonOps.INSTANCE, profile).getOrThrow(IllegalArgumentException::new)).getOrThrow(IllegalArgumentException::new);
        WorldEnvironment.validate(dimension, level.dimensionType(), profile);
        WorldAdministration.save(source.getServer(), WorldAdministration.get(source.getServer()).withEnvironment(dimension, profile));
        WorldEnvironment.changed(level);
    }
}
