package com.gwsheridan.worldsunwritten.spawn;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.gwsheridan.worldsunwritten.environment.EnvironmentProfile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/** Mutable world administration is stored separately from the immutable generation snapshot. */
public final class WorldAdministration {
    private static final String FILE = "worldsunwritten-administration-v1.dat";
    private static final String REQUIRED = "worldsunwritten_administration";
    private static final Map<MinecraftServer, Settings> ACTIVE = java.util.Collections.synchronizedMap(new WeakHashMap<>());

    private WorldAdministration() {}

    public static void verify(Path dataDirectory, Dynamic<?> levelData) {
        if ((levelData.get(REQUIRED).asBoolean().result().orElse(false) || levelData.get(com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyIds.NAMESPACE + "_administration").asBoolean().result().orElse(false)) && Files.notExists(com.gwsheridan.worldsunwritten.compatibility.legacy.WorldFiles.file(dataDirectory, FILE.substring("worldsunwritten".length())))) {
            throw com.gwsheridan.worldsunwritten.compatibility.SaveProtection.incompatible(
                    "The saved Worlds Unwritten administration file is missing. Restore " + com.gwsheridan.worldsunwritten.compatibility.legacy.WorldFiles.file(dataDirectory, FILE.substring("worldsunwritten".length())));
        }
    }

    public static void load(ServerAboutToStartEvent event) {
        var server = event.getServer();
        Path file = com.gwsheridan.worldsunwritten.compatibility.legacy.WorldFiles.file(server.getWorldPath(LevelResource.DATA), FILE.substring("worldsunwritten".length()));
        if (!Files.exists(file)) {
            ACTIVE.put(server, Settings.DEFAULT);
            return;
        }
        try {
            CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.create(2 * 1024 * 1024));
            ACTIVE.put(server, Settings.CODEC.parse(NbtOps.INSTANCE, root).getOrThrow(IOException::new));
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Cannot read Worlds Unwritten administration settings. Restore " + file, e);
        }
    }

    public static void markLevelData(CompoundTag root, Path dataDirectory) {
        if (Files.exists(com.gwsheridan.worldsunwritten.compatibility.legacy.WorldFiles.file(dataDirectory, FILE.substring("worldsunwritten".length())))) {
            root.getCompound("Data").ifPresent(data -> data.putBoolean(com.gwsheridan.worldsunwritten.compatibility.legacy.WorldFiles.marker(dataDirectory, "_administration"), true));
        }
    }

    public static Settings get(MinecraftServer server) {
        return ACTIVE.getOrDefault(server, Settings.DEFAULT);
    }

    public static void save(MinecraftServer server, Settings settings) {
        com.gwsheridan.worldsunwritten.modpack.WorldBundles.active(server).ifPresent(bundle->bundle.manifest().pack().ifPresent(policy->{
            var old=get(server);var lock=bundle.manifest().lock();
            if(!policy.allows(lock,"initial")&&!old.initial().equals(settings.initial())||!policy.allows(lock,"respawn")&&!old.respawn().equals(settings.respawn())||!policy.allows(lock,"portals")&&!old.portals().equals(settings.portals())||!policy.allows(lock,"protection")&&!old.protection().equals(settings.protection()))throw new IllegalStateException("The bundled runtime policy is locked.");
        }));
        writeSettings(server,settings);
    }

    public static void initializePack(MinecraftServer server, Settings settings){
        var data=server.getWorldPath(LevelResource.DATA);
        if(Files.exists(com.gwsheridan.worldsunwritten.compatibility.legacy.WorldFiles.file(data, FILE.substring("worldsunwritten".length()))))return;
        if(com.gwsheridan.worldsunwritten.compatibility.GenerationSnapshot.exists(data))throw new IllegalStateException("Pack defaults cannot initialize an existing world's administration.");
        writeSettings(server,settings);
    }

    private static void writeSettings(MinecraftServer server,Settings settings){
        CompoundTag root = (CompoundTag) Settings.CODEC.encodeStart(NbtOps.INSTANCE, settings).getOrThrow();
        Settings.CODEC.parse(NbtOps.INSTANCE, root).getOrThrow();
        Path file = com.gwsheridan.worldsunwritten.compatibility.legacy.WorldFiles.file(server.getWorldPath(LevelResource.DATA), FILE.substring("worldsunwritten".length()));
        try {
            Files.createDirectories(file.getParent());
            Path temporary = Files.createTempFile(file.getParent(), ".worldsunwritten-administration-", ".tmp");
            try {
                NbtIo.writeCompressed(root, temporary);
                try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                    channel.force(true);
                }
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                ACTIVE.put(server, settings);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot save Worlds Unwritten administration settings", e);
        }
    }

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
