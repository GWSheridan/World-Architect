package com.gwsheridan.worldarchitect.api;

import com.gwsheridan.worldsunwritten.modpack.WorldBundles;
import com.gwsheridan.worldsunwritten.skyblock.IslandSpawns;
import com.gwsheridan.worldarchitect.spawn.SafeSpawns;
import com.gwsheridan.worldsunwritten.spawn.SpawnPolicies;
import com.gwsheridan.worldarchitect.spawn.SpawnPolicy;
import com.gwsheridan.worldarchitect.spawn.WorldAdministration;
import com.gwsheridan.worldsunwritten.worldgen.VoidGenerator;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class SpawnApi {
    public enum Phase { INITIAL, RESPAWN, PORTAL }
    private SpawnApi() {}
    static ServerLevel level(MinecraftServer server, Identifier dimension) {
        var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (level == null) throw new IllegalArgumentException("Unknown dimension " + dimension);
        return level;
    }
    /** Resolution may allocate a persistent personal/team island. Call only for an actual player. */
    public static Optional<SafeSpawns.Target> resolve(MinecraftServer server, SpawnPolicy policy, UUID player, String name) throws IOException { return com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyValues.legacy(com.gwsheridan.worldsunwritten.api.SpawnApi.resolve(com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyValues.current(server), com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyValues.current(policy), com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyValues.current(player), com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyValues.current(name))); }
    public static Optional<SpawnPolicy> policy(MinecraftServer server, Phase phase, Identifier dimension) { return com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyValues.legacy(com.gwsheridan.worldsunwritten.api.SpawnApi.policy(com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyValues.current(server), com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyValues.current(phase), com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyValues.current(dimension))); }
    public static void set(CommandSourceStack source, Phase phase, SpawnPolicy policy) throws IOException { com.gwsheridan.worldsunwritten.api.SpawnApi.set(com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyValues.current(source), com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyValues.current(phase), com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyValues.current(policy)); }
    public static void clear(CommandSourceStack source, Phase phase, Identifier dimension) { com.gwsheridan.worldsunwritten.api.SpawnApi.clear(com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyValues.current(source), com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyValues.current(phase), com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyValues.current(dimension)); }
    private static SpawnPolicy checked(SpawnPolicy policy) throws IOException {
        return SpawnPolicy.CODEC.parse(NbtOps.INSTANCE, SpawnPolicy.CODEC.encodeStart(NbtOps.INSTANCE, policy).getOrThrow(IOException::new)).getOrThrow(IOException::new);
    }
    private static String key(Phase phase) { return phase.name().toLowerCase(java.util.Locale.ROOT); }
    private static void allow(CommandSourceStack source, Phase phase, Identifier dimension) {
        ApiAccess.admin(source);
        WorldBundles.active(source.getServer()).ifPresent(bundle -> {
            if(bundle.manifest().pack().isPresent()){
                if(!bundle.manifest().pack().orElseThrow().allows(bundle.manifest().lock(),phase==Phase.PORTAL?"portals":key(phase)))throw new IllegalStateException("The bundled spawn policy is locked.");
                return;
            }
            if (!bundle.manifest().canEditDimension(dimension) || phase != Phase.PORTAL && !bundle.manifest().canEditDimension(Level.OVERWORLD.identifier())) throw new IllegalStateException("The bundled spawn policy is locked.");
        });
    }
}
