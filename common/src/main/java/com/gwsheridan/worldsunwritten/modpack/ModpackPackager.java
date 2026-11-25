package com.gwsheridan.worldsunwritten.modpack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.gwsheridan.worldsunwritten.compatibility.SaveProtection;
import com.gwsheridan.worldsunwritten.config.IslandTemplate;
import com.gwsheridan.worldsunwritten.structure.NativeTemplate;
import com.gwsheridan.worldsunwritten.worldgen.ManagedGenerator;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;

final class ModpackPackager {
    private ModpackPackager() {}

    static Path export(MinecraftServer server, String name) throws IOException {
        if (!BundleFiles.filename(name)) {
            throw new IOException("Use a single draft directory name containing letters, digits, hyphens or underscores.");
        }
        Path config = ModpackRuntime.directory();
        Path draft = config.resolve("drafts").resolve(name);
        Path output = config.resolve("exports").resolve(name + ".zip");
        if (Files.exists(output)) {
            throw new IOException("An export named " + output.getFileName() + " already exists.");
        }
        var manifest = BundleManifest.CODEC.parse(JsonOps.INSTANCE, BundleFiles.readJson(draft.resolve("manifest.json")))
                .getOrThrow(IOException::new);
        var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        Path authored = draft.resolve("preset.json");
        JsonObject preset = Files.exists(authored) ? BundleFiles.readJson(authored) : currentPreset(server, ops);
        var structures = collectStructures(draft);
        Path islandFile = draft.resolve("islands.json");
        if (Files.exists(islandFile)) {
            var bindings = Codec.unboundedMap(Identifier.CODEC, Starter.CODEC)
                    .parse(JsonOps.INSTANCE, BundleFiles.readJson(islandFile)).getOrThrow(IOException::new);
            bindIslands(preset, structures, bindings);
        }
        var checked = PresetStudio.validate(new PresetStudio.Draft(manifest, preset, structures), server.registryAccess());
        structures.putAll(PresetStudio.capture(manifest, checked.preset().createWorldDimensions(), checked.registries()).templates());
        checked = PresetStudio.validate(new PresetStudio.Draft(manifest, checked.draft().preset(), structures), server.registryAccess());
        byte[] encodedPreset = BundleFiles.jsonBytes(checked.draft().preset());
        if (encodedPreset.length > BundleFiles.MAX_JSON_BYTES) {
            throw new IOException("The assembled preset exceeds the JSON size limit.");
        }
        Files.createDirectories(output.getParent());
        Path temporary = Files.createTempFile(output.getParent(), ".bundle-", ".tmp");
        try {
            try (var zip = new ZipOutputStream(Files.newOutputStream(temporary))) {
                add(zip, "pack.mcmeta", BundleFiles.jsonBytes(packMetadata(manifest.title())));
                add(zip, BundleFiles.MANIFEST, BundleFiles.jsonBytes(BundleManifest.CODEC.encodeStart(JsonOps.INSTANCE, manifest).getOrThrow()));
                add(zip, BundleFiles.presetPath(manifest.preset()), encodedPreset);
                for (var structure : structures.entrySet()) {
                    add(zip, structure.getKey(), structure.getValue());
                }
            }
            PresetBundle.read(temporary);
            Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE);
            return output;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static JsonObject currentPreset(MinecraftServer server, RegistryOps<JsonElement> ops) throws IOException {
        var live = new WorldDimensions(server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM));
        return WorldPreset.DIRECT_CODEC.encodeStart(ops, new WorldPreset(live.dimensions())).getOrThrow(IOException::new).getAsJsonObject();
    }

    static SortedMap<String, byte[]> collectStructures(Path draft) throws IOException {
        var structures = new TreeMap<String, byte[]>();
        Path root = draft.resolve("data");
        if (!Files.exists(root)) {
            return structures;
        }
        long total = 0;
        try (var paths = Files.walk(root)) {
            var files = paths.filter(Files::isRegularFile).iterator();
            while (files.hasNext()) {
                var file = files.next();
                var name = draft.relativize(file).toString().replace('\\', '/');
                if (!BundleFiles.templatePath(name) && !BundleFiles.modularPath(name)) {
                    throw new IOException("Expected a native structure under data/<namespace>/structure/: " + name);
                }
                if (structures.size() == BundleFiles.MAX_TEMPLATES) {
                    throw new IOException("A bundle can contain at most " + BundleFiles.MAX_TEMPLATES + " structures.");
                }
                byte[] bytes;
                try (var input = Files.newInputStream(file)) {
                    bytes = BundleFiles.readBytes(input, BundleFiles.MAX_TEMPLATE_BYTES, name);
                }
                if ((total += bytes.length) > BundleFiles.MAX_ARCHIVE_BYTES) {
                    throw new IOException("Bundled native structure files exceed 32 MiB.");
                }
                if (BundleFiles.templatePath(name)) NativeTemplate.read(NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.create(16L * 1024 * 1024)));
                structures.put(name, bytes);
            }
        }
        return structures;
    }

    private static void bindIslands(JsonObject preset, Map<String, byte[]> structures, Map<Identifier, Starter> bindings) throws IOException {
        if (!(preset.get("dimensions") instanceof JsonObject dimensions)) {
            throw new IOException("The preset needs a dimensions object.");
        }
        for (var binding : bindings.entrySet()) {
            if (!(dimensions.get(binding.getKey().toString()) instanceof JsonObject stem)
                    || !(stem.get("generator") instanceof JsonObject generator)
                    || !(generator.get("island") instanceof JsonObject island)) {
                throw new IOException("Add a Skyblock generator and island settings for " + binding.getKey());
            }
            var source = binding.getValue();
            var bytes = structures.get(BundleFiles.templatePath(source.structure));
            if (bytes == null) {
                throw new IOException("Missing bundled starter island " + source.structure);
            }
            var nativeTag = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.create(8L * 1024 * 1024));
            var template = IslandTemplate.importStructure(nativeTag, source.spawn);
            island.add("template", IslandTemplate.CODEC.encodeStart(JsonOps.INSTANCE, template).getOrThrow(IOException::new));
        }
    }

    static JsonObject packMetadata(String title) {
        var metadata = new JsonObject();
        var pack = new JsonObject();
        var current = SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA);
        var version = new JsonArray();
        version.add(current.major());
        version.add(current.minor());
        pack.addProperty("description", title);
        pack.add("min_format", version);
        pack.add("max_format", version.deepCopy());
        metadata.add("pack", pack);
        return metadata;
    }

    static void add(ZipOutputStream zip, String path, byte[] bytes) throws IOException {
        var entry = new ZipEntry(path);
        entry.setTime(0L);
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    private record Starter(Identifier structure, BlockPos spawn) {
        static final Codec<Starter> CODEC = RecordCodecBuilder.create(builder -> builder.group(
                Identifier.CODEC.fieldOf("structure").forGetter(Starter::structure),
                BlockPos.CODEC.fieldOf("spawn").forGetter(Starter::spawn)
        ).apply(builder, Starter::new));
    }
}
