package com.gwsheridan.worldsunwritten.modpack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.gwsheridan.worldsunwritten.config.DimensionProfile;
import com.gwsheridan.worldsunwritten.modpack.BundleManifest.Feature;
import com.gwsheridan.worldsunwritten.modpack.BundleManifest.Option;
import com.gwsheridan.worldsunwritten.worldgen.SolidColumnGenerator;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.jspecify.annotations.Nullable;

public final class ModpackRuntime {
    public static final String SNAPSHOT_KEY = "modpack_bundle";

    private ModpackRuntime() {}

    public static Path directory() {
        return com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyConfiguration.directory(FMLPaths.CONFIGDIR.get());
    }

    public static Optional<PresetBundle> installed() {
        if (Loaded.bundleError != null) {
            throw Loaded.bundleError;
        }
        return Loaded.bundle;
    }

    public static Optional<PresetBundle> locked() {
        return installed().filter(bundle -> bundle.manifest().locked());
    }

    public static boolean developerMode() {
        return Loaded.config.developerMode;
    }

    /** Explicit setup action; the loaded policy changes only after the instance restarts. */
    public static void requestAuthoring() throws IOException {
        Path path=directory().resolve("developer.json");Files.createDirectories(path.getParent());
        var config=Files.exists(path)?BundleFiles.readJson(path):new com.google.gson.JsonObject();
        config.addProperty("format",1);config.addProperty("developer_mode",true);
        Config.CODEC.parse(JsonOps.INSTANCE,config).getOrThrow(IOException::new);
        Path temporary=Files.createTempFile(path.getParent(),".developer-",".tmp");
        try{Files.writeString(temporary,config.toString(),java.nio.charset.StandardCharsets.UTF_8);Files.move(temporary,path,java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temporary);}
    }

    public static boolean allowStructureLibrary(MinecraftServer server) {
        return WorldBundles.active(server).map(bundle -> bundle.manifest().allowStructureLibrary()).orElse(true);
    }

    public static void addPack(AddPackFindersEvent event) {
        if (event.getPackType() == PackType.SERVER_DATA && !event.isTrusted()) {
            WorldBundles.forRepositorySource().ifPresent(bundle -> event.addRepositorySource(consumer -> consumer.accept(bundle.pack())));
        }
    }

    public static Properties dedicatedDefaults(Properties properties) {
        // These defaults are read before Minecraft identifies the save. An archived world has its own bundle.
        Loaded.bundle.ifPresent(bundle -> {
            var manifest = bundle.manifest();
            properties.putIfAbsent("level-type", manifest.preset().toString());
            properties.putIfAbsent("level-seed", Long.toString(manifest.options().seed()));
            properties.putIfAbsent("generate-structures", Boolean.toString(manifest.options().generateStructures()));
        });
        return properties;
    }

