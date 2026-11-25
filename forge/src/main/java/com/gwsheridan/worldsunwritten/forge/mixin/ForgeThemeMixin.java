package com.gwsheridan.worldsunwritten.forge.mixin;

import com.gwsheridan.worldsunwritten.client.UnwrittenTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
abstract class ForgeThemeMixin {
    @Inject(method = "extractBackground", at = @At("TAIL"))
    private void worldsunwritten$background(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partialTick, CallbackInfo callback) {
        UnwrittenTheme.background((Screen) (Object) this, graphics);
    }
}
