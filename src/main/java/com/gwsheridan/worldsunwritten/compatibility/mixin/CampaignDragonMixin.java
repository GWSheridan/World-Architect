package com.gwsheridan.worldsunwritten.compatibility.mixin;

import com.gwsheridan.worldsunwritten.cells.CellWorlds;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.dimension.end.EnderDragonFight;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(EnderDragonFight.class)
abstract class CampaignDragonMixin {
    @Shadow @Final private ServerLevel level;
    @Shadow private UUID dragonUUID;
    @Shadow private boolean dragonKilled;

    @Inject(method = "setDragonKilled", at = @At("TAIL"))
    private void worldsunwritten$verifiedDragonGoal(EnderDragon dragon, CallbackInfo callback) {
        if (dragonKilled && dragon.getUUID().equals(dragonUUID)) CellWorlds.dragonDefeated(level);
    }
}
