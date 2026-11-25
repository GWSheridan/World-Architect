package com.gwsheridan.worldsunwritten.compatibility.mixin;

import com.gwsheridan.worldsunwritten.modpack.ModpackRuntime;
import java.util.Properties;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(DedicatedServerProperties.class)
abstract class DedicatedServerPropertiesMixin {
    @ModifyArg(method = "fromFile", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/server/dedicated/DedicatedServerProperties;<init>(Ljava/util/Properties;)V"))
    private static Properties worldsunwritten$bundleDefaults(Properties properties) {
        return ModpackRuntime.dedicatedDefaults(properties);
    }
}
