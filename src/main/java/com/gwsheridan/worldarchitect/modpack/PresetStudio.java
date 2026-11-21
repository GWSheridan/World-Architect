package com.gwsheridan.worldarchitect.modpack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.gwsheridan.worldarchitect.compatibility.SaveProtection;
import com.gwsheridan.worldarchitect.rules.RuleDependencies;
import com.gwsheridan.worldarchitect.rules.RuleSpawns;
import com.gwsheridan.worldarchitect.structure.ModularStructure;
import com.gwsheridan.worldarchitect.structure.NativeTemplate;
import com.gwsheridan.worldarchitect.worldgen.ManagedGenerator;
import com.gwsheridan.worldarchitect.worldgen.SolidColumnGenerator;
import com.gwsheridan.worldarchitect.worldgen.VoidGenerator;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.zip.ZipOutputStream;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Local authoring only. Revisions are immutable bundle archives, never saved-world overrides. */
public final class PresetStudio {
    private PresetStudio() {}
    public record Draft(BundleManifest manifest, JsonObject preset, Map<String, byte[]> templates) {
        public Draft {
            preset = preset.deepCopy();
            var copy = new TreeMap<String, byte[]>(); templates.forEach((key, value) -> copy.put(key, value.clone()));
            templates = java.util.Collections.unmodifiableSortedMap(copy);
        }
        @Override public JsonObject preset() { return preset.deepCopy(); }
        @Override public Map<String, byte[]> templates() {
            var copy = new TreeMap<String, byte[]>(); templates.forEach((key, value) -> copy.put(key, value.clone())); return copy;
        }
        public Draft withManifest(BundleManifest next) { return new Draft(manifest.inheritance().isPresent()&&next.inheritance().isEmpty()?next.withInheritance(manifest.inheritance()):next, preset, templates); }
    }
    public record Revision(String name, String release, BundleManifest manifest) {
        public String label() { return name + " / " + release + " · " + manifest.lock().getSerializedName(); }
    }
    public record Checked(Draft draft, WorldPreset preset, HolderLookup.Provider registries, List<String> report, boolean canApply) {
        public Checked { report = List.copyOf(report); }
    }

