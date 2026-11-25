package com.gwsheridan.worldsunwritten.fabric.mixin;

import com.gwsheridan.worldsunwritten.cells.CellWorlds;
import com.gwsheridan.worldsunwritten.environment.WorldEnvironment;
import com.gwsheridan.worldsunwritten.fabric.SpawnAdmission;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
abstract class FabricFinalizeSpawnMixin implements SpawnAdmission {
    @Unique private boolean worldsunwritten$denied;

    @Inject(method = "finalizeSpawn", at = @At("RETURN"))
    private void worldsunwritten$checkSpawn(ServerLevelAccessor access, DifficultyInstance difficulty,
            EntitySpawnReason reason, SpawnGroupData group, CallbackInfoReturnable<SpawnGroupData> callback) {
        Mob mob = (Mob) (Object) this;
        var level = access.getLevel();
        worldsunwritten$denied = CellWorlds.locked(level, mob.blockPosition())
                || WorldEnvironment.spawnDenied(level, mob.getType(), reason);
    }

    @Override
    public boolean worldsunwritten$spawnDenied() {
        return worldsunwritten$denied;
    }
}
