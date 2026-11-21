package com.gwsheridan.worldarchitect.compatibility;

import com.gwsheridan.worldarchitect.WorldArchitect;
import com.gwsheridan.worldarchitect.api.DimensionAdapterRegistry;
import com.gwsheridan.worldarchitect.dimension.DimensionCatalog;
import com.gwsheridan.worldarchitect.modpack.ModpackRuntime;
import com.gwsheridan.worldarchitect.modpack.PresetBundle;
import com.gwsheridan.worldarchitect.modpack.WorldBundles;
import com.gwsheridan.worldarchitect.skyblock.IslandAllocations;
import com.gwsheridan.worldarchitect.structure.ModularStructure;
import com.gwsheridan.worldarchitect.spawn.WorldAdministration;
import com.gwsheridan.worldarchitect.worldgen.ManagedGenerator;
import com.gwsheridan.worldarchitect.worldgen.NativeStructures;
import com.gwsheridan.worldarchitect.worldgen.SolidColumnGenerator;
import com.gwsheridan.worldarchitect.worldgen.VoidGenerator;
import com.mojang.serialization.Dynamic;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

public final class SaveProtection {
    private static final String STRUCTURE_DEPENDENCIES = "structure_dependencies";
    private static final String PROTECTED_SAVE = "worldarchitect_protected";

    private SaveProtection() {}

    public static void markLevelData(CompoundTag root, Path dataDirectory) {
        WorldAdministration.markLevelData(root, dataDirectory);
        if (GenerationSnapshot.exists(dataDirectory)) {
            root.getCompound("Data").ifPresent(data -> data.putBoolean(PROTECTED_SAVE, true));
        }
    }

    public static boolean knowsProtectedSave(Path dataDirectory, Dynamic<?> levelData) {
        return levelData.get(PROTECTED_SAVE).asBoolean().result().orElse(false)
                || IslandAllocations.exists(dataDirectory);
    }

    public static void recordNewWorld(ServerAboutToStartEvent event) {
        var server = event.getServer();
        WorldGenSettings settings = server.getWorldGenSettings();
        var liveDimensions = server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM);
        var worldDimensions = new WorldDimensions(liveDimensions);
        var bundle = WorldBundles.active(server);
        ModpackRuntime.validate(bundle, settings.options(), settings.dimensions(), worldDimensions, server.registryAccess(),
                server.getPackRepository().getSelectedIds());
        settings.dimensions().dimensions().forEach((key, stem) -> {
            if (stem.generator() instanceof ManagedGenerator) {
                LevelStem live = liveDimensions.getValue(key);
                if (live == null || live.generator() != stem.generator()) {
                    throw incompatible("A datapack overrides the requested generator for " + key.identifier());
                }
                validateGenerator(key, stem);
            }
        });
        var dataDirectory = server.getWorldPath(LevelResource.DATA);
        com.gwsheridan.worldarchitect.rules.RuleSpawns.validate(settings.dimensions().dimensions(), server.registryAccess());
        var original = GenerationSnapshot.load(dataDirectory);
        if (original.isEmpty() && bundle.isEmpty()
                && settings.dimensions().dimensions().values().stream().noneMatch(stem -> stem.generator() instanceof ManagedGenerator)) {
            return;
        }
        boolean newWorld = Files.notExists(GenerationSnapshot.settingsPath(dataDirectory));
        if (!newWorld && original.isEmpty()) {
            throw incompatible("The original generation snapshot is missing");
        }
        if (original.isPresent()) {
            var saved = original.orElseThrow();
            verifyBundle(saved, bundle);
            if (!saved.getCompoundOrEmpty(GenerationSnapshot.SETTINGS).equals(encodeSettings(settings, server.registryAccess()))) {
                throw incompatible("The live generation settings differ from the original snapshot");
            }
            if (!saved.getCompoundOrEmpty(STRUCTURE_DEPENDENCIES).equals(structureDependencies(settings, server.registryAccess()))) {
                throw incompatible("A saved structure definition or its applicable biomes changed");
            }
            if (GenerationSnapshot.hasDependencyPins(saved)) {
                GenerationDependencies.verify(saved.getCompoundOrEmpty(GenerationDependencies.PINS),
                        GenerationDependencies.capture(worldDimensions, server.registryAccess(), server.getWorldData().getDataConfiguration(),
                                server.getResourceManager(), server.getWorldPath(LevelResource.GENERATED_DIR)));
            }
        }
        LevelStem overworld = settings.dimensions().dimensions().get(LevelStem.OVERWORLD);
        for(var stem:settings.dimensions().dimensions().values())if(stem.generator() instanceof com.gwsheridan.worldarchitect.worldgen.SkyblockGenerator rich){
            try{rich.validateAssets(server.registryAccess());}catch(java.io.IOException error){throw incompatible(error.getMessage());}
        }
        for(var stem:settings.dimensions().dimensions().values())if(stem.generator() instanceof com.gwsheridan.worldarchitect.worldgen.CellGenerator cells)try{cells.validateAssets(server.registryAccess());}catch(IOException error){throw incompatible(error.getMessage());}
        com.gwsheridan.worldarchitect.cells.CellWorlds.prepare(server,newWorld&&original.isEmpty());
        if(overworld.generator() instanceof com.gwsheridan.worldarchitect.worldgen.SkyblockGenerator rich && rich.catalog().personalSlots()>0){
            IslandAllocations.prepareCatalog(dataDirectory,rich.catalog(),settings.options().seed(),newWorld&&original.isEmpty());
        } else if (overworld.generator() instanceof VoidGenerator generator) {
            generator.layout().filter(island -> island.personalSlots() > 0).ifPresent(island ->
                    IslandAllocations.prepare(dataDirectory, island.personalSlots(), newWorld && original.isEmpty()));
        }
        bundle.ifPresent(value -> WorldBundles.preserve(dataDirectory, value));
        if(newWorld&&original.isEmpty())bundle.flatMap(value->value.manifest().pack()).ifPresent(policy->WorldAdministration.initializePack(server,policy.defaults()));
        if (original.isPresent()) {
            return;
        }

