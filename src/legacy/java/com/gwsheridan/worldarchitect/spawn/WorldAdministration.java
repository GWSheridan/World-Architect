package com.gwsheridan.worldarchitect.spawn;
import java.util.*;import com.gwsheridan.worldarchitect.environment.EnvironmentProfile;import com.mojang.serialization.Codec;import com.mojang.serialization.codecs.RecordCodecBuilder;import net.minecraft.resources.Identifier;
public final class WorldAdministration {
 private WorldAdministration() {}
public record Protection(boolean breaking, boolean placement, boolean containers, boolean shared, List<Identifier> containerBlocks) {
        public static final Protection OFF = new Protection(false, false, false, false,
                List.of(Identifier.withDefaultNamespace("chest"), Identifier.withDefaultNamespace("trapped_chest"), Identifier.withDefaultNamespace("barrel")));
        private static final Codec<Protection> CODEC = RecordCodecBuilder.create(builder -> builder.group(
                Codec.BOOL.optionalFieldOf("breaking", false).forGetter(Protection::breaking),
                Codec.BOOL.optionalFieldOf("placement", false).forGetter(Protection::placement),
                Codec.BOOL.optionalFieldOf("containers", false).forGetter(Protection::containers),
                Codec.BOOL.optionalFieldOf("shared", false).forGetter(Protection::shared),
                Identifier.CODEC.listOf(0, 64).optionalFieldOf("container_blocks", OFF.containerBlocks).forGetter(Protection::containerBlocks)
        ).apply(builder, Protection::new));

        public Protection {
            containerBlocks = List.copyOf(containerBlocks);
        }
    }
public record Settings(Optional<SpawnPolicy> initial, Optional<SpawnPolicy> respawn, Map<Identifier, SpawnPolicy> portals, Protection protection,
            Map<Identifier, EnvironmentProfile> environments) {
        public static final Settings DEFAULT = new Settings(Optional.empty(), Optional.empty(), Map.of(), Protection.OFF, Map.of());
        public static final Codec<Settings> CODEC = RecordCodecBuilder.create(builder -> builder.group(
                Codec.intRange(1, 1).fieldOf("version").forGetter(settings -> 1),
                SpawnPolicy.CODEC.optionalFieldOf("initial").forGetter(Settings::initial),
                SpawnPolicy.CODEC.optionalFieldOf("respawn").forGetter(Settings::respawn),
                Codec.unboundedMap(Identifier.CODEC, SpawnPolicy.CODEC).optionalFieldOf("portals", Map.of()).forGetter(Settings::portals),
                Protection.CODEC.optionalFieldOf("protection", Protection.OFF).forGetter(Settings::protection),
                Codec.unboundedMap(Identifier.CODEC, EnvironmentProfile.CODEC).optionalFieldOf("environments", Map.of()).forGetter(Settings::environments)
        ).apply(builder, (version, initial, respawn, portals, protection, environments) -> new Settings(initial, respawn, portals, protection, environments)));

        public Settings {
            portals = Map.copyOf(portals);
            environments = Map.copyOf(environments);
        }

        public Settings withPolicy(String event, Optional<SpawnPolicy> policy, Identifier dimension) {
            var next = new HashMap<>(portals);
            if (event.equals("portal")) {
                if (policy.isPresent()) {
                    next.put(dimension, policy.orElseThrow());
                } else {
                    next.remove(dimension);
                }
            }
            return new Settings(event.equals("initial") ? policy : initial, event.equals("respawn") ? policy : respawn, next, protection, environments);
        }

        public Settings withProtection(Protection protection) {
            return new Settings(initial, respawn, portals, protection, environments);
        }

        public Settings withEnvironment(Identifier dimension, EnvironmentProfile profile) {
            var next = new HashMap<>(environments);
            if (profile.equals(EnvironmentProfile.DEFAULT)) next.remove(dimension);
            else next.put(dimension, profile);
            return new Settings(initial, respawn, portals, protection, next);
        }
    }
}
