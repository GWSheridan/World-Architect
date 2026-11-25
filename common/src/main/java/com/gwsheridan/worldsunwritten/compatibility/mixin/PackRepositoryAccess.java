package com.gwsheridan.worldsunwritten.compatibility.mixin;

import java.util.Collection;
import java.util.List;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PackRepository.class)
public interface PackRepositoryAccess {
    @Invoker("rebuildSelected")
    List<Pack> worldsunwritten$resolveSelection(Collection<String> ids);
}
