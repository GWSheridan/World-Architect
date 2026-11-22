package com.gwsheridan.worldsunwritten.modpack;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.zip.ZipFile;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.neoforged.fml.ModList;

public final class PresetBundle {
    static final String PACK_PREFIX = "worldsunwritten_bundle/";
    private final Path archive;
    private final String fingerprint;
    private final BundleManifest manifest;
    private final JsonObject preset;
    private final String packNamespace;

    private PresetBundle(Path archive, String fingerprint, BundleManifest manifest, JsonObject preset) {
        this(archive, fingerprint, manifest, preset, com.gwsheridan.worldsunwritten.WorldsUnwritten.ID);
    }
    private PresetBundle(Path archive, String fingerprint, BundleManifest manifest, JsonObject preset, String namespace) {
        this.archive = archive;
        this.fingerprint = fingerprint;
        this.manifest = manifest;
        this.preset = preset;
        this.packNamespace = namespace;
    }

    PresetBundle withPackNamespace(String namespace) { return new PresetBundle(archive, fingerprint, manifest, preset, namespace); }

    static PresetBundle read(Path archive) throws IOException {
        String fingerprint = BundleFiles.fingerprint(archive);
        try (var zip = new ZipFile(archive.toFile())) {
            if (zip.size() < 3 || zip.size() > BundleFiles.MAX_TEMPLATES + 3) {
                throw new IOException("Unexpected number of files in the preset archive.");
            }
            String manifestPath = BundleFiles.manifest(zip);
            BundleManifest manifest = BundleManifest.CODEC.parse(JsonOps.INSTANCE, readJson(zip, manifestPath)).getOrThrow(IOException::new);
            for (String mod : manifest.requiredMods()) {
                if (!ModList.get().isLoaded(mod)) {
                    throw new IOException("Bundle " + manifest.preset() + " requires missing mod " + mod);
                }
            }
            String presetPath = BundleFiles.presetPath(manifest.preset());
            var entries = new HashSet<String>();
            int templates = 0;
            long templateBytes = 0;
            for (var entry : zip.stream().toList()) {
                String name = entry.getName();
                if (!entries.add(name) || entry.isDirectory()) {
                    throw new IOException("Duplicate or unexpected archive entry " + name);
                }
                if (BundleFiles.templatePath(name) || BundleFiles.modularPath(name)) {
                    if (++templates > BundleFiles.MAX_TEMPLATES || entry.getSize() < 0 || entry.getSize() > BundleFiles.MAX_TEMPLATE_BYTES
                            || (templateBytes += entry.getSize()) > BundleFiles.MAX_ARCHIVE_BYTES) {
                        throw new IOException("The archive exceeds its bundled structure limits.");
                    }
                } else if (!name.equals("pack.mcmeta") && !name.equals(manifestPath) && !name.equals(presetPath)) {
                    throw new IOException("Unsupported preset archive entry " + name);
                }
                if (BundleFiles.modularPath(name)) {
                    var definition = readJson(zip, name);
                    if (!definition.has("type") || !com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyIds.modular(definition.get("type").getAsString())) {
                        throw new IOException("Only Worlds Unwritten modular definitions can be embedded: " + name);
                    }
                }
            }
            readJson(zip, "pack.mcmeta");
            return new PresetBundle(archive, fingerprint, manifest, readJson(zip, presetPath));
        }
    }

    private static JsonObject readJson(ZipFile zip, String name) throws IOException {
        var entry = zip.getEntry(name);
        if (entry == null || entry.isDirectory()) {
            throw new IOException("Missing " + name + " in " + zip.getName());
        }
        try (var input = zip.getInputStream(entry)) {
            return BundleFiles.readJson(input, name);
        }
    }

    public BundleManifest manifest() {
        return manifest;
    }
    String fingerprint(){return fingerprint;}

    /** Library labels need metadata only; a missing mod must not hide unrelated saved revisions. */
    static BundleManifest summary(Path archive) throws IOException {
        if (java.nio.file.Files.size(archive) > BundleFiles.MAX_ARCHIVE_BYTES) throw new IOException("Preset archive exceeds 32 MiB: " + archive.getFileName());
        try (var zip = new ZipFile(archive.toFile())) {
            if (zip.size() < 3 || zip.size() > BundleFiles.MAX_TEMPLATES + 3) throw new IOException("Unexpected number of archive entries: " + archive.getFileName());
            return BundleManifest.CODEC.parse(JsonOps.INSTANCE, readJson(zip, BundleFiles.manifest(zip))).getOrThrow(IOException::new);
        }
    }

    JsonObject presetJson() { return preset.deepCopy(); }

    java.util.SortedMap<String, byte[]> templates() throws IOException {
        verifyArchive();
        var result = new java.util.TreeMap<String, byte[]>();
        try (var zip = new ZipFile(archive.toFile())) {
            for (var entry : zip.stream().toList()) {
                if (BundleFiles.templatePath(entry.getName()) || BundleFiles.modularPath(entry.getName())) {
                    try (var input = zip.getInputStream(entry)) {
                        result.put(entry.getName(), BundleFiles.readBytes(input, BundleFiles.MAX_TEMPLATE_BYTES, entry.getName()));
                    }
                }
            }
        }
        return result;
    }

    Path archive() {
        return archive;
    }

    public ResourceKey<WorldPreset> key() {
        return ResourceKey.create(Registries.WORLD_PRESET, manifest.preset());
    }

    String packId() {
        return packNamespace + "_bundle/" + manifest.preset();
    }

    Pack pack() {
        verifyArchive();
        var result = Pack.readMetaAndCreate(new PackLocationInfo(packId(), Component.literal(manifest.title()), PackSource.BUILT_IN,
                        Optional.empty()), new FilePackResources.FileResourcesSupplier(archive), PackType.SERVER_DATA,
                new PackSelectionConfig(true, Pack.Position.TOP, true));
        if (result == null) {
            throw ModpackRuntime.problem("Cannot register datapack from " + archive.getFileName());
        }
        return result;
    }

    void verifyArchive() {
        try {
            if (!fingerprint.equals(BundleFiles.fingerprint(archive))) {
                throw ModpackRuntime.problem("Installed archive changed since startup: " + archive.getFileName());
            }
        } catch (IOException e) {
            throw ModpackRuntime.problem("Cannot read installed archive " + archive.getFileName() + ": " + e.getMessage());
        }
    }

    WorldPreset expectedPreset(HolderLookup.Provider registries) {
        var ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        var decoded=WorldPreset.DIRECT_CODEC.parse(ops, preset).getOrThrow(message ->
                ModpackRuntime.problem("Cannot decode bundled preset " + manifest.preset() + ": " + message));
        if(manifest.inheritance().isPresent())try{manifest.inheritance().orElseThrow().enforce(new PresetStudio.Draft(manifest,preset,templates()),decoded,registries);}catch(IOException e){throw ModpackRuntime.problem(e.getMessage());}
        return decoded;
    }

    public CompoundTag pin() {
        CompoundTag pin = new CompoundTag();
        pin.putInt("format", 1);
        pin.putString("preset", manifest.preset().toString());
        pin.putString("release", manifest.release());
        pin.putString("sha256", fingerprint);
        return pin;
    }
}
