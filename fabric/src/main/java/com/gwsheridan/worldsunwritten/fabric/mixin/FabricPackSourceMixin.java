package com.gwsheridan.worldsunwritten.fabric.mixin;

import com.gwsheridan.worldsunwritten.modpack.ModpackRuntime;
import java.util.Arrays;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.repository.ServerPacksSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ServerPacksSource.class)
abstract class FabricPackSourceMixin {
    @ModifyArg(method = "createPackRepository(Ljava/nio/file/Path;Lnet/minecraft/world/level/validation/DirectoryValidator;)Lnet/minecraft/server/packs/repository/PackRepository;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/repository/PackRepository;<init>([Lnet/minecraft/server/packs/repository/RepositorySource;)V"))
    private static RepositorySource[] worldsunwritten$addBundle(RepositorySource[] sources) {
        return ModpackRuntime.bundlePackSource().map(bundle -> {
            var extended = Arrays.copyOf(sources, sources.length + 1);
            extended[sources.length] = bundle;
            return extended;
        }).orElse(sources);
    }
}
