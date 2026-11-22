package com.gwsheridan.worldsunwritten.compatibility;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.gwsheridan.worldsunwritten.worldgen.ManagedGenerator;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.structure.structures.EndCityStructure;
import com.gwsheridan.worldsunwritten.worldgen.SolidColumnGenerator;
import net.minecraft.world.level.storage.LevelResource;

public final class GenerationDependencies {
    static final String PINS = "generation_dependencies";
    private static final String RESOURCES = "native_resources";
    private static final ThreadLocal<ResourceManager> LOADING_RESOURCES = new ThreadLocal<>();
    private static final int GENERATION_CONTRACT = 1;

    private GenerationDependencies() {}

    public static <T> T duringLoad(ResourceManager resources, Supplier<T> read) {
        var previous = LOADING_RESOURCES.get();
        LOADING_RESOURCES.set(resources);
        try {
            return read.get();
        } finally {
            if (previous == null) {
                LOADING_RESOURCES.remove();
            } else {
                LOADING_RESOURCES.set(previous);
            }
        }
    }

    static ResourceManager loadingResources() {
        var resources = LOADING_RESOURCES.get();
        if (resources == null) {
            throw SaveProtection.incompatible("Cannot verify the active generation resources");
        }
        return resources;
    }

    static CompoundTag capture(WorldDimensions dimensions, HolderLookup.Provider registries, WorldDataConfiguration configuration,
            ResourceManager resources, Path generated) {
        var ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        CompoundTag pins = new CompoundTag();
        pins.putInt("contract", GENERATION_CONTRACT);
        pins.put("effective_dimensions", WorldDimensions.CODEC.codec().encodeStart(ops, dimensions).getOrThrow());
        var types = new CompoundTag();
        var biomes = new CompoundTag();
        for (var entry : dimensions.dimensions().entrySet()) {
            var key = entry.getKey().identifier().toString();
            var stem = entry.getValue();
            types.put(key, DimensionType.DIRECT_CODEC.encodeStart(ops, stem.type().value()).getOrThrow());
            if (stem.generator() instanceof ManagedGenerator) {
                for (var biome : stem.generator().getBiomeSource().possibleBiomes()) {
                    biome.unwrapKey().ifPresent(id -> {
                        String name = id.identifier().toString();
                        if (!biomes.contains(name)) {
                            biomes.put(name, Biome.DIRECT_CODEC.encodeStart(ops, biome.value()).getOrThrow());
                        }
                    });
                }
            }
        }
        pins.put("dimension_types", types);
        pins.put("managed_biomes", biomes);
        var ruleMods = new CompoundTag();
        dimensions.dimensions().values().stream().filter(stem -> stem.generator() instanceof ManagedGenerator)
                .map(stem -> (ManagedGenerator) stem.generator()).flatMap(generator -> generator.dimensionProfile().stream())
                .flatMap(profile -> profile.rules().rules().stream()).flatMap(rule -> rule.when().mods().stream()).distinct().sorted()
                .forEach(id -> ruleMods.putString(id, com.gwsheridan.worldsunwritten.rules.RuleDependencies.version(id)));
        if (!ruleMods.isEmpty()) pins.put("rule_mods", ruleMods);
        var extensions = com.gwsheridan.worldsunwritten.worldgen.GenerationExtensions.pins(dimensions, registries);
        if (!extensions.isEmpty()) pins.put("native_extensions", extensions);
        ListTag flags = new ListTag();
        FeatureFlags.REGISTRY.toNames(configuration.enabledFeatures()).stream().sorted()
                .forEach(flag -> flags.add(StringTag.valueOf(flag.toString())));
        pins.put("feature_flags", flags);
        if (dimensions.dimensions().values().stream().anyMatch(stem -> !(stem.generator() instanceof ManagedGenerator)
                  || stem.generator() instanceof ManagedGenerator managed && managed.endProgression().isPresent()
                  || stem.generator() instanceof SolidColumnGenerator solid && (com.gwsheridan.worldsunwritten.worldgen.GenerationExtensions.needsResourcePins(solid)
                          || solid.structureRules().stream().anyMatch(rule -> rule.structure().value() instanceof EndCityStructure)))) {
            pins.put(RESOURCES, captureNativeResources(resources, generated));
        }
        return pins;
    }

    static void verify(CompoundTag saved, CompoundTag current) {
        if (saved.getIntOr("contract", 0) != GENERATION_CONTRACT) {
            throw SaveProtection.incompatible("Unsupported world-generation contract " + saved.getIntOr("contract", 0));
        }
        for (String section : List.of("effective_dimensions", "dimension_types", "managed_biomes", "feature_flags", RESOURCES, "rule_mods", "native_extensions")) {
            if (!java.util.Objects.equals(saved.get(section), current.get(section))) {
                String subject = firstDifference(saved.getCompoundOrEmpty(section), current.getCompoundOrEmpty(section));
                throw SaveProtection.incompatible("A saved world-generation dependency changed in " + section + subject);
            }
        }
    }

