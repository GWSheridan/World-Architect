package com.gwsheridan.worldsunwritten.client;

import com.gwsheridan.worldsunwritten.studio.StudioViewPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

final class ClientStudioViews {
    private ClientStudioViews() {}
    static void register(RegisterClientPayloadHandlersEvent event){event.register(StudioViewPayload.TYPE,(payload,context)->{
        var mc=Minecraft.getInstance();try{
            switch(payload.view().getStringOr("kind","")) {
                case "cell_progress" -> mc.gui.setScreen(new CellProgressScreen(payload.view()));
                case "inspection" -> mc.gui.setScreen(new WorldInspectorScreen(payload.view()));
                case "workbench" -> mc.gui.setScreen(new WorkbenchScenarioScreen(payload.view()));
                case "workbench_export" -> WorkbenchScenarioScreen.acknowledge(payload.view());
                case "site" -> SiteInspectorScreen.show(payload.view());
                case "assembly" -> mc.gui.setScreen(payload.view().getBooleanOr("theme_editor",false)?new AssemblyThemeScreen(payload.view()):payload.view().getBooleanOr("pool_editor",false)?new AssemblyPoolScreen(payload.view()):new AssemblyStudioScreen(payload.view()));
                case "administration" -> mc.gui.setScreen(new AdministrationPanelScreen(payload.view()));
                case "island_choice" -> mc.gui.setScreen(new IslandChoiceScreen(payload.view()));
                case "island_choice_done" -> {if(mc.gui.screen() instanceof IslandChoiceScreen)mc.gui.setScreen(null);}
                case "island_editor" -> {if(mc.getConnection()!=null)mc.gui.setScreen(new IslandStudioScreen(null,mc.getConnection().registryAccess(),com.gwsheridan.worldsunwritten.structure.IslandBuild.classic(),ignored->{}));}
                case "administration_confirmation" -> mc.gui.setScreen(new StudioConfirmScreen(answer -> {
                    if(mc.getConnection()!=null)mc.getConnection().sendCommand(answer
                            ? "wa_admin confirm "+payload.view().getStringOr("token","")+" "+payload.view().getStringOr("confirmation","")
                            : "wa_admin cancel "+payload.view().getStringOr("token",""));
                    mc.gui.setScreen(null);
                },Component.literal("Confirm administration change?"),Component.literal(payload.view().getStringOr("description","")+" Current permissions and ownership are checked again."),Component.literal("Confirm change"),Component.literal("Cancel")));
                default -> { }
            }
        }catch(RuntimeException e){if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("Cannot display this studio view. Reopen it from the server command."));}
    });}
}