        CompoundTag root = new CompoundTag();
        root.putInt("snapshot_schema", GenerationSnapshot.SCHEMA);
        root.put(GenerationSnapshot.SETTINGS, encodeSettings(settings, server.registryAccess()));
        root.put(GenerationDependencies.PINS, GenerationDependencies.capture(worldDimensions, server.registryAccess(),
                server.getWorldData().getDataConfiguration(), server.getResourceManager(), server.getWorldPath(LevelResource.GENERATED_DIR)));
        CompoundTag dependencies = structureDependencies(settings, server.registryAccess());
        if (!dependencies.isEmpty()) {
            root.put(STRUCTURE_DEPENDENCIES, dependencies);
        }
        bundle.ifPresent(value -> {
            root.put(GenerationSnapshot.ARCHIVE, value.pin());
            if (value.manifest().locked()) {
                root.put(ModpackRuntime.SNAPSHOT_KEY, value.pin());
            }
        });
        NbtUtils.addCurrentDataVersion(root);
        GenerationSnapshot.initialize(dataDirectory, root);
    }

    private static CompoundTag encodeSettings(WorldGenSettings settings, HolderLookup.Provider registries) {
        return (CompoundTag) WorldGenSettings.CODEC.encodeStart(RegistryOps.create(NbtOps.INSTANCE, registries), settings).getOrThrow();
    }

    private static void verifyBundle(CompoundTag snapshot, Optional<PresetBundle> active) {
        var lock = WorldBundles.lockPin(snapshot);
        var pinned = GenerationSnapshot.hasDependencyPins(snapshot) ? GenerationSnapshot.archivePin(snapshot) : lock;
        if (!pinned.equals(active.map(PresetBundle::pin))
                || !lock.equals(active.filter(bundle -> bundle.manifest().locked()).map(PresetBundle::pin))) {
            throw incompatible("The world-local preset archive or its lock differs from the original snapshot");
        }
    }

    private static CompoundTag structureDependencies(WorldGenSettings settings, HolderLookup.Provider registries) {
        CompoundTag result = new CompoundTag();
        settings.dimensions().dimensions().forEach((key, stem) -> {
            if (stem.generator() instanceof SolidColumnGenerator generator) {
                for (var rule : generator.structureRules()) {
                    var id = rule.structure().unwrapKey().orElseThrow().identifier();
                    var structure = rule.structure().value();
                    if (structure instanceof ModularStructure modular) {
                        try {
                            modular.validateTemplates(registries);
                        } catch (IOException e) {
                            throw incompatible("Invalid template metadata in " + id + ". " + e.getMessage());
                        }
                    }
                    if (generator.getBiomeSource().possibleBiomes().stream().noneMatch(structure.biomes()::contains)) {
                        throw incompatible("Structure " + id + " has no matching biome in " + key.identifier());
                    }
                    if (!result.contains(id.toString())) {
                        result.put(id.toString(), NativeStructures.definition(structure, registries));
                    }
                }
            }
        });
        return result;
    }

    public static void verify(LevelStorageSource.LevelStorageAccess access, Dynamic<?> levelData, WorldDataConfiguration configuration,
            Registry<LevelStem> datapackDimensions, HolderLookup.Provider registries) {
        var dataDirectory = access.getLevelPath(LevelResource.DATA);
        WorldAdministration.verify(dataDirectory, levelData);
        var original = GenerationSnapshot.load(dataDirectory);
        var savedBundlePacks = levelData.get("DataPacks").get("Enabled").asStream()
                .flatMap(value -> value.asString().result().stream()).filter(WorldBundles::isBundlePack).toList();
        boolean bundled = !savedBundlePacks.isEmpty() || configuration.dataPacks().getEnabled().stream().anyMatch(WorldBundles::isBundlePack);
        boolean protectedSave = bundled || knowsProtectedSave(dataDirectory, levelData);
        CompoundTag settings;
        try {
            settings = GenerationSnapshot.read(GenerationSnapshot.settingsPath(dataDirectory));
        } catch (IOException | RuntimeException e) {
            if (original.isPresent() || protectedSave) {
                throw incompatible("Cannot read the saved world generation settings: " + e.getMessage());
            }
            return;
        }
        CompoundTag data = settings.getCompoundOrEmpty(GenerationSnapshot.SETTINGS);
        CompoundTag dimensions = data.getCompoundOrEmpty("dimensions");
        boolean hasArchitectGenerator = dimensions.keySet().stream().anyMatch(key -> isArchitect(dimensions.getCompoundOrEmpty(key)));
        if (original.isEmpty()) {
            if (protectedSave || hasArchitectGenerator) {
                throw incompatible("The original generation snapshot is missing");
            }
            return;
        }
        if (NbtUtils.getDataVersion(settings) != SharedConstants.getCurrentVersion().dataVersion().version()) {
            throw incompatible("The saved generation settings require a different Minecraft data version");
        }
        CompoundTag saved = original.orElseThrow();
        if (!savedBundlePacks.isEmpty() && !GenerationSnapshot.hasDependencyPins(saved) && WorldBundles.lockPin(saved).isEmpty()) {
            throw incompatible("This legacy world used an unlocked bundle without an archive fingerprint. "
                    + "Open it with the previous World Architect version and its original bundle");
        }
        if (NbtUtils.getDataVersion(saved) != NbtUtils.getDataVersion(settings)
                || !saved.getCompoundOrEmpty(GenerationSnapshot.SETTINGS).equals(data)) {
            throw incompatible("The save's current generation settings differ from its original snapshot");
        }
        CompoundTag structurePins = saved.getCompoundOrEmpty(STRUCTURE_DEPENDENCIES);
        var bundle = WorldBundles.select(access, original);
        verifyBundle(saved, bundle);

        var ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        for (String key : dimensions.keySet()) {
            CompoundTag entry = dimensions.getCompoundOrEmpty(key);
            var decoded = LevelStem.CODEC.parse(ops, entry);
            LevelStem stem = decoded.result().orElseThrow(() -> incompatible("Cannot decode " + key + ": "
                    + decoded.error().orElseThrow().message()));
            if (stem.generator() instanceof ManagedGenerator) {
                var dimension = ResourceKey.create(Registries.LEVEL_STEM, Identifier.parse(key));
                validateGenerator(dimension, stem);
                datapackDimensions.getOptional(dimension).ifPresent(source -> validateOverride(dimension, stem, source));
            }
            if (stem.generator() instanceof SolidColumnGenerator generator) {
                for (var rule : generator.structureRules()) {
                    String id = rule.structure().unwrapKey().orElseThrow().identifier().toString();
                    var pinned = structurePins.getCompoundOrEmpty(id).get("registered");
                    if (!Objects.equals(pinned, NativeStructures.registeredDefinition(rule.structure().value(), registries))) {
                        throw incompatible("A saved structure definition or its applicable biomes changed: " + id);
                    }
                }
            }
        }
        var decoded = WorldGenSettings.CODEC.parse(ops, data);
        if (decoded.error().isPresent()) {
            throw incompatible(decoded.error().orElseThrow().message());
        }
        var chosen = decoded.getOrThrow();
        com.gwsheridan.worldarchitect.cells.CellWorlds.verifySaved(dataDirectory,chosen.dimensions(),chosen.options().seed(),registries);
        var effective = new WorldDimensions(chosen.dimensions().bake(datapackDimensions).dimensions());
        ModpackRuntime.validate(bundle, chosen.options(), chosen.dimensions(), effective, registries, configuration.dataPacks().getEnabled());
        if (GenerationSnapshot.hasDependencyPins(saved)) {
            GenerationDependencies.verify(saved.getCompoundOrEmpty(GenerationDependencies.PINS),
                    GenerationDependencies.capture(effective, registries, configuration, GenerationDependencies.loadingResources(),
                            access.getLevelPath(LevelResource.GENERATED_DIR)));
        }
    }

    public static void validateOverride(ResourceKey<LevelStem> key, LevelStem stem, LevelStem source) {
        ManagedGenerator generator = (ManagedGenerator) stem.generator();
        var profile = generator.dimensionProfile().orElseThrow(() -> incompatible("A datapack overrides the saved legacy generator for " + key.identifier()));
        if (profile.adapter().isEmpty() && (!DimensionCatalog.usesStandardGenerator(source)
                || DimensionAdapterRegistry.get(key.identifier()).isPresent())) {
            throw incompatible("The source generator now requires a dimension adapter for " + key.identifier());
        }
        validateGenerator(key, stem);
        profile.adapter().ifPresent(binding -> {
            var adapter = DimensionAdapterRegistry.get(binding.id()).orElseThrow();
            var mode = generator.terrainMode();
            if (!adapter.inspect(key, source).modes().contains(mode)) {
                throw incompatible("The adapter for " + key.identifier() + " does not support " + mode);
            }
            if(mode==com.gwsheridan.worldarchitect.api.DimensionAdapter.TerrainMode.CELLULAR&&!adapter.supportsCellularTerrain())throw incompatible("The adapter for "+key.identifier()+" has not opted into cellular terrain");
        });
    }

    public static void validateGenerator(ResourceKey<LevelStem> key, LevelStem stem) {
        var chunkGenerator = stem.generator();
        ManagedGenerator generator = (ManagedGenerator) chunkGenerator;
        generator.dimensionProfile().ifPresent(profile -> {
            if (!profile.rules().isEmpty() && !profile.rules().dimension().equals(key.identifier())) {
                throw incompatible("Rule engine binding " + profile.rules().dimension() + " does not match " + key.identifier());
            }
        });
        var type = stem.type().value();
        if(generator instanceof com.gwsheridan.worldarchitect.worldgen.CellGenerator cells&&cells.cells().progressive()&&type.hasEnderDragonFight())throw incompatible("Progressive cells require a dimension without a native dragon fight. Keep static cells in the End or use a separate custom dimension");
        if (generator.endProgression().isPresent() && (!key.equals(LevelStem.END) || !type.hasEnderDragonFight()
                || type.minY() != 0 || type.height() != 256 || generator.dimensionProfile().orElseThrow().adapter().isPresent())) {
            throw incompatible("Vanilla End progression requires minecraft:the_end with its native fight and 0..255 build range");
        }
        if (type.minY() != chunkGenerator.getMinY() || type.height() != chunkGenerator.getGenDepth()) {
            throw incompatible("The vertical range of " + key.identifier() + " changed from the saved generator's range");
        }
        generator.profileProblem().ifPresent(problem -> {
            throw incompatible("Invalid generation rules in " + key.identifier() + ": " + problem);
        });
        if (generator instanceof VoidGenerator empty) {
            if (key.equals(LevelStem.OVERWORLD) && empty.layout().isEmpty() && !empty.allowUnsafeSpawn()) {
                throw incompatible("The empty Overworld has no safe spawn. Configure an island or explicitly set allow_unsafe_spawn to true");
            }
            if (!key.equals(LevelStem.OVERWORLD) && empty.layout().filter(island -> island.personalSlots() > 0).isPresent()) {
                throw incompatible("Personal Skyblock allocations require an Overworld Skyblock profile");
            }
        }
        generator.dimensionProfile().flatMap(profile -> profile.adapter()).ifPresent(binding -> {
            if (!binding.id().equals(key.identifier()) || DimensionAdapterRegistry.get(binding.id())
                    .filter(adapter -> adapter.version() == binding.version()).isEmpty()) {
                throw incompatible("Dimension " + key.identifier() + " requires adapter " + binding.id() + " version " + binding.version());
            }
            if(generator.terrainMode()==com.gwsheridan.worldarchitect.api.DimensionAdapter.TerrainMode.CELLULAR&&!DimensionAdapterRegistry.get(binding.id()).orElseThrow().supportsCellularTerrain())throw incompatible("The adapter for "+binding.id()+" has not opted into cellular terrain");
        });
    }

    private static boolean isArchitect(CompoundTag stem) {
        return stem.getCompoundOrEmpty("generator").getStringOr("type", "").startsWith(WorldArchitect.ID + ":");
    }

    public static IllegalStateException incompatible(String detail) {
        return new IncompatibleSaveException("World Architect stopped the world load. " + detail
                + ". Restore the original world files and compatible mods or datapacks before reopening the save.");
    }

    public static final class IncompatibleSaveException extends IllegalStateException {
        private IncompatibleSaveException(String message) {
            super(message);
        }
    }
}
