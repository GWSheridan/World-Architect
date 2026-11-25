package com.gwsheridan.worldsunwritten.structure;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.storage.LevelResource;

/** Mutable authoring documents live separately from immutable generated-world definitions. */
record AssemblyDraft(Identifier id, int revision, Identifier biome, int yOffset, int maxSlope, boolean randomRotation, List<Part> parts, Optional<ProceduralPool> pool,Optional<PlacementAnchor> anchor,Optional<StructureFoundation> foundation,Optional<StructureThemes> themes) {
    AssemblyDraft(Identifier id,int revision,Identifier biome,int y,int slope,boolean random,List<Part> parts,Optional<ProceduralPool> pool,Optional<PlacementAnchor> anchor){this(id,revision,biome,y,slope,random,parts,pool,anchor,Optional.empty(),Optional.empty());}
    static final int MAX_JSON = 8 * 1024 * 1024;
    static final Codec<AssemblyDraft> CODEC = RecordCodecBuilder.<AssemblyDraft>create(b -> b.group(
            Codec.intRange(1, 1).fieldOf("format").forGetter(d -> 1),
            Identifier.CODEC.fieldOf("id").forGetter(AssemblyDraft::id),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("revision").forGetter(AssemblyDraft::revision),
            Identifier.CODEC.fieldOf("biome").forGetter(AssemblyDraft::biome),
            Codec.intRange(-64, 64).fieldOf("y_offset").forGetter(AssemblyDraft::yOffset),
            Codec.intRange(0, 32).fieldOf("max_slope").forGetter(AssemblyDraft::maxSlope),
            Codec.BOOL.fieldOf("random_rotation").forGetter(AssemblyDraft::randomRotation),
            Part.CODEC.listOf(0, ModularStructure.MAX_MODULES).fieldOf("parts").forGetter(AssemblyDraft::parts),
            ProceduralPool.CODEC.optionalFieldOf("assembly_pool").forGetter(AssemblyDraft::pool),
            PlacementAnchor.CODEC.optionalFieldOf("placement").forGetter(AssemblyDraft::anchor),
            StructureFoundation.CODEC.optionalFieldOf("foundation").forGetter(AssemblyDraft::foundation),
            StructureThemes.CODEC.optionalFieldOf("themes").forGetter(AssemblyDraft::themes)
    ).apply(b, (format, id, revision, biome, y, slope, random, parts, pool,anchor,foundation,themes) -> new AssemblyDraft(id, revision, biome, y, slope, random, parts, pool,anchor,foundation,themes)));

