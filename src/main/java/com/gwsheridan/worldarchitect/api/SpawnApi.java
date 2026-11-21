package com.gwsheridan.worldarchitect.api;

import com.gwsheridan.worldarchitect.modpack.WorldBundles;
import com.gwsheridan.worldarchitect.skyblock.IslandSpawns;
import com.gwsheridan.worldarchitect.spawn.SafeSpawns;
import com.gwsheridan.worldarchitect.spawn.SpawnPolicies;
import com.gwsheridan.worldarchitect.spawn.SpawnPolicy;
import com.gwsheridan.worldarchitect.spawn.WorldAdministration;
import com.gwsheridan.worldarchitect.worldgen.VoidGenerator;
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
    public static Optional<SafeSpawns.Target> resolve(MinecraftServer server, SpawnPolicy policy, UUID player, String name) throws IOException {
        ApiAccess.thread(server);
        policy = checked(policy);
        return SpawnPolicies.primary(server, policy, player, name);
    }
    public static Optional<SpawnPolicy> policy(MinecraftServer server, Phase phase, Identifier dimension) {
        ApiAccess.thread(server);
        var settings = WorldAdministration.get(server);
        return switch (phase) { case INITIAL -> settings.initial(); case RESPAWN -> settings.respawn(); case PORTAL -> Optional.ofNullable(settings.portals().get(dimension)); };
    }
    public static void set(CommandSourceStack source, Phase phase, SpawnPolicy policy) throws IOException {
        allow(source, phase, policy.dimension());
        policy = checked(policy);
        var server = source.getServer(); var level = level(server, policy.dimension());
        if (policy.kind() == SpawnPolicy.Kind.ISLAND || policy.kind() == SpawnPolicy.Kind.TEAM) {
            if (!(level.getChunkSource().getGenerator() instanceof VoidGenerator generator) || generator.layout().isEmpty()) throw new IOException("Island policies require a Skyblock dimension.");
            var island = generator.layout().orElseThrow();
            if(policy.kind()==SpawnPolicy.Kind.TEAM&&!SpawnPolicies.supportsTeamPolicy(generator))throw new IOException("Team spawn policies require a world created with team island allocation.");
            if (policy.kind() == SpawnPolicy.Kind.TEAM && (level != server.overworld() || island.personalSlots() == 0)) throw new IOException("Team policies require personal Overworld island slots.");
            if (island.personalSlots() > 0 && level != server.overworld()) throw new IOException("Personal islands require the Overworld.");
            if (IslandSpawns.position(level, island, 0).isEmpty()) throw new IOException("The shared island has no safe spawn.");
        } else if (SpawnPolicies.primary(server, policy, new UUID(0, 0), "").isEmpty()) throw new IOException("The requested spawn has no safe floor and headroom.");
        WorldAdministration.save(server, WorldAdministration.get(server).withPolicy(key(phase), Optional.of(policy), policy.dimension()));
    }
    public static void clear(CommandSourceStack source, Phase phase, Identifier dimension) {
        allow(source, phase, dimension);
        WorldAdministration.save(source.getServer(), WorldAdministration.get(source.getServer()).withPolicy(key(phase), Optional.empty(), dimension));
    }
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
