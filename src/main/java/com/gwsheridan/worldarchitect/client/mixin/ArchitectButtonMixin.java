package com.gwsheridan.worldarchitect.client.mixin;

import com.gwsheridan.worldarchitect.client.ArchitectTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractButton.class)
public abstract class ArchitectButtonMixin {
    @Inject(method="extractDefaultSprite",at=@At("HEAD"),cancellable=true)
    private void architectSurface(GuiGraphicsExtractor graphics,CallbackInfo callback){
        if(ArchitectTheme.active()){ArchitectTheme.button(graphics,(AbstractWidget)(Object)this);callback.cancel();}
    }
}
