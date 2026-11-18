package com.gwsheridan.worldarchitect.compatibility;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.levelgen.WorldGenSettings;

/** Existing saves keep the original filename and payload; the schema describes the envelope. */
public final class GenerationSnapshot {
    static final int SCHEMA = 2;
    static final String ARCHIVE = "bundle_archive";
    static final String SETTINGS = "data";
    private static final String FILE = "worldarchitect-generation-v1.dat";
    private static final long MAX_NBT_BYTES = 32L * 1024 * 1024;

    private GenerationSnapshot() {}

    static Path snapshotPath(Path dataDirectory) {
        return dataDirectory.resolve(FILE);
    }

    static Path settingsPath(Path dataDirectory) {
        return WorldGenSettings.TYPE.id().withSuffix(".dat").resolveAgainst(dataDirectory);
    }

    public static boolean exists(Path dataDirectory) {
        return Files.exists(snapshotPath(dataDirectory));
    }

    static CompoundTag read(Path file) throws IOException {
        return NbtIo.readCompressed(file, NbtAccounter.create(MAX_NBT_BYTES));
    }

    public static Optional<CompoundTag> load(Path dataDirectory) {
        Path file = snapshotPath(dataDirectory);
        if (Files.notExists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(read(file));
        } catch (IOException | RuntimeException e) {
            throw SaveProtection.incompatible("Cannot read the original generation snapshot: " + e.getMessage());
        }
    }

    public static boolean hasDependencyPins(CompoundTag snapshot) {
        var version = snapshot.get("snapshot_schema");
        int schema = version == null ? 1 : version instanceof IntTag number ? number.intValue() : -1;
        if (schema != 1 && schema != SCHEMA) {
            throw SaveProtection.incompatible("Unsupported generation snapshot schema " + schema);
        }
        return schema == SCHEMA;
    }

    public static Optional<CompoundTag> archivePin(CompoundTag snapshot) {
        var pin = snapshot.getCompound(ARCHIVE);
        if (pin.isEmpty() && snapshot.contains(ARCHIVE)) {
            throw SaveProtection.incompatible("Invalid saved preset archive");
        }
        return pin;
    }

    static void initialize(Path dataDirectory, CompoundTag root) {
        Path file = snapshotPath(dataDirectory);
        if (!Files.notExists(file)) {
            throw SaveProtection.incompatible("A generation snapshot already exists");
        }
        try {
            Files.createDirectories(dataDirectory);
            Path temporary = Files.createTempFile(dataDirectory, ".worldarchitect-generation-", ".tmp");
            try {
                NbtIo.writeCompressed(root, temporary);
                read(temporary);
                try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                    channel.force(true);
                }
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("World Architect could not store the original generation settings", e);
        }
    }
}