    public static void verifyReload(MinecraftServer server, Collection<String> enabledPacks) {
        var root = GenerationSnapshot.load(server.getWorldPath(LevelResource.DATA));
        if (root.isEmpty() || !GenerationSnapshot.hasDependencyPins(root.orElseThrow())) {
            return;
        }
        var saved = root.orElseThrow().getCompoundOrEmpty(PINS).getCompound(RESOURCES);
        if (saved.isPresent()) {
            var packs = server.getPackRepository().rebuildSelected(enabledPacks).stream().flatMap(Pack::open).toList();
            try (var proposed = new MultiPackResourceManager(PackType.SERVER_DATA, packs)) {
                var current = captureNativeResources(proposed, server.getWorldPath(LevelResource.GENERATED_DIR));
                if (!saved.orElseThrow().equals(current)) {
                    throw SaveProtection.incompatible("A saved native generation resource changed" + firstDifference(saved.orElseThrow(), current));
                }
            }
        }
    }

    private static String firstDifference(CompoundTag left, CompoundTag right) {
        Set<String> keys = new TreeSet<>(left.keySet());
        keys.addAll(right.keySet());
        return keys.stream().filter(key -> !java.util.Objects.equals(left.get(key), right.get(key))).findFirst()
                .map(key -> ": " + key).orElse("");
    }

    private static CompoundTag captureNativeResources(ResourceManager resources, Path generated) {
        CompoundTag result = new CompoundTag();
        try {
            for (String directory : List.of("worldgen", "neoforge/biome_modifier", "neoforge/structure_modifier")) {
                for (var entry : resources.listResources(directory, GenerationDependencies::generationJson).entrySet()) {
                    result.putString(entry.getKey().toString(), jsonHash(entry.getValue()));
                }
            }
            for (String directory : List.of("tags/worldgen", "tags/block")) {
                for (var entry : resources.listResourceStacks(directory, id -> id.getPath().endsWith(".json")).entrySet()) {
                    ListTag stack = new ListTag();
                    for (Resource resource : entry.getValue()) {
                        stack.add(StringTag.valueOf(jsonHash(resource)));
                    }
                    result.put(entry.getKey().toString(), stack);
                }
            }
            for (var entry : resources.listResources("structure", id -> id.getPath().endsWith(".nbt")).entrySet()) {
                Identifier key = entry.getKey();
                Path local = generated.resolve(key.getNamespace()).resolve(key.getPath()).normalize();
                try (var input = local.startsWith(generated.normalize()) && Files.isRegularFile(local)
                        ? Files.newInputStream(local) : entry.getValue().open()) {
                    result.putString(key.toString(), streamHash(input));
                }
            }
        } catch (IOException | RuntimeException e) {
            throw SaveProtection.incompatible("Cannot fingerprint a native generation resource: " + e.getMessage());
        }
        return result;
    }

    private static boolean generationJson(Identifier id) {
        String path = id.getPath();
        return path.endsWith(".json") && !path.startsWith("worldgen/world_preset/") && !path.startsWith("worldgen/flat_level_generator_preset/");
    }

    private static String jsonHash(Resource resource) throws IOException {
        try (var reader = resource.openAsReader()) {
            StringBuilder canonical = new StringBuilder();
            appendCanonical(JsonParser.parseReader(reader), canonical);
            return HexFormat.of().formatHex(sha256().digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        }
    }

    private static void appendCanonical(JsonElement value, StringBuilder out) {
        if (value.isJsonObject()) {
            var keys = new ArrayList<>(value.getAsJsonObject().entrySet());
            keys.sort(Comparator.comparing(java.util.Map.Entry::getKey));
            out.append('{');
            for (var entry : keys) {
                out.append(new com.google.gson.JsonPrimitive(entry.getKey())).append(':');
                appendCanonical(entry.getValue(), out);
                out.append(',');
            }
            out.append('}');
        } else if (value.isJsonArray()) {
            out.append('[');
            for (var item : value.getAsJsonArray()) {
                appendCanonical(item, out);
                out.append(',');
            }
            out.append(']');
        } else {
            out.append(value);
        }
    }

    private static String streamHash(InputStream input) throws IOException {
        MessageDigest digest = sha256();
        new DigestInputStream(input, digest).transferTo(OutputStream.nullOutputStream());
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
