package com.gwsheridan.worldsunwritten.client.mixin;

import com.gwsheridan.worldsunwritten.modpack.BundleManifest.Option;
import com.gwsheridan.worldsunwritten.modpack.ModpackRuntime;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$WorldTab")
abstract class ModpackWorldTabMixin {
    @Shadow @Final private EditBox seedEdit;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void worldsunwritten$seedControls(CallbackInfo callback) {
        if (!allows(Option.SEED)) {
            seedEdit.setEditable(false);
            seedEdit.setTooltip(Tooltip.create(Component.translatable("worldsunwritten.modpack.option_locked")));
        }
    }

    @ModifyArg(method = "<init>", index = 0, at = @At(value = "INVOKE", ordinal = 0, target =
            "Lnet/minecraft/client/gui/screens/worldselection/SwitchGrid$SwitchBuilder;withIsActiveCondition(Ljava/util/function/BooleanSupplier;)Lnet/minecraft/client/gui/screens/worldselection/SwitchGrid$SwitchBuilder;"))
    private BooleanSupplier worldsunwritten$structureControls(BooleanSupplier original) {
        return () -> allows(Option.GENERATE_STRUCTURES) && original.getAsBoolean();
    }

    @ModifyArg(method = "<init>", index = 0, at = @At(value = "INVOKE", ordinal = 1, target =
            "Lnet/minecraft/client/gui/screens/worldselection/SwitchGrid$SwitchBuilder;withIsActiveCondition(Ljava/util/function/BooleanSupplier;)Lnet/minecraft/client/gui/screens/worldselection/SwitchGrid$SwitchBuilder;"))
    private BooleanSupplier worldsunwritten$chestControls(BooleanSupplier original) {
        return () -> allows(Option.BONUS_CHEST) && original.getAsBoolean();
    }

    private static boolean allows(Option option) {
        return ModpackRuntime.locked().map(bundle -> bundle.manifest().canEditOption(option)).orElse(true);
    }
}
