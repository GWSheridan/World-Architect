package com.gwsheridan.worldarchitect.modpack;

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
    static final String PACK_PREFIX = "worldarchitect_bundle/";
    private final Path archive;
    private final String fingerprint;
    private final BundleManifest manifest;
    private final JsonObject preset;

    private PresetBundle(Path archive, String fingerprint, BundleManifest manifest, JsonObject preset) {
        this.archive = archive;
        this.fingerprint = fingerprint;
        this.manifest = manifest;
        this.preset = preset;
    }

    static PresetBundle read(Path archive) throws IOException {
        String fingerprint = BundleFiles.fingerprint(archive);
        try (var zip = new ZipFile(archive.toFile())) {
            if (zip.size() < 3 || zip.size() > BundleFiles.MAX_TEMPLATES + 3) {
                throw new IOException("Unexpected number of files in the preset archive.");
            }
            BundleManifest manifest = BundleManifest.CODEC.parse(JsonOps.INSTANCE, readJson(zip, BundleFiles.MANIFEST)).getOrThrow(IOException::new);
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
                if (BundleFiles.templatePath(name)) {
                    if (++templates > BundleFiles.MAX_TEMPLATES || entry.getSize() < 0 || entry.getSize() > BundleFiles.MAX_TEMPLATE_BYTES
                            || (templateBytes += entry.getSize()) > BundleFiles.MAX_ARCHIVE_BYTES) {
                        throw new IOException("The archive exceeds its bundled structure limits.");
                    }
                } else if (!name.equals("pack.mcmeta") && !name.equals(BundleFiles.MANIFEST) && !name.equals(presetPath)) {
                    throw new IOException("Unsupported preset archive entry " + name);
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

    Path archive() {
        return archive;
    }

    public ResourceKey<WorldPreset> key() {
        return ResourceKey.create(Registries.WORLD_PRESET, manifest.preset());
    }

    String packId() {
        return PACK_PREFIX + manifest.preset();
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
        return WorldPreset.DIRECT_CODEC.parse(ops, preset).getOrThrow(message ->
                ModpackRuntime.problem("Cannot decode bundled preset " + manifest.preset() + ": " + message));
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
