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
    private static final KeyEvent ENTER = new KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN,'\r',0);
    private static boolean started;
    private static int ticks;
    private static CreateWorldScreen parent;
    private static Screen manager;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) throws Exception {
        if (!Boolean.getBoolean("wa.creator.acceptance")||!System.getProperty("wa.creator.locked","").isEmpty()) return;
        var mc = Minecraft.getInstance();
        if (!started) {
            if (!(mc.gui.screen() instanceof TitleScreen) || ++ticks<30) return;
            started=true; ticks=0;
            CreateWorldScreen.openFresh(mc,mc::stop,(screen,layers,settings,rules,packs)->true);
            parent=(CreateWorldScreen)mc.gui.screen(); var ui=parent.getUiState();
            ui.setWorldType(ui.getNormalPresetList().stream().filter(t->t.preset().is(WorldArchitect.DIMENSIONS_PRESET)).findFirst().orElseThrow());
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
            var ui=parent.getUiState();ui.setSeed("18");ui.updateDimensions((registries,old)->ui.getSettings().worldgenLoadContext()
                    .lookupOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getOrThrow(WorldArchitect.DIMENSIONS_PRESET).value().createWorldDimensions());
            manager=parent.getUiState().getPresetEditor().createEditScreen(parent,parent.getUiState().getSettings());mc.gui.setScreen(manager);bounds();press("Starters");bounds();
        });
        STEPS.add(()->{press(starter);bounds();
            if(starter.equals("Layered")){
                press("Layered");bounds();press("Terrain Shape");bounds();press("Regional terrain (0)");bounds();
                press("Add rectangle");bounds();form(List.of("forest","0","-512","-512","512","512","64"));press("Terrain & biome");bounds();form(List.of("120","16","plateaus","192","7","minecraft:forest"));
                press("Add circle");bounds();form(List.of("desert","10","-128","-128","128","128","32"));press("Terrain & biome");form(List.of("180","0","plateaus","192","0","minecraft:desert"));bounds();
                press("History / organize");bounds();press("Duplicate selected region");require(regionCount()==3,"Region duplication failed");press("History / organize");press("Undo");require(regionCount()==2,"Region undo failed");press("History / organize");press("Redo");require(regionCount()==3,"Region redo failed");press("History / organize");press("Undo");
                press("History / organize");press("Add polygon region");press("Cancel");require(regionCount()==3,"Polygon creation failed");bounds();press("Polygon / priority");choose(1);choose(0);choose(0);bounds();form(List.of("-160","-128"));press("History / organize");press("Undo");press("History / organize");press("Redo");
                require(((com.gwsheridan.worldarchitect.config.RegionalTerrain.Zone)((List<?>)value(mc.gui.screen(),"zones")).get(2)).vertices().getFirst().x()==-160,"Polygon vertex history was lost");
                press("History / organize");press("Undo");press("History / organize");press("Undo");require(regionCount()==2,"Polygon undo did not restore the legacy draft"); press("History / organize");press("Add composite region");bounds();form(List.of("woodland_ring","subtract","forest,desert","20","0"));require(regionCount()==3,"Composite editor did not create a region");require(((com.gwsheridan.worldarchitect.config.RegionalTerrain.Zone)((List<?>)value(mc.gui.screen(),"zones")).get(2)).mask()==com.gwsheridan.worldarchitect.config.RegionalTerrain.Mask.COMPOSITE,"Composite editor changed its mask");press("History / organize");press("Undo");require(regionCount()==2,"Composite undo did not restore the draft");
                // Simulate interruption without invoking the editor's explicit Cancel/discard path.
                var regionScreen=mc.gui.screen();mc.gui.setScreen((Screen)value(regionScreen,"parent"));press("Regional terrain (0)");require(mc.gui.screen().getTitle().getString().equals("Draft history and recovery"),"Interrupted regions were not offered for recovery");bounds();press("Restore recovery");require(regionCount()==2,"Region recovery changed the draft");
            }
        });
        STEPS.add(()->{if(starter.equals("Layered")){bounds();regionMapClicks();Screenshot.grab(mc,false);}});
        STEPS.add(()->{if(starter.equals("Layered")){press("Done");bounds();
                press("Regional terrain (2)");press("Add circle");press("Cancel");press("Cancel");press("Regional terrain (2)");press("Cancel");press("Done");press("Done");bounds();
            }
            press("Preview terrain");});
        STEPS.add(()->{bounds();Screenshot.grab(mc,false);});
        for(int i=0;i<3;i++) {
            STEPS.add(()->{var cycle=mc.gui.screen().children().stream().filter(CycleButton.class::isInstance).map(CycleButton.class::cast).findFirst().orElseThrow();cycle.onPress(ENTER);});
            STEPS.add(()->Screenshot.grab(mc,false));
        }
        STEPS.add(()->{press("Back");pressEither("World Generation Rules","Generation Rules");press("Spawn");bounds();press("History / organize");bounds();press("Duplicate selected rule");press("History / organize");press("Undo");press("History / organize");press("Redo");press("History / organize");press("Undo");
            if(starter.equals("Layered")){press("Material");press("Named terrain region");bounds();form(List.of("forest","winner","false"));}
            press("Inspect rules");bounds();});
        for(int overlay=0;overlay<5;overlay++) {
            STEPS.add(()->{bounds();Screenshot.grab(mc,false);var cycle=mc.gui.screen().children().stream().filter(CycleButton.class::isInstance).map(CycleButton.class::cast).findFirst().orElseThrow();cycle.onPress(ENTER);});
        }
        STEPS.add(()->{press("Coordinates");bounds();press("Apply");press("Explain");bounds();press("Back");press("Order & warnings");bounds();press("Back");press("Back");press("Cancel");press("Preset Studio");bounds();press("Metadata");bounds();});
        STEPS.add(()->{
            var fields=mc.gui.screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();
            String name=starter.toLowerCase(Locale.ROOT).replace(' ','-')+"-"+scale;
            fields.get(0).setValue(name);fields.get(1).setValue("wa_presets:"+name);fields.get(2).setValue(starter+" starter");fields.get(3).setValue("Validation fixture");
            press("Apply");press("History");bounds();press("Undo");press("History");press("Redo");press("Duplicate");press("History");press("Undo");press("Validate draft");press("Save version");press("Export ZIP");Screenshot.grab(mc,false);
            if(starter.equals("Layered")){
                press("Workbench");bounds();press("Run settings");bounds();var input=mc.gui.screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();input.get(1).setValue(Long.toString(Long.MIN_VALUE));press("Apply");press("History / recovery");bounds();press("Undo");require(workbenchSeed().equals("18"),"Workbench undo changed its original seed");press("History / recovery");press("Redo");require(workbenchSeed().equals(Long.toString(Long.MIN_VALUE)),"Workbench rounded a signed 64-bit seed");press("Export scenario");form(List.of("creator-workbench-"+scale));bounds();Screenshot.grab(mc,false);press("Cancel");
            }
            press("Browse saved presets");bounds();
            var search=mc.gui.screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();search.setValue(name);
            mc.gui.screen().keyPressed(new KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_TAB,'\t',0));press("Load selected");press("Continue");
        });
        STEPS.add(()->{press("Apply to world");press("Done");
            var gen=parent.getUiState().getSettings().selectedDimensions().get(starter.equals("Custom End")?LevelStem.END:LevelStem.OVERWORLD).orElseThrow().generator();
            require(starter.equals("Single Block")?gen instanceof SingleBlockGenerator:starter.equals("Layered")?gen instanceof LayeredGenerator:starter.equals("Skyblock")?gen instanceof VoidGenerator:gen instanceof ManagedGenerator managed&&managed.endProgression().isPresent(),"Starter was not applied");
            manager=parent.getUiState().getPresetEditor().createEditScreen(parent,parent.getUiState().getSettings());mc.gui.setScreen(manager);press("Starters");press(starter.equals("Skyblock")?"Single Block":"Skyblock");manager.onClose();press("Keep editing");manager.onClose();press("Discard changes");
            require(parent.getUiState().getSettings().selectedDimensions().get(starter.equals("Custom End")?LevelStem.END:LevelStem.OVERWORLD).orElseThrow().generator()==gen,"Cancelled draft reached world settings");
        });
    }
    private static void form(List<String> values){var fields=Minecraft.getInstance().gui.screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();require(fields.size()==values.size(),"Unexpected region form");for(int i=0;i<values.size();i++)fields.get(i).setValue(values.get(i));press("Apply");}
    private static Object value(Object target,String name){try{var field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static int regionCount(){return ((List<?>)value(Minecraft.getInstance().gui.screen(),"zones")).size();}
    @SuppressWarnings({"rawtypes","unchecked"}) private static void choose(int index){try{var screen=Minecraft.getInstance().gui.screen();var rows=screen.children().stream().filter(AbstractSelectionList.class::isInstance).map(AbstractSelectionList.class::cast).findFirst().orElseThrow();var entry=rows.children().get(index);var method=Arrays.stream(rows.getClass().getMethods()).filter(m->m.getName().equals("setSelected")&&!m.isBridge()&&m.getParameterCount()==1&&m.getParameterTypes()[0].isInstance(entry)).findFirst().orElseThrow();method.setAccessible(true);method.invoke(rows,entry);press("Select");}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static String workbenchSeed(){return ((com.google.gson.JsonObject)value(Minecraft.getInstance().gui.screen(),"document")).getAsJsonArray("runs").get(0).getAsJsonObject().get("seed").getAsString();}
    private static void regionMapClicks(){try{var screen=Minecraft.getInstance().gui.screen();var type=screen.getClass();var fields=new HashMap<String,java.lang.reflect.Field>();for(String name:List.of("mapX","mapY","mapSize","zones")){var field=type.getDeclaredField(name);field.setAccessible(true);fields.put(name,field);}int x=fields.get("mapX").getInt(screen),y=fields.get("mapY").getInt(screen),size=fields.get("mapSize").getInt(screen);var before=List.copyOf((List<?>)fields.get("zones").get(screen));screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(x+size*.75,y+size*.5,new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT,0)),false);require(!before.equals(fields.get("zones").get(screen)),"Map move changed no draft bounds");screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(x+size*.75,y+size*.5,new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0)),false);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static void pressEither(String a,String b){var screen=Minecraft.getInstance().gui.screen();press(screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).anyMatch(w->w.getMessage().getString().equals(a))?a:b);}
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
        validateBounds(screen);
        int width=screen.width,height=screen.height;
        screen.resize(320,240);validateBounds(screen);screen.resize(width,height);validateBounds(screen);
    }
    private static void validateBounds(Screen screen) {
        var widgets=screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).filter(w->w.visible).toList();
        for(var a:widgets) {require(a.getX()>=0&&a.getY()>=0&&a.getRight()<=screen.width&&a.getBottom()<=screen.height,"Off-screen control in "+screen.getTitle().getString());
            for(var b:widgets)if(a!=b)require(a.getRight()<=b.getX()||a.getX()>=b.getRight()||a.getBottom()<=b.getY()||a.getY()>=b.getBottom(),"Overlapping controls in "+screen.getTitle().getString()+": "+a.getMessage().getString()+" / "+b.getMessage().getString());}
    }
    private static void require(boolean test,String message){if(!test)throw new AssertionError(message);}
}
