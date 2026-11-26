package com.gwsheridan.worldsunwritten.modpack;

import com.gwsheridan.worldsunwritten.platform.Platform;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;

/** Distribution settings for an authoring revision. They are independent of the world preset. */
public record PackTargets(Map<String, Target> targets) {
    public static final List<String> LOADERS = List.of("neoforge", "forge", "fabric");

    public record Target(String version, List<String> required, List<String> optional) {
        public Target {
            required = required.stream().distinct().sorted().toList();
            optional = optional.stream().distinct().sorted().toList();
            if (version == null || !version.matches("[0-9]+(?:\\.[0-9]+){1,4}")
                    || required.size() + optional.size() > 64
                    || !Collections.disjoint(required, optional)
                    || java.util.stream.Stream.concat(required.stream(), optional.stream())
                            .anyMatch(id -> !id.matches("[a-z][a-z0-9_-]{1,63}"))) {
                throw new IllegalArgumentException("Use a loader version and distinct valid required and optional mod IDs.");
            }
        }

        public List<String> requiredMods(String loader, BundleManifest manifest) {
            var ids = new TreeSet<>(manifest.requiredMods());
            ids.addAll(required);
            if (loader.equals("fabric")) ids.add("fabric-api");
            return List.copyOf(ids);
        }

        public List<String> optionalMods(String loader, BundleManifest manifest) {
            var ids = new TreeSet<>(optional);
            ids.removeAll(requiredMods(loader, manifest));
            return List.copyOf(ids);
        }
    }

    private static final Codec<Target> TARGET = RecordCodecBuilder.create(builder -> builder.group(
            Codec.STRING.fieldOf("version").forGetter(Target::version),
            Codec.STRING.listOf(0, 64).optionalFieldOf("required", List.of()).forGetter(Target::required),
            Codec.STRING.listOf(0, 64).optionalFieldOf("optional", List.of()).forGetter(Target::optional)
    ).apply(builder, Target::new));

    public static final Codec<PackTargets> CODEC = Codec.unboundedMap(Codec.STRING, TARGET)
            .comapFlatMap(map -> {
                if (map.isEmpty() || !LOADERS.containsAll(map.keySet()))
                    return DataResult.error(() -> "Choose at least one supported loader.");
                return DataResult.success(new PackTargets(map));
            }, PackTargets::targets);

    public PackTargets {
        targets = Collections.unmodifiableMap(new TreeMap<>(targets));
        if (targets.isEmpty() || !LOADERS.containsAll(targets.keySet()))
            throw new IllegalArgumentException("Choose at least one supported loader.");
    }

    public static PackTargets current() {
        String loader = Platform.get().loaderId();
        return new PackTargets(Map.of(loader, defaultTarget(loader)));
    }

    public static Target defaultTarget(String loader) {
        if (!LOADERS.contains(loader)) throw new IllegalArgumentException("Unknown loader " + loader);
        var platform = Platform.get();
        String version = platform.loaderId().equals(loader)
                ? platform.loadedMods().get(loader.equals("fabric") ? "fabricloader" : loader) : null;
        if (version == null || !version.matches("[0-9]+(?:\\.[0-9]+){1,4}")) version = runtime().getProperty(loader);
        return new Target(version, List.of(), List.of());
    }

    public static Properties runtime() {
        var properties = new Properties();
        try (var input = PackTargets.class.getResourceAsStream("/worldsunwritten-runtime.properties")) {
            if (input == null) throw new IOException("Missing Worlds Unwritten runtime metadata.");
            properties.load(input);
            return properties;
        } catch (IOException error) {
            throw new java.io.UncheckedIOException(error);
        }
    }

    public PackTargets with(String loader, Target target) {
        var edited = new TreeMap<>(targets);
        if (target == null) edited.remove(loader);
        else edited.put(loader, target);
        return new PackTargets(edited);
    }

    public List<String> report(BundleManifest manifest) {
        var rows = new ArrayList<String>();
        var host = Platform.get();
        targets.forEach((loader, target) -> {
            var required = target.requiredMods(loader, manifest);
            var optional = target.optionalMods(loader, manifest);
            rows.add(loader + " " + target.version());
            rows.add("Required: " + (required.isEmpty() ? "none" : String.join(", ", required)));
            if (!optional.isEmpty()) rows.add("Optional: " + String.join(", ", optional));
            if (loader.equals(host.loaderId())) {
                var missing = new TreeSet<>(required);
                missing.removeAll(host.loadedMods().keySet());
                rows.add(missing.isEmpty() ? "Required mods found in this instance." : "Missing here: " + String.join(", ", missing));
            } else {
                rows.add("Run the test kit on this target to verify its provider registries.");
            }
        });
        return List.copyOf(rows);
    }

    public static PackTargets load(Path directory, String name, String release) throws IOException {
        Path file = path(directory, name, release);
        if (!Files.exists(file)) return current();
        try {
            return CODEC.parse(JsonOps.INSTANCE, BundleFiles.readJson(file)).getOrThrow(IOException::new);
        } catch (IllegalArgumentException error) {
            throw new IOException("Invalid saved loader targets for " + name + " " + release, error);
        }
    }

    public void save(Path directory, String name, String release) throws IOException {
        Path file = path(directory, name, release);
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), ".targets-", ".tmp");
        try {
            Files.write(temporary, BundleFiles.jsonBytes(CODEC.encodeStart(JsonOps.INSTANCE, this).getOrThrow(IOException::new)));
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Path path(Path directory, String name, String release) throws IOException {
        if (!name.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}")
                || !release.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,39}")) {
            throw new IOException("Invalid project name or release.");
        }
        return directory.resolve("studio").resolve(name).resolve(release + ".targets.json");
    }
}
