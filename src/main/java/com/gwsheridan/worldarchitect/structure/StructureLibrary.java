package com.gwsheridan.worldarchitect.structure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.LevelResource;

/** Server-thread access to a world-local library of native .nbt files. */
final class StructureLibrary {
    private static final long NBT_BUDGET = 32L * 1024 * 1024;

    private StructureLibrary() {}

    static LibraryEntry capture(ServerLevel level, Identifier id, String author, BlockPos first, BlockPos second) throws IOException {
        BlockPos min = new BlockPos(Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()), Math.min(first.getZ(), second.getZ()));
        BlockPos max = new BlockPos(Math.max(first.getX(), second.getX()), Math.max(first.getY(), second.getY()), Math.max(first.getZ(), second.getZ()));
        if (!level.isInWorldBounds(min) || !level.isInWorldBounds(max)) {
            throw new IOException("Selection falls outside this dimension's build bounds.");
        }
        BlockPos size = max.subtract(min).offset(1, 1, 1);
        NativeTemplate.checkSize(size);
        for (int x = min.getX() >> 4; x <= max.getX() >> 4; x++) {
            for (int z = min.getZ() >> 4; z <= max.getZ() >> 4; z++) {
                if (!level.hasChunkAt(new BlockPos(x << 4, min.getY(), z << 4))) {
                    throw new IOException("Load every chunk in the selection before capturing it.");
                }
            }
        }
        var cells = new ArrayList<NativeTemplate.Cell>();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            var state = level.getBlockState(pos);
            var entity = level.getBlockEntity(pos);
            if (!state.isAir()) {
                cells.add(new NativeTemplate.Cell(pos.subtract(min), state,
                        entity == null ? null : entity.saveWithoutMetadata(level.registryAccess())));
            }
        }
        NativeTemplate template = sanitize(level.getServer(), new NativeTemplate(size, cells));
        return LibraryEntry.read(template, new CompoundTag(), id.getPath(), author);
    }

    static LibraryEntry importNative(MinecraftServer server, Identifier source, Identifier id, String author) throws IOException {
        Path file = generatedPath(server, source);
        final CompoundTag nativeTag;
        if (Files.isRegularFile(file)) {
            nativeTag = read(file);
        } else {
            Identifier resourceId = StructureTemplateManager.WORLD_STRUCTURE_LISTER.idToFile(source);
            var resource = server.getResourceManager().getResource(resourceId)
                    .orElseThrow(() -> new IOException("No native structure " + source + " in generated files or enabled datapacks."));
            try (var input = resource.open()) {
                nativeTag = NbtIo.readCompressed(input, NbtAccounter.create(NBT_BUDGET));
            }
        }
        CompoundTag upgraded = upgrade(server, nativeTag);
        NativeTemplate template = sanitize(server, NativeTemplate.read(upgraded));
        return LibraryEntry.read(template, nativeTag, id.getPath(), author);
    }

    static LibraryEntry load(MinecraftServer server, Identifier id) throws IOException {
        Path file = libraryPath(server, id);
        if (!Files.isRegularFile(file)) {
            throw new IOException("No saved structure named " + id);
        }
        CompoundTag root = read(file);
        var template = sanitize(server, NativeTemplate.read(upgrade(server, root)));
        return LibraryEntry.read(template, root, id.getPath(), "");
    }

    static void saveNew(MinecraftServer server, Identifier id, LibraryEntry entry) throws IOException {
        write(libraryPath(server, id), entry.toTag(), false);
    }

    static void update(MinecraftServer server, Identifier id, LibraryEntry entry) throws IOException {
        write(libraryPath(server, id), entry.toTag(), true);
    }

    static void export(MinecraftServer server, Identifier id, LibraryEntry entry) throws IOException {
        write(generatedPath(server, id), entry.toTag(), false);
        server.getStructureTemplateManager().remove(id);
    }

    static void rename(MinecraftServer server, Identifier from, Identifier to) throws IOException {
        Path source = libraryPath(server, from);
        Path target = libraryPath(server, to);
        if (!Files.isRegularFile(source) || Files.exists(target)) {
            throw new IOException("Rename requires an existing source and an unused destination ID.");
        }
        Files.createDirectories(target.getParent());
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
    }

    static List<Identifier> list(MinecraftServer server) throws IOException {
        Path root = directory(server);
        if (Files.notExists(root)) {
            return List.of();
        }
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).map(root::relativize).map(StructureLibrary::entryId)
                    .flatMap(java.util.Optional::stream).sorted().toList();
        }
    }

    private static java.util.Optional<Identifier> entryId(Path path) {
        if (path.getNameCount() < 2 || !path.toString().endsWith(".nbt")) {
            return java.util.Optional.empty();
        }
        String file = path.subpath(1, path.getNameCount()).toString().replace('\\', '/');
        return java.util.Optional.ofNullable(Identifier.tryBuild(path.getName(0).toString(), file.substring(0, file.length() - 4)));
    }

    private static NativeTemplate sanitize(MinecraftServer server, NativeTemplate source) throws IOException {
        List<NativeTemplate.Cell> safe = new ArrayList<>();
        for (var cell : source.blocks()) {
            if (TemplateMetadata.restricted(cell.state())) {
                throw new IOException("Unsupported template block " + cell.state() + " at " + cell.pos().toShortString());
            }
            if (cell.isSolidContent()) {
                safe.add(new NativeTemplate.Cell(cell.pos(), cell.state(),
                        TemplateMetadata.sanitize(cell.state(), cell.metadata(), server.registryAccess())));
            }
        }
        return new NativeTemplate(source.size(), safe);
    }

    private static CompoundTag upgrade(MinecraftServer server, CompoundTag root) throws IOException {
        int version = NbtUtils.getDataVersion(root, 500);
        int current = SharedConstants.getCurrentVersion().dataVersion().version();
        if (version > current) {
            throw new IOException("This native structure requires a newer Minecraft data version.");
        }
        return version == current ? root : DataFixTypes.STRUCTURE.updateToCurrentVersion(server.getFixerUpper(), root, version);
    }

    private static Path libraryPath(MinecraftServer server, Identifier id) throws IOException {
        return path(directory(server), id, "");
    }

    private static Path generatedPath(MinecraftServer server, Identifier id) throws IOException {
        return path(server.getWorldPath(LevelResource.GENERATED_DIR), id, "structure");
    }

    private static Path directory(MinecraftServer server) {
        return server.getWorldPath(LevelResource.DATA).resolve("worldarchitect").resolve("structures");
    }

    private static Path path(Path root, Identifier id, String folder) throws IOException {
        if (!id.getNamespace().matches("[a-z0-9_-]{1,48}") || id.getPath().length() > 120
                || !id.getPath().matches("[a-z0-9_.-]+(/[a-z0-9_.-]+)*")
                || List.of(id.getPath().split("/")).stream().anyMatch(part -> part.equals(".") || part.equals("..") || part.endsWith("."))) {
            throw new IOException("Invalid structure file ID " + id);
        }
        return root.resolve(id.getNamespace()).resolve(folder).resolve(id.getPath() + ".nbt");
    }

    private static CompoundTag read(Path path) throws IOException {
        return NbtIo.readCompressed(path, NbtAccounter.create(NBT_BUDGET));
    }

    private static void write(Path path, CompoundTag root, boolean replace) throws IOException {
        if (!replace && Files.exists(path)) {
            throw new IOException("A template already exists at " + path.getFileName());
        }
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), ".template-", ".tmp");
        try {
            NbtIo.writeCompressed(root, temporary);
            read(temporary);
            if (replace) {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
