package com.gwsheridan.worldsunwritten.packtest;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod("wa_pack_test")
public final class NeoForgePackTest {
    public NeoForgePackTest() {
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> PackTestMod.register(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> PackTestMod.tick(event.getServer()));
    }
}
