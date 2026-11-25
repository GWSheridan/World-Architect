package com.gwsheridan.worldsunwritten.client.mixin;

import com.gwsheridan.worldsunwritten.WorldsUnwritten;
import com.gwsheridan.worldsunwritten.client.PresetEditors;
import com.gwsheridan.worldsunwritten.modpack.BundleManifest.Option;
import com.gwsheridan.worldsunwritten.modpack.ModpackRuntime;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.nio.file.Path;
import net.minecraft.client.gui.screens.worldselection.PresetEditor;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.WorldTypeEntry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldCreationUiState.class)
abstract class WorldCreationUiStateMixin {
    @Shadow private WorldCreationContext settings;
    @Shadow private WorldTypeEntry worldType;
    @Shadow @Final private List<WorldTypeEntry> normalPresetList;
    @Shadow @Final private List<WorldTypeEntry> altPresetList;
    @Shadow private String seed;
    @Shadow private boolean generateStructures;
    @Shadow private boolean bonusChest;
    @Shadow public abstract void setWorldType(WorldTypeEntry type);

    @Inject(method = "updatePresetLists", at = @At("HEAD"), cancellable = true)
    private void worldsunwritten$restrictPresets(CallbackInfo callback) {
        ModpackRuntime.locked().ifPresent(bundle -> {
            var holder = settings.worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET).get(bundle.key())
                    .orElseThrow(() -> ModpackRuntime.problem("The required world preset is missing"));
            if (worldType.preset() == null || !worldType.preset().is(bundle.key())) {
                settings = settings.withDimensions((registries, old) -> holder.value().createWorldDimensions());
            }
            worldType = new WorldTypeEntry(holder);
            normalPresetList.clear();
            normalPresetList.add(worldType);
            altPresetList.clear();
            altPresetList.add(worldType);
            callback.cancel();
        });
    }

    @Inject(method = "updatePresetLists", at = @At("TAIL"))
    private void worldsunwritten$exposeUnlockedPreset(CallbackInfo callback) {
        if (ModpackRuntime.locked().isEmpty()) {
            for (var list : List.of(normalPresetList, altPresetList)) {
                list.replaceAll(entry -> entry.preset() == null || ModpackRuntime.installed().filter(bundle -> entry.preset().is(bundle.key())).isPresent()
                        ? entry : new WorldTypeEntry(com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyPresets.current(entry.preset(), settings.worldgenLoadContext())));
            }
        }
        ModpackRuntime.installed().filter(bundle -> !bundle.manifest().locked()).ifPresent(bundle ->
                settings.worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET).get(bundle.key()).ifPresent(holder -> {
                    var entry = new WorldTypeEntry(holder);
                    if (!normalPresetList.contains(entry)) {
                        normalPresetList.add(entry);
                    }
                    if (!altPresetList.contains(entry)) {
                        altPresetList.add(entry);
                    }
                }));
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void worldsunwritten$bundleDefaults(Path folder, WorldCreationContext initial, Optional<ResourceKey<WorldPreset>> preset,
            OptionalLong initialSeed, CallbackInfo callback) {
        ModpackRuntime.installed().ifPresent(bundle -> {
            if (!bundle.manifest().locked()) {
                if (initialSeed.isPresent()) {
                    return;
                }
                var holder = settings.worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET).get(bundle.key()).orElseThrow();
                setWorldType(new WorldTypeEntry(holder));
            }
            var options = bundle.manifest().options();
            seed = Long.toString(options.seed());
            generateStructures = options.generateStructures();
            bonusChest = options.generateBonusChest();
            settings = settings.withOptions(old -> options);
        });
    }

    @Inject(method = "setWorldType", at = @At("HEAD"), cancellable = true)
    private void worldsunwritten$keepPreset(WorldTypeEntry requested, CallbackInfo callback) {
        if (ModpackRuntime.locked().isPresent()) {
            callback.cancel();
        }
    }

    @Inject(method = "getPresetEditor", at = @At("HEAD"), cancellable = true)
    private void worldsunwritten$bundleEditor(CallbackInfoReturnable<PresetEditor> callback) {
        ModpackRuntime.installed().filter(bundle -> worldType.preset() != null && worldType.preset().is(bundle.key()))
                .ifPresent(bundle -> callback.setReturnValue(PresetEditors.bundleEditor(bundle)));
        if (!callback.isCancelled() && worldType.preset() != null) {
            worldType.preset().unwrapKey().map(PresetEditors::get).ifPresent(callback::setReturnValue);
        }
    }

    @ModifyVariable(method = "setSeed", at = @At("HEAD"), argsOnly = true)
    private String worldsunwritten$seed(String requested) {
        return ModpackRuntime.locked().filter(bundle -> !bundle.manifest().canEditOption(Option.SEED))
                .map(bundle -> Long.toString(bundle.manifest().options().seed())).orElse(requested);
    }

    @ModifyVariable(method = "setGenerateStructures", at = @At("HEAD"), argsOnly = true)
    private boolean worldsunwritten$structures(boolean requested) {
        return ModpackRuntime.locked().filter(bundle -> !bundle.manifest().canEditOption(Option.GENERATE_STRUCTURES))
                .map(bundle -> bundle.manifest().options().generateStructures()).orElse(requested);
    }

    @ModifyVariable(method = "setBonusChest", at = @At("HEAD"), argsOnly = true)
    private boolean worldsunwritten$bonusChest(boolean requested) {
        return ModpackRuntime.locked().filter(bundle -> !bundle.manifest().canEditOption(Option.BONUS_CHEST))
                .map(bundle -> bundle.manifest().options().generateBonusChest()).orElse(requested);
    }

    @Inject(method = "isBonusChest", at = @At("HEAD"), cancellable = true)
    private void worldsunwritten$preserveLockedChest(CallbackInfoReturnable<Boolean> callback) {
        ModpackRuntime.locked().filter(bundle -> !bundle.manifest().canEditOption(Option.BONUS_CHEST))
                .ifPresent(bundle -> callback.setReturnValue(bundle.manifest().options().generateBonusChest()));
    }

    // The datapack reload path still tests vanilla's editor map before retaining edited dimensions.
    @WrapOperation(method = "updatePresetLists", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object worldsunwritten$recognizeEditor(Map<?, ?> editors, Object key, Operation<Object> original) {
        var bundle = ModpackRuntime.installed();
        if (bundle.isPresent() && Optional.of(bundle.orElseThrow().key()).equals(key)) {
            return PresetEditors.bundleEditor(bundle.orElseThrow());
        }
        if (Optional.of(WorldsUnwritten.PRESET).equals(key)) {
            return worldsunwritten$findEditor(WorldsUnwritten.PRESET);
        }
        if (Optional.of(WorldsUnwritten.LAYERED_PRESET).equals(key)) {
            return worldsunwritten$findEditor(WorldsUnwritten.LAYERED_PRESET);
        }
        if (Optional.of(WorldsUnwritten.DIMENSIONS_PRESET).equals(key)) {
            return worldsunwritten$findEditor(WorldsUnwritten.DIMENSIONS_PRESET);
        }
        return original.call(editors, key);
    }

    @Unique
    private Object worldsunwritten$findEditor(ResourceKey<WorldPreset> key) {
        WorldCreationUiState ui = (WorldCreationUiState) (Object) this;
        var presets = ui.getSettings().worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET);
        return presets.containsKey(key) ? PresetEditors.get(key) : null;
    }
}
