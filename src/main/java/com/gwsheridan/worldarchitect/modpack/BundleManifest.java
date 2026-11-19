package com.gwsheridan.worldarchitect.modpack;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.WorldOptions;

public record BundleManifest(Identifier preset, String title, String description, String author, String release,
        Lock lock, WorldOptions options, List<Identifier> editableDimensions, Map<Identifier, List<Feature>> editableFeatures,
        List<Option> editableOptions, List<String> requiredMods, boolean allowStructureLibrary) {
    public static final Codec<BundleManifest> CODEC = RecordCodecBuilder.<BundleManifest>create(builder -> builder.group(
            Codec.intRange(1, 1).fieldOf("format").forGetter(manifest -> 1),
            Identifier.CODEC.fieldOf("preset").forGetter(BundleManifest::preset),
            Codec.STRING.fieldOf("title").forGetter(BundleManifest::title),
            Codec.STRING.fieldOf("description").forGetter(BundleManifest::description),
            Codec.STRING.fieldOf("author").forGetter(BundleManifest::author),
            Codec.STRING.fieldOf("release").forGetter(BundleManifest::release),
            StringRepresentable.fromEnum(Lock::values).fieldOf("lock").forGetter(BundleManifest::lock),
            WorldOptions.CODEC.codec().fieldOf("options").forGetter(BundleManifest::options),
            Identifier.CODEC.listOf(0, 128).optionalFieldOf("editable_dimensions", List.of()).forGetter(BundleManifest::editableDimensions),
            Codec.unboundedMap(Identifier.CODEC, StringRepresentable.fromEnum(Feature::values).listOf(1, 6))
                    .optionalFieldOf("editable_features", Map.of()).forGetter(BundleManifest::editableFeatures),
            StringRepresentable.fromEnum(Option::values).listOf(0, 3).optionalFieldOf("editable_options", List.of()).forGetter(BundleManifest::editableOptions),
            Codec.STRING.listOf(0, 256).optionalFieldOf("required_mods", List.of()).forGetter(BundleManifest::requiredMods),
            Codec.BOOL.optionalFieldOf("allow_structure_library", true).forGetter(BundleManifest::allowStructureLibrary)
    ).apply(builder, (format, preset, title, description, author, release, lock, options, dimensions, features, editableOptions, mods, library) ->
            new BundleManifest(preset, title, description, author, release, lock, options, dimensions, features, editableOptions, mods, library)))
            .validate(BundleManifest::validate);

    public BundleManifest {
        editableDimensions = List.copyOf(editableDimensions);
        editableFeatures = editableFeatures.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        editableOptions = List.copyOf(editableOptions);
        requiredMods = List.copyOf(requiredMods);
    }

    private DataResult<BundleManifest> validate() {
        if (!BundleFiles.safeId(preset) || preset.getNamespace().equals("minecraft")) {
            return invalid("Use a non-Minecraft namespace and a valid path for the bundled preset ID.");
        }
        if (title.isBlank() || title.length() > 80 || description.length() > 512 || author.isBlank() || author.length() > 64
                || !release.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,39}")) {
            return invalid("Invalid bundle title, description, author or release.");
        }
        boolean hasEdits = !editableDimensions.isEmpty() || !editableFeatures.isEmpty() || !editableOptions.isEmpty();
        if (hasEdits != (lock == Lock.PARTIAL)) {
            return invalid("Only a partial lock accepts edit lists, and it must permit at least one edit.");
        }
        if (editableFeatures.keySet().stream().anyMatch(editableDimensions::contains)) {
            return invalid("Give a dimension either full editing or selected feature editors.");
        }
        if (requiredMods.stream().anyMatch(mod -> !mod.matches("[a-z][a-z0-9_]{1,63}"))) {
            return invalid("Invalid mod ID in required_mods.");
        }
        return DataResult.success(this);
    }

    private static DataResult<BundleManifest> invalid(String problem) {
        return DataResult.error(() -> problem);
    }

    public boolean locked() {
        return lock != Lock.UNLOCKED;
    }

    public boolean canEditDimension(Identifier id) {
        return !locked() || editableDimensions.contains(id);
    }

    public boolean canEditFeature(Identifier id, Feature feature) {
        return canEditDimension(id) || editableFeatures.getOrDefault(id, List.of()).contains(feature);
    }

    public boolean canEditOption(Option option) {
        return !locked() || editableOptions.contains(option);
    }

    public enum Lock implements StringRepresentable {
        UNLOCKED("unlocked"), PARTIAL("partial"), FULL("full");

        private final String name;

        Lock(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public enum Feature implements StringRepresentable {
        BIOMES("biomes", "biomes", "biome_scale", "biome_seed"), ORES("ores", "ores"),
        DECORATIONS("decorations", "decorations"), CAVES("caves", "caves"), FLUIDS("fluids", "sea"),
        STRUCTURES("structures", "structures"), NATIVE_FEATURES("native_features", "native_features");

        private final String name;
        private final List<String> fields;

        Feature(String name, String... fields) {
            this.name = name;
            this.fields = List.of(fields);
        }

        List<String> fields() {
            return fields;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public enum Option implements StringRepresentable {
        SEED("seed"), GENERATE_STRUCTURES("generate_structures"), BONUS_CHEST("bonus_chest");

        private final String name;

        Option(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
