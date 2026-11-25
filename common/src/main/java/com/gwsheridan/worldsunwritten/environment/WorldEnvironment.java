package com.gwsheridan.worldsunwritten.environment;

import net.minecraft.world.entity.player.Player;
import com.gwsheridan.worldsunwritten.platform.Platform;
import com.gwsheridan.worldsunwritten.spawn.WorldAdministration;
import java.util.function.Function;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.WeatherData;

public final class WorldEnvironment {
    private static volatile Function<Level, EnvironmentProfile> clientProfiles = ignored -> EnvironmentProfile.DEFAULT;
    private WorldEnvironment() {}

    public static void clientProfiles(Function<Level, EnvironmentProfile> profiles) { clientProfiles = profiles; }

    public static EnvironmentProfile profile(Level level) {
        return level instanceof ServerLevel serverLevel ? profile(serverLevel.getServer(), level.dimension().identifier()) : clientProfiles.apply(level);
    }

    public static EnvironmentProfile profile(MinecraftServer server, Identifier dimension) {
        return WorldAdministration.get(server).environments().getOrDefault(dimension, EnvironmentProfile.DEFAULT);
    }

    public record Capabilities(boolean time, boolean weather, boolean sky, boolean clouds, boolean portals) {
        public String description() {
            return "Time: " + available(time, "dimension has no default clock or fixes its own time")
                    + "\nWeather: " + available(weather, "dimension has no surface weather")
                    + "\nMob spawning: native spawn checks and spawners supported"
                    + "\nPortal entry: " + available(portals, "custom portals need an integration calling WorldEnvironment.canEnterPortal")
                    + "\nFog: native air fog supported; water, lava and status effects retain their own fog"
                    + "\nSky colour: " + available(sky, "dimension does not use the Overworld skybox")
                    + "\nClouds: " + available(clouds, "dimension does not use the Overworld skybox")
                    + "\nVisuals use native environment attributes; custom renderers can replace them. Client cloud preference still applies.";
        }
        private static String available(boolean value, String reason) { return value ? "supported" : "unsupported (" + reason + ")"; }
    }

    public static Capabilities capabilities(Identifier id, DimensionType type) {
        boolean normalSky = type.skybox() == DimensionType.Skybox.OVERWORLD;
        return new Capabilities(type.defaultClock().isPresent() && !type.hasFixedTime(),
                type.hasSkyLight() && !type.hasCeiling() && !id.equals(Level.END.identifier()), normalSky, normalSky,
                id.equals(Level.OVERWORLD.identifier()) || id.equals(Level.NETHER.identifier()) || id.equals(Level.END.identifier())
                        || com.gwsheridan.worldsunwritten.api.DimensionAdapterRegistry.get(id).filter(adapter -> adapter.supportsPortalPolicies()).isPresent());
    }

    public static void validate(Identifier id, DimensionType type, EnvironmentProfile profile) {
        var capabilities = capabilities(id, type);
        if (!capabilities.time && profile.time().mode() != EnvironmentProfile.TimeMode.VANILLA) throw new IllegalArgumentException(id + " does not support time overrides: no changing default clock.");
        if (!capabilities.weather && profile.weather() != EnvironmentProfile.Weather.VANILLA) throw new IllegalArgumentException(id + " does not support surface weather overrides.");
        if (!capabilities.sky && profile.visual().skyColor().isPresent()) throw new IllegalArgumentException(id + " does not support the native Overworld sky colour.");
        if (!capabilities.clouds && (profile.visual().clouds() != EnvironmentProfile.Clouds.VANILLA || profile.visual().cloudColor().isPresent() || profile.visual().cloudHeight().isPresent())) throw new IllegalArgumentException(id + " does not support native Overworld clouds.");
        if (!capabilities.portals && profile.portal() != EnvironmentProfile.Portal.ALL) throw new IllegalArgumentException(id + " needs a custom portal integration before portal-entry restrictions can be configured.");
    }

    public static void validateSaved(MinecraftServer server) {
        var stems = server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM);
        WorldAdministration.get(server).environments().forEach((id, profile) -> {
            var stem = stems.getValue(id);
            if (stem == null) throw new IllegalStateException("Saved environment dimension is missing: " + id);
            validate(id, stem.type().value(), profile);
        });
    }

    public static boolean canEnterPortal(ServerLevel destination, Entity entity) {
        return switch (profile(destination).portal()) {
            case ALL -> true;
            case NONE -> false;
            case PLAYERS -> entity instanceof ServerPlayer;
            case OPERATORS -> entity instanceof ServerPlayer player && Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(player.createCommandSourceStack());
        };
    }

    public static WeatherData weatherData(ServerLevel level) {
        var weather = profile(level).weather();
        return new WeatherData(weather == EnvironmentProfile.Weather.CLEAR ? Integer.MAX_VALUE : 0,
                Integer.MAX_VALUE, Integer.MAX_VALUE, weather == EnvironmentProfile.Weather.RAIN || weather == EnvironmentProfile.Weather.THUNDER,
                weather == EnvironmentProfile.Weather.THUNDER);
    }

    public static boolean applyWeather(ServerLevel level) {
        var weather = profile(level).weather();
        if (weather == EnvironmentProfile.Weather.VANILLA) return false;
        float rain = weather == EnvironmentProfile.Weather.CLEAR ? 0 : 1;
        float thunder = weather == EnvironmentProfile.Weather.THUNDER ? 1 : 0;
        if (level.getRainLevel(1) != rain || level.getThunderLevel(1) != thunder) {
            level.setRainLevel(rain);
            level.setThunderLevel(thunder);
            var players = level.getServer().getPlayerList();
            players.broadcastAll(new ClientboundGameEventPacket(rain == 0 ? ClientboundGameEventPacket.STOP_RAINING : ClientboundGameEventPacket.START_RAINING, 0), level.dimension());
            players.broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, rain), level.dimension());
            players.broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, thunder), level.dimension());
        }
        return true;
    }

    public static boolean spawnDenied(ServerLevel level, net.minecraft.world.entity.EntityType<?> type,
            net.minecraft.world.entity.EntitySpawnReason reason) {
        return !profile(level).allowsSpawn(type.getCategory(), reason);
    }

    public static void sync(ServerPlayer player) {
        Platform.get().sendToPlayer(player, new EnvironmentPayload(player.level().dimension().identifier(), profile(player.level())));
    }

    public static void login(Player actor) { if (actor instanceof ServerPlayer player) sync(player); }
    public static void dimensionChanged(Player actor) { if (actor instanceof ServerPlayer player) sync(player); }
    public static void respawned(Player actor) { if (actor instanceof ServerPlayer player) sync(player); }

    public static void changed(ServerLevel level) {
        level.environmentAttributes().invalidateTickCache();
        applyWeather(level);
        level.players().forEach(WorldEnvironment::sync);
    }
}
