package com.gwsheridan.worldarchitect.compatibility.mixin;

import com.gwsheridan.worldarchitect.compatibility.SaveProtection;
import com.gwsheridan.worldarchitect.dimension.DimensionCatalog;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(WorldDimensions.class)
abstract class WorldDimensionsMixin {
    // Vanilla gives datapack stems precedence. Only explicitly saved overrides mask a source stem.
    @ModifyVariable(method = "bake", at = @At("HEAD"), argsOnly = true)
    private Registry<LevelStem> worldarchitect$applyExplicitProfiles(Registry<LevelStem> datapackDimensions) {
        WorldDimensions selected = (WorldDimensions) (Object) this;
        if (datapackDimensions.registryKeySet().stream().noneMatch(key -> DimensionCatalog.isExplicitProfile(selected.dimensions().get(key)))) {
            return datapackDimensions;
        }

        MappedRegistry<LevelStem> remaining = new MappedRegistry<>(Registries.LEVEL_STEM, Lifecycle.stable());
        datapackDimensions.listElements().forEach(entry -> {
            LevelStem override = selected.dimensions().get(entry.key());
            if (DimensionCatalog.isExplicitProfile(override)) {
                SaveProtection.validateOverride(entry.key(), override, entry.value());
            } else {
                remaining.register(entry.key(), entry.value(), datapackDimensions.registrationInfo(entry.key()).orElseThrow());
            }
        });
        return remaining.freeze();
    }
}