    public static WorldDimensions dedicatedDimensions(HolderLookup.Provider registries, Supplier<WorldDimensions> vanilla) {
        Path authored = directory().resolve("world-selection.json");
        if (installed().isEmpty() || Files.notExists(authored)) {
            return vanilla.get();
        }
        try {
            return WorldPreset.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, registries), BundleFiles.readJson(authored))
                    .getOrThrow(IOException::new).createWorldDimensions();
        } catch (IOException e) {
            throw problem("Cannot decode world-selection.json: " + e.getMessage());
        }
    }

    public static boolean defaultBonusChest(boolean requested) {
        return requested || installed().map(bundle -> bundle.manifest().options().generateBonusChest()).orElse(false);
    }

    public static void validatePacks(Optional<PresetBundle> selected, Collection<String> enabledPacks, @Nullable PackRepository repository) {
        selected.filter(bundle -> bundle.manifest().locked()).ifPresent(bundle -> {
            bundle.verifyArchive();
            var accepted = new HashSet<>(CommonHooks.getModDataPacksWithVanilla());
            accepted.add(bundle.packId());
            if (repository != null) {
                List.copyOf(accepted).forEach(id -> {
                    var parent = repository.getPack(id);
                    if (parent != null) {
                        parent.getChildren().forEach(child -> accepted.add(child.getId()));
                    }
                });
            }
            if (!enabledPacks.contains(bundle.packId())) {
                throw problem("The required bundle datapack is missing");
            }
            var unexpected = enabledPacks.stream().filter(pack -> !accepted.contains(pack)).sorted().toList();
            if (!unexpected.isEmpty()) {
                throw problem("Locked worlds cannot enable additional datapacks: " + unexpected);
            }
        });
    }

    public static void validate(WorldOptions options, WorldDimensions actual, WorldDimensions live, HolderLookup.Provider registries,
            Collection<String> enabledPacks) {
        validate(installed(), options, actual, live, registries, enabledPacks);
    }

    public static void validate(Optional<PresetBundle> selected, WorldOptions options, WorldDimensions actual, WorldDimensions live,
            HolderLookup.Provider registries, Collection<String> enabledPacks) {
        validatePacks(selected, enabledPacks, null);
        if (selected.isPresent() && !enabledPacks.contains(selected.orElseThrow().packId())) {
            throw problem("The original bundle datapack was not loaded");
        }
        selected.filter(bundle -> bundle.manifest().locked()).ifPresent(bundle -> {
            var manifest = bundle.manifest();
            compareOptions(manifest, options);
            var expected = bundle.expectedPreset(registries).createWorldDimensions();
            validateManifestDimensions(manifest, expected);
            compareDimensions(manifest, expected, actual, registries);
            compareDimensions(manifest, expected, live, registries);
        });
    }

    static void validateManifestDimensions(BundleManifest manifest, WorldDimensions expected) {
        var ids = expected.dimensions().keySet().stream().map(key -> key.identifier()).toList();
        if (!ids.containsAll(manifest.editableDimensions()) || !ids.containsAll(manifest.editableFeatures().keySet())) {
            throw problem("An edit list names a dimension missing from bundled preset " + manifest.preset());
        }
        expected.dimensions().forEach((key, stem) -> {
            if (manifest.editableFeatures().containsKey(key.identifier()) && !(stem.generator() instanceof SolidColumnGenerator)) {
                throw problem("Feature edits require a Single Block or Layered profile in " + key.identifier());
            }
        });
    }

    public static void compareOptions(BundleManifest manifest, WorldOptions actual) {
        var expected = WorldOptions.CODEC.codec().encodeStart(JsonOps.INSTANCE, manifest.options()).getOrThrow().getAsJsonObject();
        var chosen = WorldOptions.CODEC.codec().encodeStart(JsonOps.INSTANCE, actual).getOrThrow().getAsJsonObject();
        for (Option option : manifest.editableOptions()) {
            expected.remove(option.getSerializedName());
            chosen.remove(option.getSerializedName());
        }
        if (!expected.equals(chosen)) {
            var keys = new HashSet<>(expected.keySet());
            keys.addAll(chosen.keySet());
            var modified = keys.stream().filter(key -> !java.util.Objects.equals(expected.get(key), chosen.get(key))).sorted().toList();
            throw problem("Locked world-generation option changed: " + modified);
        }
    }

    public static void compareDimensions(BundleManifest manifest, WorldDimensions expected, WorldDimensions actual, HolderLookup.Provider registries) {
        if (!actual.dimensions().keySet().equals(expected.dimensions().keySet())) {
            throw problem("The dimension set differs from bundled preset " + manifest.preset());
        }
        var ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        expected.dimensions().forEach((key, stem) -> {
            if (!manifest.canEditDimension(key.identifier())) {
                List<Feature> features = manifest.editableFeatures().getOrDefault(key.identifier(), List.of());
                var baseline = lockedStem(stem, features, ops);
                var chosen = lockedStem(actual.dimensions().get(key), features, ops);
                if (!baseline.equals(chosen)) {
                    throw problem("Locked generation settings changed in " + key.identifier());
                }
            }
        });
    }

    private static JsonElement lockedStem(LevelStem stem, List<Feature> features, RegistryOps<JsonElement> ops) {
        if (!features.isEmpty() && stem.generator() instanceof SolidColumnGenerator generator && generator.dimensionProfile().isEmpty()) {
            stem = new LevelStem(stem.type(), generator.withDimensionProfile(new DimensionProfile(generator.getMinY(), generator.getGenDepth(), Optional.empty())));
        }
        JsonObject encoded = LevelStem.CODEC.encodeStart(ops, stem).getOrThrow().getAsJsonObject();
        if (!features.isEmpty() && encoded.getAsJsonObject("generator").get("dimension_profile") instanceof JsonObject profile
                && profile.get("content") instanceof JsonObject content) {
            for (Feature feature : features) {
                feature.fields().forEach(content::remove);
            }
            // Districts version the structure field; an allowed structure edit may change that version.
            if(features.contains(Feature.STRUCTURES))content.remove("version");
            if (content.isEmpty()) {
                profile.remove("content");
            }
        }
        return encoded;
    }

    public static IllegalStateException problem(String detail) {
        return new IllegalStateException("Worlds Unwritten modpack policy: " + detail + ".");
    }

    private record Config(boolean developerMode, Optional<String> bundle) {
        static final Codec<Config> CODEC = RecordCodecBuilder.create(builder -> builder.group(
                Codec.intRange(1, 1).fieldOf("format").forGetter(config -> 1),
                Codec.BOOL.optionalFieldOf("developer_mode", false).forGetter(Config::developerMode),
                Codec.STRING.optionalFieldOf("bundle").forGetter(Config::bundle)
        ).apply(builder, (version, developer, bundle) -> new Config(developer, bundle)));
    }

    private static final class Loaded {
        static final Config config = readConfig();
        static final Optional<PresetBundle> bundle;
        static final @Nullable IllegalStateException bundleError;

        static {
            Optional<PresetBundle> loaded = Optional.empty();
            IllegalStateException failure = null;
            try {
                loaded = config.bundle.map(Loaded::readBundle);
            } catch (IllegalStateException e) {
                failure = e;
            }
            bundle = loaded;
            bundleError = failure;
        }

        private static Config readConfig() {
            Path path = directory().resolve("developer.json");
            if (Files.notExists(path)) {
                return new Config(false, Optional.empty());
            }
            try {
                return Config.CODEC.parse(JsonOps.INSTANCE, BundleFiles.readJson(path)).getOrThrow(IOException::new);
            } catch (IOException e) {
                throw problem("Cannot read " + path + ": " + e.getMessage());
            }
        }

        private static PresetBundle readBundle(String filename) {
            if (!BundleFiles.archiveFilename(filename)) {
                throw problem("Choose a local ZIP filename in developer.json");
            }
            try {
                return PresetBundle.read(directory().resolve("bundles").resolve(filename));
            } catch (IOException e) {
                throw problem("Cannot load installed bundle " + filename + ": " + e.getMessage());
            }
        }
    }
}
