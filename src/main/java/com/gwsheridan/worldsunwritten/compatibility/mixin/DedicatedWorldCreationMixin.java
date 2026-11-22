package com.gwsheridan.worldsunwritten.compatibility.mixin;

import com.gwsheridan.worldsunwritten.compatibility.SaveProtection;
import com.gwsheridan.worldsunwritten.modpack.ModpackRuntime;
import com.gwsheridan.worldsunwritten.worldgen.ManagedGenerator;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.Main;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.server.dedicated.DedicatedServerSettings;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.storage.LevelDataAndDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Main.class)
abstract class DedicatedWorldCreationMixin {
    @WrapOperation(method = "createNewWorldData", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/server/dedicated/DedicatedServerProperties;createDimensions(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/level/levelgen/WorldDimensions;"))
    private static WorldDimensions worldsunwritten$selectPreset(DedicatedServerProperties properties, HolderLookup.Provider registries,
            Operation<WorldDimensions> original) {
        return ModpackRuntime.dedicatedDimensions(registries, () -> original.call(properties, registries));
    }

    @ModifyVariable(method = "createNewWorldData", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private static boolean worldsunwritten$bundleBonusChest(boolean requested) {
        return ModpackRuntime.defaultBonusChest(requested);
    }

    @Inject(method = "createNewWorldData", at = @At("RETURN"))
    private static void worldsunwritten$verifyBeforeWritingWorld(DedicatedServerSettings properties, WorldLoader.DataLoadContext context,
            Registry<LevelStem> datapackDimensions, boolean demo, boolean chest,
            CallbackInfoReturnable<WorldLoader.DataLoadOutput<LevelDataAndDimensions.WorldDataAndGenSettings>> callback) {
        var result = callback.getReturnValue();
        var settings = result.cookie().genSettings();
        var live = new WorldDimensions(result.finalDimensions().lookupOrThrow(Registries.LEVEL_STEM));
        live.dimensions().forEach((key, stem) -> {
            if (stem.generator() instanceof ManagedGenerator) {
                SaveProtection.validateGenerator(key, stem);
            }
        });
        ModpackRuntime.validate(settings.options(), settings.dimensions(), live, context.datapackWorldRegistries(),
                context.dataConfiguration().dataPacks().getEnabled());
    }
}
