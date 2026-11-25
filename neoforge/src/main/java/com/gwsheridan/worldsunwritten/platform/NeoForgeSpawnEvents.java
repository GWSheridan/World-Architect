package com.gwsheridan.worldsunwritten.platform;

import com.gwsheridan.worldsunwritten.environment.WorldEnvironment;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

final class NeoForgeSpawnEvents {
    private NeoForgeSpawnEvents() {}

    static void register(IEventBus bus) {
        bus.addListener((MobSpawnEvent.SpawnPlacementCheck event) -> {
            if (WorldEnvironment.spawnDenied(event.getLevel().getLevel(), event.getEntityType(), event.getSpawnType())) {
                event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
            }
        });
        bus.addListener((MobSpawnEvent.PositionCheck event) -> {
            if (WorldEnvironment.spawnDenied(event.getLevel().getLevel(), event.getEntity().getType(), event.getSpawnType())) {
                event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
            }
        });
        bus.addListener((FinalizeSpawnEvent event) -> {
            if (WorldEnvironment.spawnDenied(event.getLevel().getLevel(), event.getEntity().getType(), event.getSpawnType())) {
                event.setSpawnCancelled(true);
            }
        });
    }
}