    public static Draft capture(BundleManifest manifest, WorldDimensions dimensions, HolderLookup.Provider registries) throws IOException {
        var ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        var json = WorldPreset.DIRECT_CODEC.encodeStart(ops, new WorldPreset(dimensions.dimensions())).getOrThrow(IOException::new).getAsJsonObject();
        var resources = new TreeMap<String, byte[]>();
        for (var entry : dimensions.dimensions().entrySet()) {
            if (entry.getValue().generator() instanceof SolidColumnGenerator solid) {
                for (var selected : solid.structureRules()) {
                    if (selected.structure().value() instanceof ModularStructure) {
                        var id = selected.structure().unwrapKey().orElseThrow().identifier();
                        resources.put(BundleFiles.modularPath(id), BundleFiles.jsonBytes(Structure.DIRECT_CODEC.encodeStart(ops, selected.structure().value()).getOrThrow(IOException::new)));
                    }
                }
            }
            if (entry.getValue().generator() instanceof VoidGenerator empty && empty.island().isPresent()) {
                var island = empty.island().orElseThrow().template();
                var nativeTemplate = new NativeTemplate(island.size(), island.blocks().stream().map(cell -> new NativeTemplate.Cell(cell.pos(), cell.state(), null)).toList());
                var tag = nativeTemplate.toTag();
                var metadata = new net.minecraft.nbt.CompoundTag(); metadata.putInt("format", 1);
                var markers = new net.minecraft.nbt.CompoundTag(); var spawn = new net.minecraft.nbt.CompoundTag();
                var position = new net.minecraft.nbt.ListTag(); position.add(net.minecraft.nbt.IntTag.valueOf(island.spawn().getX())); position.add(net.minecraft.nbt.IntTag.valueOf(island.spawn().getY())); position.add(net.minecraft.nbt.IntTag.valueOf(island.spawn().getZ()));
                spawn.put("pos", position); spawn.putString("facing", "south"); markers.put("spawn", spawn); metadata.put("markers", markers); tag.put("worldarchitect", metadata);
                var bytes = new ByteArrayOutputStream(); NbtIo.writeCompressed(tag, bytes);
                var identity = manifest.preset() + "/" + entry.getKey().identifier();
                var id = Identifier.fromNamespaceAndPath(manifest.preset().getNamespace(), "studio/island_" + java.util.UUID.nameUUIDFromBytes(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString().replace("-", ""));
                resources.put(BundleFiles.templatePath(id), bytes.toByteArray());
            }
        }
        var dependencies = new TreeSet<>(manifest.requiredMods()); var namespaces = new TreeSet<String>(); collectNamespaces(json, namespaces);
        namespaces.stream().filter(RuleDependencies.loaded()::contains).forEach(dependencies::add);
        for (var resource : resources.entrySet()) if (resource.getKey().endsWith(".json")) {
            collectNamespaces(com.google.gson.JsonParser.parseString(new String(resource.getValue(), java.nio.charset.StandardCharsets.UTF_8)), namespaces);
        }
        namespaces.stream().filter(RuleDependencies.loaded()::contains).forEach(dependencies::add);
        var m = manifest;
        manifest = new BundleManifest(m.preset(), m.title(), m.description(), m.author(), m.release(), m.lock(), m.options(), m.editableDimensions(), m.editableFeatures(), m.editableOptions(), List.copyOf(dependencies), m.allowStructureLibrary(),m.inheritance());
        return new Draft(manifest, json, resources);
    }

    public static BundleManifest defaults(String name, String release, WorldOptions options) {
        return new BundleManifest(Identifier.fromNamespaceAndPath("wa_presets", name.toLowerCase(java.util.Locale.ROOT)), name,
                "Created with World Architect Preset Studio", "Modpack author", release, BundleManifest.Lock.UNLOCKED, options,
                List.of(), Map.of(), List.of(), List.of("worldarchitect"), true);
    }

    /** Uses a private structure registry overlay; validation never registers definitions in a live world. */
    public static Checked validate(Draft source, HolderLookup.Provider base) throws IOException {
        try {
            var manifest = BundleManifest.CODEC.parse(JsonOps.INSTANCE, BundleManifest.CODEC.encodeStart(JsonOps.INSTANCE, source.manifest()).getOrThrow()).getOrThrow(IOException::new);
            for (String mod : manifest.requiredMods()) if (!RuleDependencies.loaded().contains(mod)) throw new IOException("Missing required mod " + mod);
            var resources = source.templates();
            if (resources.size() > BundleFiles.MAX_TEMPLATES) throw new IOException("At most 128 embedded templates are allowed.");
            long size = 0; boolean canApply = true;
            var definitions = new TreeMap<Identifier, Structure>();
            var baseOps = RegistryOps.create(JsonOps.INSTANCE, base);
            for (var resource : resources.entrySet()) {
                var path = resource.getKey(); var bytes = resource.getValue();
                if (bytes.length > BundleFiles.MAX_TEMPLATE_BYTES || (size += bytes.length) > BundleFiles.MAX_ARCHIVE_BYTES) throw new IOException("Embedded templates exceed their size budget.");
                if (BundleFiles.modularPath(path)) {
                    var json = BundleFiles.readJson(new ByteArrayInputStream(bytes), path);
                    // Decode each native template first so list-codec errors cannot hide a missing block.
                    if (!json.has("modules") || !json.get("modules").isJsonArray()) throw new IOException("Missing modules in " + path);
                    for (var value : json.getAsJsonArray("modules")) {
                        try {
                            var module = value.getAsJsonObject();
                            var template = net.minecraft.nbt.CompoundTag.CODEC.parse(JsonOps.INSTANCE, module.get("template")).getOrThrow(IOException::new);
                            NativeTemplate.read(template);
                        } catch (IOException | RuntimeException e) { throw new IOException(path + ": " + e.getMessage(), e); }
                    }
                    var structure = Structure.DIRECT_CODEC.parse(baseOps, json).getOrThrow(IOException::new);
                    if (!(structure instanceof ModularStructure modular)) throw new IOException("Only modular definitions may be embedded: " + path);
                    modular.validateTemplates(base);
                    var parts = path.split("/", 5); var id = Identifier.fromNamespaceAndPath(parts[1], parts[4].substring(0, parts[4].length() - 5));
                    if (!BundleFiles.safeId(id)) throw new IOException("Invalid embedded structure ID " + id);
                    definitions.put(id, structure);
                    var current = base.lookupOrThrow(Registries.STRUCTURE).get(ResourceKey.create(Registries.STRUCTURE, id));
                    if (current.isEmpty() || !Structure.DIRECT_CODEC.encodeStart(baseOps, current.orElseThrow().value()).getOrThrow()
                            .equals(Structure.DIRECT_CODEC.encodeStart(baseOps, structure).getOrThrow())) canApply = false;
                } else if (BundleFiles.templatePath(path)) {
                    NativeTemplate.read(NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.create(16L * 1024 * 1024)));
                } else throw new IOException("Unsupported embedded resource " + path);
            }
            HolderLookup.Provider registries = base;
            if (!definitions.isEmpty()) {
                var overlay = new MappedRegistry<Structure>(Registries.STRUCTURE, Lifecycle.stable());
                base.lookupOrThrow(Registries.STRUCTURE).listElements().filter(holder -> !definitions.containsKey(holder.key().identifier()))
                        .forEach(holder -> overlay.register(holder.key(), holder.value(), RegistrationInfo.BUILT_IN));
                definitions.forEach((id, structure) -> overlay.register(ResourceKey.create(Registries.STRUCTURE, id), structure, RegistrationInfo.BUILT_IN));
                overlay.freeze();
                registries = HolderLookup.Provider.create(Stream.concat(base.listRegistries().filter(registry -> !registry.key().equals(Registries.STRUCTURE)), Stream.of(overlay)));
            }
            var ops = RegistryOps.create(JsonOps.INSTANCE, registries);
            var preset = WorldPreset.DIRECT_CODEC.parse(ops, source.preset()).getOrThrow(IOException::new);
            if(manifest.inheritance().isPresent())manifest.inheritance().orElseThrow().enforce(source,preset,registries);
            var dimensions = preset.createWorldDimensions();
            ModpackRuntime.validateManifestDimensions(manifest, dimensions);
            dimensions.dimensions().forEach((key, stem) -> { if (stem.generator() instanceof ManagedGenerator) SaveProtection.validateGenerator(key, stem); });
            RuleSpawns.validate(dimensions.dimensions(), registries);
            var encoded = WorldPreset.DIRECT_CODEC.encodeStart(ops, preset).getOrThrow(IOException::new).getAsJsonObject();
            if (BundleFiles.jsonBytes(encoded).length > BundleFiles.MAX_JSON_BYTES) throw new IOException("Preset exceeds the 8 MiB JSON limit.");
            var normalized = new Draft(manifest, encoded, resources);
            var report = report(normalized, dimensions);
            return new Checked(normalized, preset, registries, report, canApply);
        } catch (IllegalArgumentException | IllegalStateException e) { throw new IOException(e.getMessage(), e); }
    }

