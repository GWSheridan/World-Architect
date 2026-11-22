package com.gwsheridan.worldarchitect.api;

import com.gwsheridan.worldarchitect.environment.EnvironmentProfile;
import com.gwsheridan.worldarchitect.environment.WorldEnvironment;
import com.gwsheridan.worldsunwritten.modpack.WorldBundles;
import com.gwsheridan.worldarchitect.spawn.WorldAdministration;
import com.mojang.serialization.JsonOps;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

public final class EnvironmentApi {
    private EnvironmentApi() {}
    public static EnvironmentProfile profile(ServerLevel level) { return com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyValues.legacy(com.gwsheridan.worldsunwritten.api.EnvironmentApi.profile(com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyValues.current(level))); }
    public static WorldEnvironment.Capabilities capabilities(ServerLevel level) { return com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyValues.legacy(com.gwsheridan.worldsunwritten.api.EnvironmentApi.capabilities(com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyValues.current(level))); }
    /** Custom portal integrations call this before teleporting into the destination. */
    public static boolean canEnter(ServerLevel destination, Entity entity) { return com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyValues.legacy(com.gwsheridan.worldsunwritten.api.EnvironmentApi.canEnter(com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyValues.current(destination), com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyValues.current(entity))); }
    public static void set(CommandSourceStack source, Identifier dimension, EnvironmentProfile profile) { com.gwsheridan.worldsunwritten.api.EnvironmentApi.set(com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyValues.current(source), com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyValues.current(dimension), com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyValues.current(profile)); }
}
