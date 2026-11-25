package com.gwsheridan.worldsunwritten.packtest;

import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("wa_pack_test")
public final class ForgePackTest {
    public ForgePackTest() {
        RegisterCommandsEvent.BUS.addListener(event -> PackTestMod.register(event.getDispatcher()));
        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> PackTestMod.tick(event.server()));
    }
}
