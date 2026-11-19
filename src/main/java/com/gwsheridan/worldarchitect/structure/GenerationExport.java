package com.gwsheridan.worldarchitect.structure;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.storage.LevelResource;

/** Authoring exports are datapack files. Generated pieces keep their own snapshots. */
final class GenerationExport {
    private GenerationExport() {}

    static void create(MinecraftServer server, Identifier id, Identifier biome, LibraryEntry entry) throws IOException {
        var holder = server.registryAccess().lookupOrThrow(Registries.BIOME).get(ResourceKey.create(Registries.BIOME, biome))
                .orElseThrow(() -> new IOException("Unknown biome " + biome));
        var settings = new Structure.StructureSettings(HolderSet.direct(holder), Map.of(),
                GenerationStep.Decoration.SURFACE_STRUCTURES, TerrainAdjustment.NONE);
        var structure = new ModularStructure(settings, List.of(new ModularStructure.Module("main", BlockPos.ZERO, entry)), 0, 4, true);
        write(server, id, structure, false);
    }

    static void append(MinecraftServer server, Identifier id, String name, BlockPos offset, LibraryEntry entry) throws IOException {
        Path file = path(server, id);
        JsonElement data;
        try (var reader = Files.newBufferedReader(file)) {
            data = JsonParser.parseReader(reader);
        } catch (IllegalArgumentException e) {
            throw new IOException("Cannot parse the generation draft " + id, e);
        }
        var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        var structure = Structure.DIRECT_CODEC.parse(ops, data).getOrThrow(IOException::new);
        if (!(structure instanceof ModularStructure)) {
            throw new IOException("The draft must use worldarchitect:modular.");
        }
        JsonObject root = data.getAsJsonObject();
        var modules = root.getAsJsonArray("modules");
        if (modules.size() >= ModularStructure.MAX_MODULES) {
            throw new IOException("A generated assembly supports at most " + ModularStructure.MAX_MODULES + " modules.");
        }
        modules.add(ModularStructure.Module.CODEC.encodeStart(ops, new ModularStructure.Module(name, offset, entry)).getOrThrow(IOException::new));
        write(server, id, (ModularStructure) Structure.DIRECT_CODEC.parse(ops, root).getOrThrow(IOException::new), true);
    }

    private static Path path(MinecraftServer server, Identifier id) throws IOException {
        if (!id.getNamespace().matches("[a-z0-9_-]{1,48}") || id.getPath().length() > 120
                || !id.getPath().matches("[a-z0-9_-]+(/[a-z0-9_-]+)*")) {
            throw new IOException("Generation export IDs must use lowercase letters, digits, underscores or hyphens.");
        }
        return server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("worldarchitect-generated").resolve("data")
                .resolve(id.getNamespace()).resolve("worldgen/structure").resolve(id.getPath() + ".json");
    }

    private static void write(MinecraftServer server, Identifier id, ModularStructure structure, boolean replace) throws IOException {
        var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        JsonElement data = Structure.DIRECT_CODEC.encodeStart(ops, structure)
                .mapError(error -> "Cannot encode generation draft. " + error).getOrThrow(IOException::new);
        Structure.DIRECT_CODEC.parse(ops, data)
                .mapError(error -> "Cannot decode generation draft. " + error).getOrThrow(IOException::new);
        structure.validateTemplates(server.registryAccess());
        Path target = path(server, id);
        if (!replace && Files.exists(target)) {
            throw new IOException("A generation draft already exists for " + id);
        }
        Files.createDirectories(target.getParent());
        Path pack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("worldarchitect-generated").resolve("pack.mcmeta");
        if (!Files.exists(pack)) {
            Files.writeString(pack, """
                    {"pack":{"description":"World Architect generated structures","min_format":[121,0],"max_format":[121,0]}}
                    """, StandardCharsets.UTF_8);
        }
        Path temporary = Files.createTempFile(target.getParent(), ".structure-", ".tmp");
        try {
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(data) + "\n", StandardCharsets.UTF_8);
            if (replace) {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
