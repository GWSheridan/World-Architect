package com.gwsheridan.worldarchitect.compatibility.mixin;

import com.gwsheridan.worldarchitect.skyblock.IslandSpawns;
import com.gwsheridan.worldarchitect.worldgen.VoidGenerator;
import com.gwsheridan.worldarchitect.spawn.SpawnPolicies;
import com.gwsheridan.worldarchitect.spawn.WorldAdministration;
import com.gwsheridan.worldarchitect.environment.WorldEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({EndPortalBlock.class, NetherPortalBlock.class})
abstract class VoidPortalMixin {
    // Destination handling precedes vanilla's automatic Nether frame and End platform placement.
    @Inject(method = "getPortalDestination", at = @At("HEAD"), cancellable = true)
    private void worldarchitect$voidEntry(ServerLevel current, Entity entity, BlockPos portal,
            CallbackInfoReturnable<TeleportTransition> callback) {
        boolean endPortal = (Object) this instanceof EndPortalBlock;
        var destination = endPortal ? (current.dimension() == Level.END ? current.getRespawnData().dimension() : Level.END)
                : (current.dimension() == Level.NETHER ? Level.OVERWORLD : Level.NETHER);
        ServerLevel level = current.getServer().getLevel(destination);
        if (level != null && !WorldEnvironment.canEnterPortal(level, entity)) {
            callback.setReturnValue(null);
            return;
        }
        if (level != null && endPortal && current.dimension() != Level.END
                && level.getChunkSource().getGenerator() instanceof com.gwsheridan.worldarchitect.worldgen.ManagedGenerator managed
                && managed.endProgression().isPresent()) return;
        if (level != null && entity instanceof ServerPlayer
                && SpawnPolicies.portalPolicy(level).isPresent()) {
            callback.setReturnValue(SpawnPolicies.portal(level, entity).orElse(null));
            return;
        }
        if(level!=null&&level.getChunkSource().getGenerator() instanceof com.gwsheridan.worldarchitect.worldgen.CellGenerator cells&&entity instanceof ServerPlayer player){
            if(endPortal&&current.dimension()==Level.END&&player.getRespawnConfig()!=null){var respawn=player.findRespawnPositionAndUseSpawnBlock(false,TeleportTransition.DO_NOTHING);if(!respawn.missingRespawnBlock()){callback.setReturnValue(respawn);return;}}
            try{
                var state=com.gwsheridan.worldarchitect.cells.CellWorlds.state(level);int slot=state==null?0:com.gwsheridan.worldarchitect.cells.CellWorlds.arrival(state,player);
                var marker=cells.spawn(new com.gwsheridan.worldarchitect.worldgen.CellGenerator.Key(slot,com.gwsheridan.worldarchitect.api.CellLayout.Cell.ORIGIN),level);
                callback.setReturnValue(com.gwsheridan.worldarchitect.spawn.SafeSpawns.nearby(level,marker).map(pos->new com.gwsheridan.worldarchitect.spawn.SafeSpawns.Target(level,pos,0).transition(TeleportTransition.DO_NOTHING)).orElse(null));return;
            }catch(java.io.IOException error){throw new java.io.UncheckedIOException(error);}
        }
        if (level != null && level.getChunkSource().getGenerator() instanceof VoidGenerator generator) {
            if (endPortal && current.dimension() == Level.END && generator.layout().isPresent()
                    && entity instanceof ServerPlayer player && player.getRespawnConfig() != null) {
                TeleportTransition respawn = player.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING);
                if (!respawn.missingRespawnBlock()) {
                    callback.setReturnValue(respawn);
                    return;
                }
            }
            callback.setReturnValue(IslandSpawns.portalEntry(level, generator, entity));
        }
    }
}
