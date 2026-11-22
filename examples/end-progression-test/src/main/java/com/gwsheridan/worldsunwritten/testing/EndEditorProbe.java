package com.gwsheridan.worldsunwritten.testing;

import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.worldselection.*;
import net.minecraft.world.level.dimension.LevelStem;
import com.gwsheridan.worldsunwritten.worldgen.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid="wa_dragon_probe",value=Dist.CLIENT)
public final class EndEditorProbe {
    private static boolean started;private static int ticks;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception {
        if(!Boolean.getBoolean("wa.end.editor"))return;
        var mc=Minecraft.getInstance();
        if(started){if(++ticks==90)Screenshot.grab(mc,false);if(ticks>110){System.out.println("END_EDITOR_PASS scales=1,2 defaults,toggle,retained-policy,preview,bounds");mc.stop();}return;}
        if(!(mc.gui.screen() instanceof TitleScreen)||++ticks<25)return;started=true;
        CreateWorldScreen.openFresh(mc,mc::stop,(screen,layers,settings,rules,packs)->true);
        var parent=(CreateWorldScreen)mc.gui.screen();var ui=parent.getUiState();
        var type=Class.forName("com.gwsheridan.worldsunwritten.client.DimensionManagerScreen");
        var constructor=type.getDeclaredConstructor(CreateWorldScreen.class,WorldCreationContext.class);constructor.setAccessible(true);
        var manager=(Screen)constructor.newInstance(parent,ui.getSettings());mc.gui.setScreen(manager);
        var rowsField=type.getDeclaredField("rows");rowsField.setAccessible(true);var rows=(ObjectSelectionList)rowsField.get(manager);
        for(Object row:rows.children()) {var key=row.getClass().getDeclaredField("key");key.setAccessible(true);if(key.get(row).equals(LevelStem.END)){rows.setSelected((ObjectSelectionList.Entry)row);break;}}
        press(mc,"Single Block");press(mc,"Done");
        for(int scale:new int[]{1,2}) {
            mc.options.guiScale().set(scale);mc.resizeGui();bounds(mc.gui.screen());
            press(mc,"Dragon fight: On");require(profile(manager,type).endProgression().isEmpty(),"Toggle did not disable");
            press(mc,"Single Block");press(mc,"Done");require(profile(manager,type).endProgression().isEmpty(),"Edit reset explicit policy");
            press(mc,"Dragon fight: Off");require(profile(manager,type).endProgression().isPresent(),"Toggle did not enable");
            press(mc,"Features");press(mc,"Done");require(profile(manager,type).endProgression().isPresent(),"Feature edit lost policy");
            var draft=type.getDeclaredField("draft");draft.setAccessible(true);var stem=((Map<net.minecraft.resources.ResourceKey<LevelStem>,LevelStem>)draft.get(manager)).get(LevelStem.END);
            var sample=GenerationPreview.forStem(stem,18).orElseThrow().sample(0,0);require(sample.occupied()&&sample.surfaceY()>40,"Missing native preview");
        }
        bounds(mc.gui.screen());ticks=0;
    }
    private static com.gwsheridan.worldsunwritten.config.DimensionProfile profile(Screen manager,Class<?> type)throws Exception {
        var draft=type.getDeclaredField("draft");draft.setAccessible(true);
        var stem=((Map<net.minecraft.resources.ResourceKey<LevelStem>,LevelStem>)draft.get(manager)).get(LevelStem.END);
        return ((ManagedGenerator)stem.generator()).dimensionProfile().orElseThrow();
    }
    private static void press(Minecraft mc,String label){var button=mc.gui.screen().children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b->b.getMessage().getString().equals(label)).findFirst().orElseThrow(()->new AssertionError("Missing "+label));require(button.active,"Disabled "+label);button.onPress(null);}
    private static void bounds(Screen screen){var widgets=screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).filter(w->w.visible).toList();for(var a:widgets){require(a.getX()>=0&&a.getY()>=0&&a.getRight()<=screen.width&&a.getBottom()<=screen.height,"Widget outside screen");for(var b:widgets)if(a!=b)require(a.getRight()<=b.getX()||a.getX()>=b.getRight()||a.getBottom()<=b.getY()||a.getY()>=b.getBottom(),"Overlapping widgets");}}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