    AssemblyDraft { parts = List.copyOf(parts); }
    AssemblyDraft(Identifier id,int revision,Identifier biome,int y,int slope,boolean random,List<Part> parts){this(id,revision,biome,y,slope,random,parts,Optional.empty());}
    AssemblyDraft(Identifier id,int revision,Identifier biome,int y,int slope,boolean random,List<Part> parts,Optional<ProceduralPool> pool){this(id,revision,biome,y,slope,random,parts,pool,Optional.empty());}
    AssemblyDraft withPool(Optional<ProceduralPool> next){return new AssemblyDraft(id,revision,biome,yOffset,maxSlope,randomRotation,parts,next,anchor,foundation,themes);}
    AssemblyDraft withAnchor(Optional<PlacementAnchor> next){return new AssemblyDraft(id,revision,biome,yOffset,maxSlope,randomRotation,parts,pool,next,foundation,themes);}
    AssemblyDraft withFoundation(Optional<StructureFoundation> next){return new AssemblyDraft(id,revision,biome,yOffset,maxSlope,randomRotation,parts,pool,anchor,next,themes);}
    AssemblyDraft withThemes(Optional<StructureThemes> next){return new AssemblyDraft(id,revision,biome,yOffset,maxSlope,randomRotation,parts,pool,anchor,foundation,next);}
    AssemblyDraft atRevision(int value){return new AssemblyDraft(id,value,biome,yOffset,maxSlope,randomRotation,parts,pool,anchor,foundation,themes);}
    record Part(String name, Identifier source, BlockPos offset, Rotation rotation, Mirror mirror, LibraryEntry original) {
        static final Codec<Part> CODEC = RecordCodecBuilder.create(b -> b.group(
                Codec.STRING.fieldOf("name").forGetter(Part::name), Identifier.CODEC.fieldOf("source").forGetter(Part::source),
                BlockPos.CODEC.fieldOf("offset").forGetter(Part::offset),
                Codec.STRING.xmap(Rotation::valueOf, Rotation::name).fieldOf("rotation").forGetter(Part::rotation),
                Codec.STRING.xmap(Mirror::valueOf, Mirror::name).fieldOf("mirror").forGetter(Part::mirror),
                LibraryEntry.CODEC.fieldOf("snapshot").forGetter(Part::original)
        ).apply(b, Part::new));
        LibraryEntry transformed() throws IOException { return original.transformed(new TemplatePlacement(BlockPos.ZERO, rotation, mirror)); }
    }
    AssemblyDraft withParts(List<Part> next) { return new AssemblyDraft(id, revision, biome, yOffset, maxSlope, randomRotation, next, pool,anchor,foundation,themes); }
    AssemblyDraft withSettings(Identifier biome, int y, int slope, boolean random) { return new AssemblyDraft(id, revision, biome, y, slope, random, parts, pool,anchor,foundation,themes); }
    AssemblyDraft check() throws IOException {
        safeId(id);
        if(anchor.isPresent())anchor.orElseThrow().validate().getOrThrow(IOException::new);
        if(foundation.isPresent())foundation.orElseThrow().validate().getOrThrow(IOException::new);
        if(themes.isPresent())themes.orElseThrow().validate().getOrThrow(IOException::new);
        if(pool.isPresent())pool.orElseThrow().validate().getOrThrow(IOException::new);
        if (parts.size() > ModularStructure.MAX_MODULES || yOffset < -64 || yOffset > 64 || maxSlope < 0 || maxSlope > 32) throw new IOException("Assembly settings exceed their bounds.");
        var names = new HashSet<String>(); long cells = 0;
        for (var part : parts) {
            if (!part.name.matches("[a-z0-9_-]{1,32}") || !names.add(part.name)) throw new IOException("Use unique module names of 1–32 lowercase letters, digits, underscores or hyphens.");
            var p = part.offset;
            if (p.getX() < 0 || p.getY() < 0 || p.getZ() < 0 || p.getX() > 95 || p.getY() > 95 || p.getZ() > 95) throw new IOException("Module offsets must be 0–95 on every axis.");
            cells += part.original.template().blocks().size();
        }
        cells+=themes.stream().flatMap(t->t.variants().stream()).mapToLong(v->v.snapshot().template().blocks().size()).sum();
        if (cells > ModularStructure.MAX_BLOCKS) throw new IOException("The complete assembly and variant catalog supports at most 65,536 occupied cells.");
        return this;
    }
    ModularStructure structure(MinecraftServer server) throws IOException {
        check(); if (parts.isEmpty()) throw new IOException("Add a saved template to begin the assembly.");
        var biomeHolder = server.registryAccess().lookupOrThrow(Registries.BIOME).get(ResourceKey.create(Registries.BIOME, biome))
                .orElseThrow(() -> new IOException("Missing biome " + biome));
        var modules = new ArrayList<ModularStructure.Module>();
        for (var part : parts) modules.add(new ModularStructure.Module(part.name, part.offset, part.transformed()));
        var settings = new Structure.StructureSettings(HolderSet.direct(biomeHolder), Map.of(), GenerationStep.Decoration.SURFACE_STRUCTURES, TerrainAdjustment.NONE);
        var transformedThemes=themes.isEmpty()?Optional.<StructureThemes>empty():Optional.of(themes.orElseThrow().transform(parts));
        var structure = new ModularStructure(settings, modules, yOffset, maxSlope, randomRotation, pool,anchor,foundation,transformedThemes);
        var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        var encoded = Structure.DIRECT_CODEC.encodeStart(ops, structure).getOrThrow(IOException::new);
        var checked = (ModularStructure) Structure.DIRECT_CODEC.parse(ops, encoded).getOrThrow(IOException::new);
        checked.validateTemplates(server.registryAccess()); return checked;
    }
    long estimatedBytes() throws IOException {
        long bytes = 2048;
        for (var part : parts) {
            var output = new ByteArrayOutputStream();
            NbtIo.write(part.original.toTag(), new DataOutputStream(output));
            bytes += output.size() * 4L + part.original.template().blocks().size() * 256L;
        }
        for(var variant:themes.stream().flatMap(t->t.variants().stream()).toList()){var output=new ByteArrayOutputStream();NbtIo.write(variant.snapshot().toTag(),new DataOutputStream(output));bytes+=output.size()*4L+variant.snapshot().template().blocks().size()*256L;}
        bytes+=themes.map(t->t.palettes().stream().mapToLong(p->512L*p.mappings().size()).sum()).orElse(0L);
        return bytes;
    }
    static AssemblyDraft load(MinecraftServer server, Identifier id) throws IOException {
        var file = path(server, id);
        if (Files.size(file) > MAX_JSON) throw new IOException("Assembly draft exceeds the 8 MiB limit.");
        try (var reader = Files.newBufferedReader(file)) {
            var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
            var draft = CODEC.parse(ops, JsonParser.parseReader(reader)).getOrThrow(IOException::new).check();
            if (!draft.id.equals(id)) throw new IOException("Assembly filename and ID disagree.");
            return draft;
        } catch (RuntimeException e) { throw new IOException("Cannot read assembly " + id + ": " + e.getMessage(), e); }
    }
    static AssemblyDraft save(MinecraftServer server, AssemblyDraft draft, int expectedRevision) throws IOException {
        draft.check(); var target = path(server, draft.id);
        if (expectedRevision < 0 ? Files.exists(target) : !Files.exists(target) || load(server, draft.id).revision != expectedRevision)
            throw new IOException("This assembly was saved by another author. Reopen it before saving your changes.");
        var saved = new AssemblyDraft(draft.id, expectedRevision < 0 ? 0 : Math.incrementExact(expectedRevision), draft.biome, draft.yOffset, draft.maxSlope, draft.randomRotation, draft.parts, draft.pool,draft.anchor);
        var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        JsonElement encoded = CODEC.encodeStart(ops, saved).getOrThrow(IOException::new);
        byte[] bytes = (new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(encoded) + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > MAX_JSON) throw new IOException("Assembly draft exceeds the 8 MiB limit.");
        Files.createDirectories(target.getParent()); var temp = Files.createTempFile(target.getParent(), ".assembly-", ".tmp");
        try {
            Files.write(temp, bytes);
            if (expectedRevision < 0) Files.move(temp, target); else Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
        return saved;
    }
    static List<Identifier> list(MinecraftServer server) throws IOException {
        var root = directory(server); if (!Files.exists(root)) return List.of();
        try (var files = Files.walk(root)) {
            return files.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS) && p.toString().endsWith(".json"))
                    .map(root::relativize).map(Path::toString).map(s -> s.replace('\\', '/')).filter(s -> s.contains("/"))
                    .map(s -> Identifier.parse(s.substring(0, s.indexOf('/')) + ":" + s.substring(s.indexOf('/') + 1, s.length() - 5)))
                    .sorted(Comparator.comparing(Identifier::toString)).limit(256).toList();
        }
    }
    static Path directory(MinecraftServer server) { return com.gwsheridan.worldsunwritten.compatibility.legacy.WorldFiles.directory(server.getWorldPath(LevelResource.DATA)).resolve("assemblies"); }
    private static Path path(MinecraftServer server, Identifier id) throws IOException {
        safeId(id); var root = directory(server).toAbsolutePath().normalize(); var target = root.resolve(id.getNamespace()).resolve(id.getPath() + ".json");
        for (var current = target; current != null && current.startsWith(root); current = current.getParent()) if (Files.isSymbolicLink(current)) throw new IOException("Assembly paths cannot contain symbolic links.");
        return target;
    }
    static void safeId(Identifier id) throws IOException {
        if (id.getNamespace().equals("minecraft") || !id.getNamespace().matches("[a-z0-9_-]{1,48}") || id.getPath().length() > 100 || !id.getPath().matches("[a-z0-9_-]+(/[a-z0-9_-]+)*"))
            throw new IOException("Choose a non-Minecraft ID with lowercase letters, digits, underscores or hyphens.");
    }
}