    private static List<String> report(Draft draft, WorldDimensions dimensions) {
        var rows = new ArrayList<String>(); var mods = new TreeSet<>(draft.manifest().requiredMods());
        rows.add("Preset " + draft.manifest().preset() + " · release " + draft.manifest().release());
        rows.add("Policy: " + draft.manifest().lock().getSerializedName() + " · " + dimensions.dimensions().size() + " dimensions");
        for (String mod : mods) rows.add("Required mod: " + mod + " · installed " + RuleDependencies.version(mod));
        var namespaces = new TreeSet<String>(); collectNamespaces(draft.preset(), namespaces);
        namespaces.stream().filter(namespace -> !Set.of("minecraft", "worldarchitect").contains(namespace)).forEach(namespace ->
                rows.add("Resource namespace: " + namespace + (RuleDependencies.loaded().contains(namespace) ? " · installed mod " + RuleDependencies.version(namespace) : " · datapack resource; ensure its registries are supplied")));
        dimensions.dimensions().forEach((key, stem) -> {
            if (stem.generator() instanceof SolidColumnGenerator solid) {
                solid.content().nativeFeatures().forEach(rule -> {
                    var adapter = com.gwsheridan.worldarchitect.api.FeatureAdapters.get(rule.feature().value()).orElseThrow();
                    rows.add("Native feature: " + rule.feature().unwrapKey().orElseThrow().identifier() + " · adapter " + com.gwsheridan.worldarchitect.api.FeatureAdapters.type(rule.feature().value()) + " version " + adapter.version() + " · work " + adapter.workLimit(rule.feature().value()) * rule.attempts());
                });
                solid.structureRules().forEach(rule -> com.gwsheridan.worldarchitect.api.StructureAdapters.get(rule.structure().value()).ifPresent(adapter ->
                        rows.add("Structure adapter: " + com.gwsheridan.worldarchitect.api.StructureAdapters.type(rule.structure().value()) + " · version " + adapter.version())));
            }
            if (stem.generator() instanceof ManagedGenerator managed) managed.dimensionProfile().ifPresent(profile -> {
                profile.adapter().ifPresent(adapter -> rows.add("Adapter: " + adapter.id() + " · version " + adapter.version()));
                profile.rules().rules().stream().flatMap(rule -> rule.when().mods().stream()).distinct().sorted().forEach(mod -> rows.add("Rule condition mod: " + mod + " · " + RuleDependencies.version(mod) + " (presence/version pinned on world creation)"));
            });
        });
        rows.add("Embedded templates: " + draft.templates.size() + " · " + draft.templates.values().stream().mapToInt(bytes -> bytes.length).sum() + " compressed/source bytes");
        draft.templates.keySet().forEach(path -> rows.add("Embedded: " + path));
        rows.add("Validation passed: profiles, locks, adapters, templates and spawn references.");
        draft.manifest().inheritance().ifPresent(source->rows.addAll(source.report(draft)));
        return rows;
    }
    private static void collectNamespaces(JsonElement json, Set<String> output) {
        if (json.isJsonObject()) json.getAsJsonObject().entrySet().forEach(entry -> collectNamespaces(entry.getValue(), output));
        else if (json.isJsonArray()) json.getAsJsonArray().forEach(value -> collectNamespaces(value, output));
        else if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            String value = json.getAsString(); if (value.matches("[a-z0-9_-]+:[a-z0-9_./-]+(\\[.*])?")) output.add(value.substring(0, value.indexOf(':')));
        }
    }

    public static List<Revision> list(Path directory) throws IOException {
        var library = directory.resolve("studio"); if (Files.notExists(library)) return List.of();
        var revisions = new ArrayList<Revision>();
        try (var files = Files.walk(library, 2)) {
            for (var file : files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && path.getFileName().toString().endsWith(".zip")).limit(257).sorted().toList()) {
                if (revisions.size() == 256) throw new IOException("The Studio library supports at most 256 revisions.");
                var manifest = PresetBundle.summary(file);
                revisions.add(new Revision(file.getParent().getFileName().toString(), manifest.release(), manifest));
            }
        }
        return List.copyOf(revisions);
    }
    /** Detached installed defaults for read-only inspection; does not install or save resources. */
    public static java.util.Optional<Draft> installedDraft() throws IOException {
        var installed=ModpackRuntime.installed();if(installed.isEmpty())return java.util.Optional.empty();var bundle=installed.orElseThrow();
        return java.util.Optional.of(new Draft(bundle.manifest(),bundle.presetJson(),bundle.templates()));
    }
    public static Draft load(Path directory, String name, String release) throws IOException {
        var bundle = PresetBundle.read(revisionPath(directory, name, release));
        if (!bundle.manifest().release().equals(release)) throw new IOException("Revision filename and manifest disagree.");
        return new Draft(bundle.manifest(), bundle.presetJson(), bundle.templates());
    }
    public static String fingerprint(Path directory,String name,String release)throws IOException{return BundleFiles.fingerprint(revisionPath(directory,name,release));}
    /** Resolves once. The child contains all parent resources and never needs the parent archive to run. */
    public static Draft derive(Path directory,String name,String release,String expectedSha,String child,String childRelease,HolderLookup.Provider registries)throws IOException{
        var bundle=PresetBundle.read(revisionPath(directory,name,release));if(!bundle.fingerprint().equals(expectedSha))throw new IOException("The selected parent changed. Reopen its saved revision.");
        var parent=validate(new Draft(bundle.manifest(),bundle.presetJson(),bundle.templates()),registries).draft();
        var inherited=PresetInheritance.capture(parent,expectedSha);var copy=duplicate(parent,child,childRelease);
        return validate(copy.withManifest(copy.manifest().withInheritance(java.util.Optional.of(inherited))),registries).draft();
    }
    public static Draft override(Draft source,String pointer,com.google.gson.JsonElement value,boolean remove,HolderLookup.Provider registries)throws IOException{
        if(source.manifest().inheritance().isEmpty())throw new IOException("Derive a child from a saved parent before editing overrides.");
        var changed=validate(new Draft(source.manifest(),PresetInheritance.override(source.preset(),pointer,value,remove),source.templates()),registries);
        var captured=capture(changed.draft().manifest(),changed.preset().createWorldDimensions(),changed.registries());
        var resources=source.templates();captured.templates().forEach(resources::putIfAbsent);
        return validate(new Draft(captured.manifest(),captured.preset(),resources),registries).draft();
    }
    public static Draft withRelease(Draft source,String release){var m=source.manifest();return source.withManifest(new BundleManifest(m.preset(),m.title(),m.description(),m.author(),release,m.lock(),m.options(),m.editableDimensions(),m.editableFeatures(),m.editableOptions(),m.requiredMods(),m.allowStructureLibrary(),m.inheritance()));}
    public static Path save(Path directory, String name, Draft source, HolderLookup.Provider registries) throws IOException {
        requireDeveloper(); var checked = validate(source, registries); var draft = checked.draft();
        var output = revisionPath(directory, name, draft.manifest().release());
        if (Files.exists(output)) throw new IOException("That preset version already exists. Choose a new release or duplicate it.");
        if (list(directory).size() >= 256) throw new IOException("The Studio library supports at most 256 revisions.");
        Files.createDirectories(output.getParent());
        var temporary = Files.createTempFile(output.getParent(), ".studio-", ".tmp");
        try {
            try (var zip = new ZipOutputStream(Files.newOutputStream(temporary))) {
                ModpackPackager.add(zip, "pack.mcmeta", BundleFiles.jsonBytes(ModpackPackager.packMetadata(draft.manifest().title())));
                ModpackPackager.add(zip, BundleFiles.MANIFEST, BundleFiles.jsonBytes(BundleManifest.CODEC.encodeStart(JsonOps.INSTANCE, draft.manifest()).getOrThrow()));
                ModpackPackager.add(zip, BundleFiles.presetPath(draft.manifest().preset()), BundleFiles.jsonBytes(draft.preset()));
                for (var resource : new TreeMap<>(draft.templates).entrySet()) ModpackPackager.add(zip, resource.getKey(), resource.getValue());
            }
            PresetBundle.read(temporary);
            // No replacement: concurrent writers cannot overwrite an immutable release.
            Files.move(temporary, output);
            return output;
        } finally { Files.deleteIfExists(temporary); }
    }
    public static Path export(Path directory, String name, String release, HolderLookup.Provider registries) throws IOException {
        requireDeveloper(); var draft = load(directory, name, release); validate(draft, registries);
        var filename = name + "-" + release + ".zip";
        if (!BundleFiles.archiveFilename(filename)) throw new IOException("Export filename is too long.");
        var destination = directory.resolve("exports").resolve(filename); Files.createDirectories(destination.getParent());
        if (Files.exists(destination)) throw new IOException("An export with that name already exists. Choose a new release.");
        var temporary = Files.createTempFile(destination.getParent(), ".export-", ".tmp");
        try {
            Files.copy(revisionPath(directory, name, release), temporary, StandardCopyOption.REPLACE_EXISTING);
            PresetBundle.read(temporary);
            Files.move(temporary, destination);
            return destination;
        } finally { Files.deleteIfExists(temporary); }
    }
    public static Draft readImport(Path directory, String filename, HolderLookup.Provider registries) throws IOException {
        requireDeveloper();
        if (!BundleFiles.archiveFilename(filename)) throw new IOException("Choose a local .zip filename from config/worldarchitect/imports.");
        var file = directory.resolve("imports").resolve(filename);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Place the ZIP in config/worldarchitect/imports first.");
        var bundle = PresetBundle.read(file);
        return validate(new Draft(bundle.manifest(), bundle.presetJson(), bundle.templates()), registries).draft();
    }
    public static Draft duplicate(Draft source, String name, String release) {
        var m = source.manifest();
        return source.withManifest(new BundleManifest(Identifier.fromNamespaceAndPath(m.preset().getNamespace(), name.toLowerCase(java.util.Locale.ROOT)),
                m.title().substring(0, Math.min(75, m.title().length())) + " copy", m.description(), m.author(), release, m.lock(), m.options(), m.editableDimensions(), m.editableFeatures(), m.editableOptions(), m.requiredMods(), m.allowStructureLibrary()));
    }

    public static WorldPreset forApply(Draft draft, HolderLookup.Provider base) throws IOException {
        if (!validate(draft, base).canApply()) throw new IOException("Install the exported bundle and restart to load its registered templates before applying this preset.");
        return WorldPreset.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, base), draft.preset()).getOrThrow(IOException::new);
    }
    private static Path revisionPath(Path directory, String name, String release) throws IOException {
        if (!BundleFiles.filename(name) || !release.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,39}")) throw new IOException("Use a simple library name and a release of up to 40 letters, digits, dots, hyphens or underscores.");
        Path root = directory.toAbsolutePath().normalize();
        Path output = root.resolve("studio").resolve(name).resolve(release + ".zip");
        // Reject links in every existing path component, including the configured library root.
        for (Path current = output; current != null && current.startsWith(root); current = current.getParent()) if (Files.isSymbolicLink(current)) throw new IOException("Studio paths cannot contain symbolic links.");
        return output;
    }
    private static void requireDeveloper() throws IOException { if (!ModpackRuntime.developerMode()) throw new IOException("Enable developer_mode in config/worldarchitect/developer.json and restart to author presets."); }
}
