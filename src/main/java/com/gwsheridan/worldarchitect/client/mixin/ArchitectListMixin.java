package com.gwsheridan.worldarchitect.client.mixin;

import com.gwsheridan.worldarchitect.client.ArchitectTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSelectionList.class)
public abstract class ArchitectListMixin {
    @Inject(method="extractListBackground",at=@At("HEAD"),cancellable=true)
    private void architectList(GuiGraphicsExtractor graphics,CallbackInfo callback){
        if(ArchitectTheme.active()){
            var list=(AbstractSelectionList<?>)(Object)this;
            ArchitectTheme.panel(graphics,list.getX(),list.getY(),list.getRight(),list.getBottom(),0xff152024);
            callback.cancel();
        }
    }
    @Inject(method="extractListSeparators",at=@At("HEAD"),cancellable=true)
    private void architectSeparators(GuiGraphicsExtractor graphics,CallbackInfo callback){if(ArchitectTheme.active())callback.cancel();}
}
