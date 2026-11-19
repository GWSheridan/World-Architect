package com.gwsheridan.worldarchitect.testing;

import com.gwsheridan.worldarchitect.WorldArchitect;
import com.gwsheridan.worldarchitect.worldgen.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.level.dimension.LevelStem;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid="wa_validation", value=Dist.CLIENT)
public final class CreatorAcceptance {
    private static final Deque<Runnable> STEPS = new ArrayDeque<>();
    private static final KeyEvent ENTER = new KeyEvent(257,0,0);
    private static boolean started;
    private static int ticks;
    private static CreateWorldScreen parent;
    private static Screen manager;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) throws Exception {
        if (!Boolean.getBoolean("wa.creator.acceptance")) return;
        var mc = Minecraft.getInstance();
        if (!started) {
            if (!(mc.gui.screen() instanceof TitleScreen) || ++ticks<30) return;
            started=true; ticks=0;
            CreateWorldScreen.openFresh(mc,mc::stop,(screen,layers,settings,rules,packs)->true);
            parent=(CreateWorldScreen)mc.gui.screen(); var ui=parent.getUiState();
            ui.setWorldType(ui.getNormalPresetList().stream().filter(t->t.preset().is(WorldArchitect.PRESET)).findFirst().orElseThrow());
            for (int scale : new int[]{1,2}) for (String starter : List.of("Single Block","Layered","Skyblock","Custom End")) queue(scale,starter);
            STEPS.add(()->{System.out.println("WA_CREATOR_WORKFLOW_PASS starters,exports,library,drafts,preview,scales1and2");mc.stop();});
        }
        if (++ticks%30==0 && !STEPS.isEmpty()) {
            try { STEPS.removeFirst().run(); }
            catch(Throwable e) { e.printStackTrace(); System.out.println("WA_CREATOR_WORKFLOW_FAILED"); mc.stop(); }
        }
    }
    private static void queue(int scale,String starter) {
        var mc=Minecraft.getInstance();
        STEPS.add(()->{
            mc.options.guiScale().set(scale);mc.resizeGui();
            manager=parent.getUiState().getPresetEditor().createEditScreen(parent,parent.getUiState().getSettings());mc.gui.setScreen(manager);bounds();press("Starters");bounds();
        });
        STEPS.add(()->{press(starter);bounds();press("Preview terrain");});
        STEPS.add(()->{bounds();Screenshot.grab(mc,false);});
        for(int i=0;i<3;i++) {
            STEPS.add(()->{var cycle=mc.gui.screen().children().stream().filter(CycleButton.class::isInstance).map(CycleButton.class::cast).findFirst().orElseThrow();cycle.onPress(ENTER);});
            STEPS.add(()->Screenshot.grab(mc,false));
        }
        STEPS.add(()->{press("Back");press("Preset Studio");bounds();press("Metadata");});
        STEPS.add(()->{
            var fields=mc.gui.screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();
            String name=starter.toLowerCase(Locale.ROOT).replace(' ','-')+"-"+scale;
            fields.get(0).setValue(name);fields.get(1).setValue("wa_presets:"+name);fields.get(2).setValue(starter+" starter");fields.get(3).setValue("Validation fixture");
            press("Apply");press("Validate draft");press("Save version");press("Export ZIP");Screenshot.grab(mc,false);
            press("Browse saved presets");bounds();
            var search=mc.gui.screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();search.setValue(name);
            var list=mc.gui.screen().children().stream().filter(ObjectSelectionList.class::isInstance).map(ObjectSelectionList.class::cast).findFirst().orElseThrow();
            mc.gui.screen().setFocused(list);mc.gui.screen().keyPressed(new KeyEvent(264,0,0));press("Load selected");press("Continue");
        });
        STEPS.add(()->{press("Apply to world");press("Done");
            var gen=parent.getUiState().getSettings().selectedDimensions().get(starter.equals("Custom End")?LevelStem.END:LevelStem.OVERWORLD).orElseThrow().generator();
            require(starter.equals("Single Block")?gen instanceof SingleBlockGenerator:starter.equals("Layered")?gen instanceof LayeredGenerator:starter.equals("Skyblock")?gen instanceof VoidGenerator:gen instanceof ManagedGenerator managed&&managed.endProgression().isPresent(),"Starter was not applied");
            manager=parent.getUiState().getPresetEditor().createEditScreen(parent,parent.getUiState().getSettings());mc.gui.setScreen(manager);press("Starters");press(starter.equals("Skyblock")?"Single Block":"Skyblock");manager.onClose();press("Keep editing");manager.onClose();press("Discard changes");
            require(parent.getUiState().getSettings().selectedDimensions().get(starter.equals("Custom End")?LevelStem.END:LevelStem.OVERWORLD).orElseThrow().generator()==gen,"Cancelled draft reached world settings");
        });
    }
    private static void press(String label) {
        var screen=Minecraft.getInstance().gui.screen();
        var button=screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b->b.getMessage().getString().equals(label)).findFirst().orElseThrow(()->new AssertionError("Missing "+label+" in "+screen.getTitle().getString()));
        if(!button.active) {
            try {var field=screen.getClass().getDeclaredField("status");field.setAccessible(true);throw new AssertionError("Disabled "+label+": "+field.get(screen));}
            catch(ReflectiveOperationException e){throw new AssertionError("Disabled "+label);}
        }
        button.onPress(ENTER);
    }
    private static void bounds() {
        var screen=Minecraft.getInstance().gui.screen();
        var widgets=screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).filter(w->w.visible).toList();
        for(var a:widgets) {require(a.getX()>=0&&a.getY()>=0&&a.getRight()<=screen.width&&a.getBottom()<=screen.height,"Off-screen control in "+screen.getTitle().getString());
            for(var b:widgets)if(a!=b)require(a.getRight()<=b.getX()||a.getX()>=b.getRight()||a.getBottom()<=b.getY()||a.getY()>=b.getBottom(),"Overlapping controls in "+screen.getTitle().getString());}
    }
    private static void require(boolean test,String message){if(!test)throw new AssertionError(message);}
}
