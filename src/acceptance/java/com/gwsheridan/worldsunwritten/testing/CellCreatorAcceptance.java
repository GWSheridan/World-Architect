package com.gwsheridan.worldsunwritten.testing;

import com.gwsheridan.worldsunwritten.WorldsUnwritten;
import com.gwsheridan.worldsunwritten.cells.CellRecipes;
import com.gwsheridan.worldsunwritten.modpack.PresetStudio;
import com.gwsheridan.worldsunwritten.worldgen.CellGenerator;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.level.dimension.LevelStem;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Real widgets and installation exports, separate from the gameplay artifact. */
@EventBusSubscriber(modid="wa_validation",value=Dist.CLIENT)
public final class CellCreatorAcceptance {
    private static final Deque<Runnable> STEPS=new ArrayDeque<>();
    private static final KeyEvent ENTER=new KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN,'\r',0);
    private static int ticks;private static boolean started;private static CreateWorldScreen parent;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("wa.creator.cells"))return;var mc=Minecraft.getInstance();
        if(!started){if(!(mc.gui.screen() instanceof TitleScreen)||++ticks<30)return;started=true;ticks=0;CreateWorldScreen.openFresh(mc,mc::stop,(screen,layers,settings,rules,packs)->true);parent=(CreateWorldScreen)mc.gui.screen();var ui=parent.getUiState();ui.setWorldType(ui.getNormalPresetList().stream().filter(t->t.preset().is(WorldsUnwritten.DIMENSIONS_PRESET)).findFirst().orElseThrow());for(var recipe:CellRecipes.NAMES)queue(recipe,1);queue("Expanding One Chunk",2);queue("Hex Skyblock",3);STEPS.add(()->{System.out.println("WA_CELL_CREATOR_PASS recipes,geometry,grass-layers,islands,objectives,locks,exports,scales1and2and3");mc.stop();});}
        if(++ticks%25==0&&!STEPS.isEmpty())try{STEPS.removeFirst().run();}catch(Throwable error){error.printStackTrace();System.out.println("WA_CELL_CREATOR_FAILED");mc.stop();}
    }
    private static void queue(String recipe,int scale){var mc=Minecraft.getInstance();String name="cell-"+CellRecipes.slug(recipe).replace('_','-')+"-"+scale;
        STEPS.add(()->{mc.options.guiScale().set(scale);mc.resizeGui();var ui=parent.getUiState();ui.updateDimensions((r,old)->ui.getSettings().worldgenLoadContext().lookupOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getOrThrow(WorldsUnwritten.DIMENSIONS_PRESET).value().createWorldDimensions());mc.gui.setScreen(ui.getPresetEditor().createEditScreen(parent,ui.getSettings()));press("Builder");press("Choose starter");choose(recipe);press("Biomes, features and cells");bounds();Screenshot.grab(mc,false);});
        STEPS.add(()->{press("Geometry and borders");bounds();press("Apply");require(mc.gui.screen().getTitle().getString().equals("Cell World Studio"),"Geometry edit failed");press("Cell types and islands");choose("start");choose("Details and biome");bounds();press("Apply");});
        STEPS.add(()->{press("Cell types and islands");choose("start");choose("Terrain and layers");bounds();press("Done");require(mc.gui.screen().getTitle().getString().equals("Cell World Studio"),"Natural grass layers were rejected");press("Cell types and islands");choose("start");choose("Edit embedded island");bounds();});
        STEPS.add(()->Screenshot.grab(mc,false));
        STEPS.add(()->{press("Cancel");press("Progression and ownership");bounds();press("Apply");var generator=(CellGenerator)value(mc.gui.screen(),"generator");if(generator.cells().progressive()){press("Objectives and prerequisites");choose("Dependency report");bounds();press("Back");}press("Fixed cell assignments");bounds();press("Apply");press("Preview saved layout");bounds();});
        STEPS.add(()->Screenshot.grab(mc,false));
        STEPS.add(()->{press("Back");press("Apply to project");press("Project");press("Project details");form(List.of(name,"wa_presets:"+name,recipe,"Validation fixture","2.0.0","Cell world installation"));press("Locks");press("Generation and runtime locks");bounds();if(recipe.equals("Hex Skyblock")){press("Lock: unlocked");press("Lock: partial");press("Structure Library: Yes");}else if(recipe.equals("Expanding One Chunk")){press("Lock: unlocked");press("Edit dimension: No");}press("Done");press("Review / export");press("Validate and inspect dependencies");bounds();press("Back");press("Save version");require(value(mc.gui.screen(),"status").toString().startsWith("Saved "),"Saving preset failed");press("Export client / server installation");require(value(mc.gui.screen(),"status").toString().startsWith("Exported "),"Installation export failed: "+value(mc.gui.screen(),"status"));var draft=(PresetStudio.Draft)value(mc.gui.screen(),"draft");System.out.println("WA19_GUI_EXPORT "+name+" "+draft.manifest().lock().getSerializedName());press("Use project");press("Done");require(parent.getUiState().getSettings().selectedDimensions().get(LevelStem.OVERWORLD).orElseThrow().generator() instanceof CellGenerator,"Cell project was not applied");});
    }
    private static Object value(Object object,String name){try{var field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}}
    private static void press(String label){var screen=Minecraft.getInstance().gui.screen();var button=screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b->b.getMessage().getString().equals(label)||b.getMessage().getString().equals("› "+label)).findFirst().orElseThrow(()->new AssertionError("Missing "+label+" in "+screen.getTitle().getString()));require(button.active,"Disabled "+label);button.onPress(ENTER);}
    @SuppressWarnings({"unchecked","rawtypes"}) private static void choose(String label){try{var screen=Minecraft.getInstance().gui.screen();var rows=screen.children().stream().filter(AbstractSelectionList.class::isInstance).map(AbstractSelectionList.class::cast).findFirst().orElseThrow();var entry=((List<?>)rows.children()).stream().filter(row->value(row,"value").equals(label)).findFirst().orElseThrow(()->new AssertionError("Missing choice "+label));var method=Arrays.stream(rows.getClass().getMethods()).filter(m->m.getName().equals("setSelected")&&!m.isBridge()&&m.getParameterCount()==1&&m.getParameterTypes()[0].isInstance(entry)).findFirst().orElseThrow();method.setAccessible(true);method.invoke(rows,entry);press("Select");}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}}
    private static void form(List<String> values){var fields=Minecraft.getInstance().gui.screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();require(fields.size()==values.size(),"Unexpected form fields");for(int i=0;i<values.size();i++)fields.get(i).setValue(values.get(i));press("Apply");}
    private static void bounds(){var screen=Minecraft.getInstance().gui.screen();int w=screen.width,h=screen.height;check(screen);screen.resize(320,240);check(screen);screen.resize(w,h);}
    private static void check(Screen screen){var widgets=screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).filter(w->w.visible).toList();for(var a:widgets){require(a.getX()>=0&&a.getY()>=0&&a.getRight()<=screen.width&&a.getBottom()<=screen.height,"Offscreen widget in "+screen.getTitle().getString());for(var b:widgets)if(a!=b)require(a.getRight()<=b.getX()||a.getX()>=b.getRight()||a.getBottom()<=b.getY()||a.getY()>=b.getBottom(),"Overlapping widgets in "+screen.getTitle().getString());}}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
