package com.gwsheridan.worldarchitect.structure;

import com.google.gson.*;
import com.gwsheridan.worldarchitect.api.DimensionAdapter.TerrainMode;
import com.gwsheridan.worldarchitect.config.*;
import com.gwsheridan.worldarchitect.dimension.DimensionCatalog;
import com.gwsheridan.worldarchitect.modpack.*;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.storage.LevelResource;

/** Exports never install a datapack or change the authoring world's live registry. */
final class AssemblyExports {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private AssemblyExports() {}
    private static byte[] bytes(JsonElement value) { return (JSON.toJson(value) + "\n").getBytes(StandardCharsets.UTF_8); }
    static JsonObject definition(MinecraftServer server, AssemblyDraft draft) throws IOException {
        return Structure.DIRECT_CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, server.registryAccess()), draft.structure(server)).getOrThrow(IOException::new).getAsJsonObject();
    }
    private static void target(MinecraftServer server, Identifier id) throws IOException {
        AssemblyDraft.safeId(id);
        if (server.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(ResourceKey.create(Registries.STRUCTURE, id)).isPresent())
            throw new IOException("Choose an unused structure ID; registered definitions are preserved.");
    }
    static Path datapack(MinecraftServer server, AssemblyDraft draft, Identifier id) throws IOException {
        target(server, id);
        var data = definition(server, draft);
        Path directory = server.getWorldPath(LevelResource.GENERATED_DIR).resolve("worldarchitect/assembly-exports");
        Path output = directory.resolve(id.getNamespace()).resolve(id.getPath() + "-datapack.zip");
        for (var p = output; p != null && p.startsWith(directory); p = p.getParent()) if (Files.isSymbolicLink(p)) throw new IOException("Export paths cannot contain symbolic links.");
        if (Files.exists(output)) throw new IOException("That export already exists. Choose another ID.");
        Files.createDirectories(output.getParent()); var temp = Files.createTempFile(output.getParent(), ".assembly-", ".tmp");
        try {
            var files = new TreeMap<String, byte[]>();
            files.put("pack.mcmeta", "{\"pack\":{\"description\":\"World Architect assembly\",\"min_format\":[121,0],\"max_format\":[121,0]}}\n".getBytes(StandardCharsets.UTF_8));
            files.put("data/" + id.getNamespace() + "/worldgen/structure/" + id.getPath() + ".json", bytes(data));
            var set=new JsonObject();var choices=new JsonArray();var choice=new JsonObject();choice.addProperty("structure",id.toString());choice.addProperty("weight",1);choices.add(choice);set.add("structures",choices);
            var placement=new JsonObject();placement.addProperty("type","minecraft:random_spread");placement.addProperty("spacing",16);placement.addProperty("separation",Math.max(3,(draft.structure(server).horizontalSpan()+15)/16));placement.addProperty("salt",Math.floorMod(id.toString().hashCode(),Integer.MAX_VALUE));set.add("placement",placement);
            files.put("data/"+id.getNamespace()+"/worldgen/structure_set/"+id.getPath()+".json",bytes(set));
            try (var zip = new ZipOutputStream(Files.newOutputStream(temp))) {
                for (var file : files.entrySet()) { var entry = new ZipEntry(file.getKey()); entry.setTime(0); zip.putNextEntry(entry); zip.write(file.getValue()); zip.closeEntry(); }
            }
            Files.move(temp, output); return output;
        } finally { Files.deleteIfExists(temp); }
    }
    static Path bundle(MinecraftServer server, AssemblyDraft draft, Identifier id, Identifier dimension, String release, long seed, String author) throws IOException {
        target(server, id);
        var dimensions = new net.minecraft.world.level.levelgen.WorldDimensions(server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM));
        var key = ResourceKey.create(Registries.LEVEL_STEM, dimension);
        var stem = dimensions.dimensions().get(key);
        if (stem == null) throw new IOException("Unknown dimension " + dimension);
        var support = DimensionCatalog.inspect(server.registryAccess(), key, stem);
        if (!support.modes().contains(TerrainMode.SINGLE_BLOCK)) throw new IOException("This dimension needs a compatible Single Block adapter.");
        var type = stem.type().value();
        var profile = new DimensionProfile(type.minY(), type.height(), support.adapter());
        if (key.equals(LevelStem.END) && type.hasEnderDragonFight() && type.minY() == 0 && type.height() == 256 && support.adapter().isEmpty())
            profile = profile.withEndProgression(Optional.of(EndProgression.vanillaArena(server.registryAccess())));
        var biome = server.registryAccess().lookupOrThrow(Registries.BIOME).get(ResourceKey.create(Registries.BIOME, draft.biome())).orElseThrow(() -> new IOException("Missing biome " + draft.biome()));
        int elevation = type.minY() + Math.max(4, Math.min(64 - type.minY(), type.height() / 3));
        var generator = new com.gwsheridan.worldarchitect.worldgen.SingleBlockGenerator(biome, new SingleBlockProfile(Blocks.STONE.defaultBlockState(), new TerrainShape(elevation, 0)), Optional.of(profile));
        var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        var encodedGenerator = ChunkGenerator.CODEC.encodeStart(ops, generator).getOrThrow(IOException::new).getAsJsonObject();
        var selection = new JsonObject(); selection.addProperty("structure", id.toString());
        int separation = Math.max(3, (draft.structure(server).horizontalSpan() + 15) / 16);
        selection.addProperty("separation", separation); selection.addProperty("spacing", Math.max(16, separation + 1));
        var structures = new JsonArray(); structures.add(selection); var content = new JsonObject(); content.add("structures", structures);
        encodedGenerator.getAsJsonObject("dimension_profile").add("content", content);
        var mods = new TreeSet<String>();
        for (var part : draft.parts()) for (var cell : part.original().template().blocks()) mods.add(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(cell.state().getBlock()).getNamespace());
        mods.add("worldarchitect"); mods.add(draft.biome().getNamespace()); mods.add(dimension.getNamespace());
        mods.retainAll(com.gwsheridan.worldarchitect.rules.RuleDependencies.loaded()); mods.remove("minecraft");
        var manifest = new BundleManifest(id, "Assembly " + id.getPath().substring(0, Math.min(70, id.getPath().length())),
                "Assembly Studio export for " + dimension, author, release, BundleManifest.Lock.UNLOCKED, new WorldOptions(seed, true, false), List.of(), Map.of(), List.of(), List.copyOf(mods), true);
        var captured = PresetStudio.capture(manifest, dimensions, server.registryAccess());
        var preset = captured.preset(); preset.getAsJsonObject("dimensions").getAsJsonObject(dimension.toString()).add("generator", encodedGenerator);
        var resources = new TreeMap<>(captured.templates());
        resources.put("data/" + id.getNamespace() + "/worldgen/structure/" + id.getPath() + ".json", bytes(definition(server, draft)));
        var exported = new PresetStudio.Draft(captured.manifest(), preset, resources);
        PresetStudio.validate(exported, server.registryAccess());
        String name = "assembly_" + UUID.nameUUIDFromBytes(id.toString().getBytes(StandardCharsets.UTF_8)).toString().replace("-", "").substring(0, 16);
        PresetStudio.save(ModpackRuntime.directory(), name, exported, server.registryAccess());
        return PresetStudio.export(ModpackRuntime.directory(), name, release, server.registryAccess());
    }
}
