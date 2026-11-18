package com.gwsheridan.worldarchitect.modpack;

import com.gwsheridan.worldarchitect.compatibility.GenerationSnapshot;
import com.gwsheridan.worldarchitect.compatibility.SaveProtection;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;

/** Bind the archive to the world repository, including reloads and servers in an integrated client. */
public final class WorldBundles {
    private static final ThreadLocal<Optional<PresetBundle>> CREATING_REPOSITORY = new ThreadLocal<>();
    private static final Map<PackRepository, Optional<PresetBundle>> REPOSITORIES = Collections.synchronizedMap(new WeakHashMap<>());

    private WorldBundles() {}

    public static boolean isBundlePack(String id) {
        return id.startsWith(PresetBundle.PACK_PREFIX);
    }

    public static PackRepository createRepository(LevelStorageSource.LevelStorageAccess storage, Supplier<PackRepository> create) {
        var bundle = select(storage, GenerationSnapshot.load(storage.getLevelPath(LevelResource.DATA)));
        var previous = CREATING_REPOSITORY.get();
        CREATING_REPOSITORY.set(bundle);
        try {
            var repository = create.get();
            REPOSITORIES.put(repository, bundle);
            return repository;
        } finally {
            if (previous == null) {
                CREATING_REPOSITORY.remove();
            } else {
                CREATING_REPOSITORY.set(previous);
            }
        }
    }

    static Optional<PresetBundle> forRepositorySource() {
        var scoped = CREATING_REPOSITORY.get();
        return scoped == null ? ModpackRuntime.installed() : scoped;
    }

    public static Optional<PresetBundle> active(MinecraftServer server) {
        return REPOSITORIES.getOrDefault(server.getPackRepository(), Optional.empty());
    }

    public static Optional<PresetBundle> select(LevelStorageSource.LevelStorageAccess storage, Optional<CompoundTag> snapshot) {
        if (snapshot.isEmpty()) {
            return storage.hasWorldData() ? Optional.empty() : ModpackRuntime.installed();
        }
        var root = snapshot.orElseThrow();
        boolean versioned = GenerationSnapshot.hasDependencyPins(root);
        var lock = lockPin(root);
        var saved = GenerationSnapshot.archivePin(root);
        if (versioned && lock.isPresent() && !lock.equals(saved)) {
            throw SaveProtection.incompatible("The saved modpack lock and archived bundle disagree");
        }
        var pin = versioned ? saved : lock;
        return pin.map(value -> archivedBundle(storage.getLevelPath(LevelResource.DATA), value, versioned));
    }

    public static Optional<CompoundTag> lockPin(CompoundTag snapshot) {
        var pin = snapshot.getCompound(ModpackRuntime.SNAPSHOT_KEY);
        if (pin.isEmpty() && snapshot.contains(ModpackRuntime.SNAPSHOT_KEY)) {
            throw SaveProtection.incompatible("Invalid saved modpack lock");
        }
        return pin;
    }

    private static PresetBundle archivedBundle(Path data, CompoundTag pin, boolean versioned) {
        Path archive = path(data, pin);
        if (!Files.notExists(archive)) {
            try {
                PresetBundle bundle = PresetBundle.read(archive);
                if (!bundle.pin().equals(pin)) {
                    throw SaveProtection.incompatible("The world-local preset archive differs from its saved fingerprint");
                }
                return bundle;
            } catch (IOException e) {
                throw SaveProtection.incompatible("Cannot read the world-local preset archive " + archive.getFileName() + ": " + e.getMessage());
            }
        }
        if (!versioned) {
            var currentlyInstalled = ModpackRuntime.installed().filter(bundle -> bundle.pin().equals(pin));
            if (currentlyInstalled.isPresent()) {
                return currentlyInstalled.orElseThrow();
            }
        }
        throw SaveProtection.incompatible("Restore the exact original bundle for " + pin.getStringOr("preset", "this save")
                + " release " + pin.getStringOr("release", "?") + " at " + archive);
    }

    public static void preserve(Path data, PresetBundle bundle) {
        var pin = bundle.pin();
        var destination = path(data, pin);
        String expected = pin.getString("sha256").orElseThrow();
        try {
            if (!Files.notExists(destination)) {
                if (!BundleFiles.fingerprint(destination).equals(expected)) {
                    throw SaveProtection.incompatible("The world-local preset archive differs from its saved fingerprint");
                }
                return;
            }
            bundle.verifyArchive();
            Files.createDirectories(destination.getParent());
            Path temporary = Files.createTempFile(destination.getParent(), ".bundle-", ".tmp");
            try {
                Files.copy(bundle.archive(), temporary, StandardCopyOption.REPLACE_EXISTING);
                if (!BundleFiles.fingerprint(temporary).equals(expected)) {
                    throw SaveProtection.incompatible("The preset bundle changed while it was being archived");
                }
                try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                    channel.force(true);
                }
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            throw SaveProtection.incompatible("Cannot preserve the world-local preset archive: " + e.getMessage());
        }
    }

    private static Path path(Path data, CompoundTag pin) {
        String digest = pin.getStringOr("sha256", "");
        if (pin.getIntOr("format", 0) != 1 || !digest.matches("[a-f0-9]{64}")) {
            throw SaveProtection.incompatible("Invalid saved preset bundle fingerprint");
        }
        return data.resolve("worldarchitect").resolve("generation").resolve(digest + ".zip");
    }
}
