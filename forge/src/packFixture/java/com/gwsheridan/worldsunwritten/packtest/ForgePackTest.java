package com.gwsheridan.worldsunwritten.packtest;

import com.gwsheridan.worldsunwritten.api.DimensionAdapterRegistry;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegisterEvent;

@Mod("wa_pack_test")
public final class ForgePackTest {
    public ForgePackTest(FMLJavaModLoadingContext context) {
        RegisterEvent.getBus(context.getModBusGroup()).addListener(event ->
                event.register(DimensionAdapterRegistry.KEY, PackTestAdapter.ID, PackTestAdapter::new));
        RegisterCommandsEvent.BUS.addListener(event -> {
            PackTestAdapter.verifyRegistration();
            PackTestMod.register(event.getDispatcher());
        });
        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> PackTestMod.tick(event.server()));
    }
}
