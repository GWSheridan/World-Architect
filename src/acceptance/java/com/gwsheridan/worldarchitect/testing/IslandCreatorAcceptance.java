package com.gwsheridan.worldarchitect.testing;

import com.gwsheridan.worldarchitect.WorldArchitect;
import com.gwsheridan.worldarchitect.modpack.PresetStudio;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Exercises the shipped widgets, renderer and immutable export path in a real client. */
@EventBusSubscriber(modid="wa_validation",value=Dist.CLIENT)
public final class IslandCreatorAcceptance {
    private static final Deque<Runnable> STEPS=new ArrayDeque<>();
    private static final KeyEvent ENTER=new KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN,'\r',0);
    private static int ticks;private static boolean started;private static CreateWorldScreen parent;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("wa.creator.studio"))return;
        var mc=Minecraft.getInstance();
        if(!started){
            if(!(mc.gui.screen() instanceof TitleScreen)||++ticks<30)return;
            started=true;ticks=0;CreateWorldScreen.openFresh(mc,mc::stop,(screen,layers,settings,rules,packs)->true);
            parent=(CreateWorldScreen)mc.gui.screen();var ui=parent.getUiState();ui.setWorldType(ui.getNormalPresetList().stream().filter(t->t.preset().is(WorldArchitect.DIMENSIONS_PRESET)).findFirst().orElseThrow());
            for(int scale:new int[]{1,2,3})queue(scale);
            STEPS.add(()->{System.out.println("WA_ISLAND_CREATOR_PASS builder,island-studio,locks,markers,history,exports,scales1and2and3");mc.stop();});
        }
        if(++ticks%35==0&&!STEPS.isEmpty())try{STEPS.removeFirst().run();}catch(Throwable e){e.printStackTrace();System.out.println("WA_ISLAND_CREATOR_FAILED");mc.stop();}
    }
    private static void queue(int scale){
        var mc=Minecraft.getInstance();
        STEPS.add(()->{mc.options.guiScale().set(scale);mc.resizeGui();var ui=parent.getUiState();ui.updateDimensions((registries,old)->ui.getSettings().worldgenLoadContext().lookupOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getOrThrow(WorldArchitect.DIMENSIONS_PRESET).value().createWorldDimensions());mc.gui.setScreen(ui.getPresetEditor().createEditScreen(parent,ui.getSettings()));press("Builder");bounds();});
        STEPS.add(()->Screenshot.grab(mc,false));
        STEPS.add(()->{press("Choose starter");choose("Personal islands");press("Project");press("Project details");form(List.of("island-studio-"+scale,"worldarchitect_examples:island-studio-"+scale,"Island Studio validation","Validation fixture","1.8.0","A clean exported Skyblock project"));press("Islands");press("Edit island catalog");choose("classic · Classic island");choose("Details and weight");var fields=mc.gui.screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();fields.get(3).setValue("missing:icon");press("Apply");require(mc.gui.screen().getTitle().getString().equals("Island type details"),"A rejected icon discarded the form");require(fields.get(3).getValue().equals("missing:icon"),"Rejected form lost entered values");fields.get(3).setValue("minecraft:grass_block");press("Apply");press("Edit island catalog");choose("classic · Classic island");choose("Open Island Studio");bounds();});
        STEPS.add(()->Screenshot.grab(mc,false));
        STEPS.add(()->{press("Slice view");bounds();});
        STEPS.add(()->Screenshot.grab(mc,false));
        STEPS.add(()->{press("Spawn markers");choose("Add marker at selected slice cell");form(List.of("arrival"));press("Spawn markers");choosePrefix("arrival ·");choose("Use as spawn");press("Transform / palette");choose("Rotate clockwise");press("Save / history");choose("History / recovery");press("Undo");press("Save / history");choose("History / recovery");press("Redo");press("Use island");require(((PresetStudio.Draft)value(mc.gui.screen(),"draft")).preset().toString().contains("arrival"),"Island marker was not retained in project");press("Locks");press("Generation and runtime locks");press("Lock: unlocked");press("Runtime permissions…");choose("protection: Locked");press("Done");var manifest=((PresetStudio.Draft)value(mc.gui.screen(),"draft")).manifest();require(manifest.pack().orElseThrow().editable().contains("protection"),"Lock editor discarded runtime scopes");press("Server policies");press("Island protection");bounds();});
        STEPS.add(()->Screenshot.grab(mc,false));
        STEPS.add(()->{press("Cancel");press("Initial spawn");choose("Team island");require(value(mc.gui.screen(),"error").toString().contains("team"),"A rejected team policy had no visible explanation");choose("World default");press("Review / export");press("Validate and inspect dependencies");bounds();});
        STEPS.add(()->Screenshot.grab(mc,false));
        STEPS.add(()->{press("Back");press("Dimensions");press("Select dimension: minecraft:overworld");choose("minecraft:the_end");press("Terrain / generator");choose("Skyblock");bounds();press("Review / export");press("Save version");require(value(mc.gui.screen(),"status").toString().startsWith("Saved "),"Save failed: "+value(mc.gui.screen(),"status"));press("Export client / server installation");require(value(mc.gui.screen(),"status").toString().startsWith("Exported "),"Export failed: "+value(mc.gui.screen(),"status"));press("Use project");press("Done");});
        STEPS.add(IslandCreatorAcceptance::checkStudioPolicy);
    }
    private static void checkStudioPolicy(){
        var mc=Minecraft.getInstance();var ui=parent.getUiState();mc.gui.setScreen(ui.getPresetEditor().createEditScreen(parent,ui.getSettings()));press("Preset Studio");press("Browse saved presets");var search=mc.gui.screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();search.setValue("island-studio-"+mc.options.guiScale().get()+" /");mc.gui.screen().keyPressed(new KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_TAB,'\t',0));press("Load selected");
        var before=((PresetStudio.Draft)value(mc.gui.screen(),"draft")).manifest();require(before.pack().isPresent(),"Loaded project omitted its runtime policy");press("Metadata");press("Apply");require(((PresetStudio.Draft)value(mc.gui.screen(),"draft")).manifest().pack().equals(before.pack()),"Editing metadata discarded runtime defaults or locks");press("World defaults");press("Apply");require(((PresetStudio.Draft)value(mc.gui.screen(),"draft")).manifest().pack().equals(before.pack()),"Editing world defaults discarded runtime policy");press("Cancel");press("Cancel");if(mc.gui.screen().getTitle().getString().equals("Discard world draft?"))press("Discard changes");
    }
    private static Object value(Object target,String name){try{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static void form(List<String> values){var fields=Minecraft.getInstance().gui.screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();require(fields.size()==values.size(),"Unexpected field count");for(int i=0;i<values.size();i++)fields.get(i).setValue(values.get(i));press("Apply");}
    private static void press(String label){var s=Minecraft.getInstance().gui.screen();var button=s.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b->b.getMessage().getString().equals(label)||b.getMessage().getString().equals("› "+label)).findFirst().orElseThrow(()->new AssertionError("Missing "+label+" in "+s.getTitle().getString()));require(button.active,"Disabled "+label);button.onPress(ENTER);}
    private static void choosePrefix(String prefix){var screen=Minecraft.getInstance().gui.screen();var choices=(List<?>)value(screen,"choices");choose(choices.stream().map(Object::toString).filter(s->s.startsWith(prefix)).findFirst().orElseThrow());}
    @SuppressWarnings({"rawtypes","unchecked"})private static void choose(String label){try{var screen=Minecraft.getInstance().gui.screen();var rows=screen.children().stream().filter(AbstractSelectionList.class::isInstance).map(AbstractSelectionList.class::cast).findFirst().orElseThrow();var entry=((List<?>)rows.children()).stream().filter(e->value(e,"value").equals(label)).findFirst().orElseThrow(()->new AssertionError("Missing choice "+label));var method=Arrays.stream(rows.getClass().getMethods()).filter(m->m.getName().equals("setSelected")&&!m.isBridge()&&m.getParameterCount()==1&&m.getParameterTypes()[0].isInstance(entry)).findFirst().orElseThrow();method.setAccessible(true);method.invoke(rows,entry);press("Select");}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static void bounds(){var screen=Minecraft.getInstance().gui.screen();int w=screen.width,h=screen.height;check(screen);screen.resize(320,240);check(screen);screen.resize(w,h);check(screen);}
    private static void check(Screen s){var widgets=s.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).filter(w->w.visible).toList();for(var a:widgets){require(a.getX()>=0&&a.getY()>=0&&a.getRight()<=s.width&&a.getBottom()<=s.height,"Offscreen widget in "+s.getTitle().getString());for(var b:widgets)if(a!=b)require(a.getRight()<=b.getX()||a.getX()>=b.getRight()||a.getBottom()<=b.getY()||a.getY()>=b.getBottom(),"Overlapping widgets in "+s.getTitle().getString());}}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
