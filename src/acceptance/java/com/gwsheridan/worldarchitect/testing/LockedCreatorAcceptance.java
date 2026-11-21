package com.gwsheridan.worldarchitect.testing;

import com.gwsheridan.worldarchitect.modpack.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.worldselection.*;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Actual clean-client controls and the common enforcement path, separate from gameplay code. */
@EventBusSubscriber(modid="wa_validation",value=Dist.CLIENT)
public final class LockedCreatorAcceptance {
    private static final String MODE=System.getProperty("wa.creator.locked","");
    private static final KeyEvent ENTER=new KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN,'\r',0);
    private static int ticks;private static boolean started;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{
        if(MODE.isEmpty())return;var mc=Minecraft.getInstance();
        if(started){if(++ticks==30)Screenshot.grab(mc,false);if(ticks>50)mc.stop();return;}
        if(!(mc.gui.screen() instanceof TitleScreen)||++ticks<30)return;started=true;ticks=0;
        try{
            var bundle=ModpackRuntime.locked().orElseThrow();var packMethod=PresetBundle.class.getDeclaredMethod("packId");packMethod.setAccessible(true);String pack=(String)packMethod.invoke(bundle);boolean partial=MODE.equals("partial");
            if(MODE.equals("inherited-partial")){inherited(mc,bundle,pack);return;}
            require(partial||MODE.equals("full"),"Unknown fixture mode");require(bundle.manifest().lock().getSerializedName().equals(MODE),"Wrong installed lock");
            CreateWorldScreen.openFresh(mc,mc::stop,(screen,layers,settings,rules,packs)->true);var parent=(CreateWorldScreen)mc.gui.screen();var ui=parent.getUiState();
            ui.setWorldType(ui.getNormalPresetList().stream().filter(t->t.preset().is(bundle.key())).findFirst().orElseThrow());
            var type=Class.forName("com.gwsheridan.worldarchitect.client.DimensionManagerScreen");var constructor=type.getDeclaredConstructor(CreateWorldScreen.class,WorldCreationContext.class);constructor.setAccessible(true);
            var manager=(Screen)constructor.newInstance(parent,ui.getSettings());mc.gui.setScreen(manager);
            for(int scale:new int[]{1,2}){
                mc.options.guiScale().set(scale);mc.resizeGui();select(manager,LevelStem.OVERWORLD);bounds();
                for(String label:List.of("Single Block","Layered","Void / Skyblock","World Generation Rules","Preset Studio"))require(!button(label).active,"Locked editor enabled: "+label);
                require(button("Features").active==partial,"Feature lock differs");press("Starters");for(String label:List.of("Single Block","Layered","Skyblock","Custom End"))require(!button(label).active,"Locked starter enabled");press("Back");
                if(partial){press("Features");bounds();for(var child:mc.gui.screen().children())if(child instanceof Button b&&List.of("Biomes","Ores","Decorations","Caves","Fluids","Structures").stream().anyMatch(n->b.getMessage().getString().startsWith(n)))require(b.active==b.getMessage().getString().startsWith("Ores"),"Partial feature control differs");
                    var ore=mc.gui.screen().children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b->b.getMessage().getString().startsWith("Ores")).findFirst().orElseThrow();ore.onPress(ENTER);press("Add rule");press("Done");press("Done");}
                var draft=type.getDeclaredField("draft");draft.setAccessible(true);var dimensions=new LinkedHashMap<>((Map<net.minecraft.resources.ResourceKey<LevelStem>,LevelStem>)draft.get(manager));var actual=new WorldDimensions(dimensions);
                ModpackRuntime.validate(Optional.of(bundle),bundle.manifest().options(),actual,actual,ui.getSettings().worldgenLoadContext(),List.of(pack));
                var source=dimensions.get(LevelStem.OVERWORLD);dimensions.put(LevelStem.OVERWORLD,new LevelStem(source.type(),dimensions.get(LevelStem.NETHER).generator()));boolean refused=false;
                try{var tampered=new WorldDimensions(dimensions);ModpackRuntime.validate(Optional.of(bundle),bundle.manifest().options(),tampered,tampered,ui.getSettings().worldgenLoadContext(),List.of(pack));}catch(IllegalStateException expected){refused=expected.getMessage().contains("Locked generation settings changed");}require(refused,"Common policy accepted locked terrain tampering");
            }
            System.out.println("WA_LOCKED_CREATOR_PASS mode="+MODE+" scales=1,2 controls,starters,permitted-edits,tamper-refusal");
        }catch(Throwable e){e.printStackTrace();System.out.println("WA_LOCKED_CREATOR_FAILED mode="+MODE);mc.stop();}
    }
    private static void inherited(Minecraft mc,PresetBundle bundle,String pack)throws Exception{
        require(bundle.manifest().inheritance().isPresent()&&bundle.manifest().lock()==BundleManifest.Lock.PARTIAL,"Missing inherited partial policy");CreateWorldScreen.openFresh(mc,mc::stop,(screen,layers,settings,rules,packs)->true);var parent=(CreateWorldScreen)mc.gui.screen();var ui=parent.getUiState();ui.setWorldType(ui.getNormalPresetList().stream().filter(t->t.preset().is(bundle.key())).findFirst().orElseThrow());var type=Class.forName("com.gwsheridan.worldarchitect.client.DimensionManagerScreen");var constructor=type.getDeclaredConstructor(CreateWorldScreen.class,WorldCreationContext.class);constructor.setAccessible(true);var manager=(Screen)constructor.newInstance(parent,ui.getSettings());mc.gui.setScreen(manager);
        for(int scale:new int[]{1,2}){mc.options.guiScale().set(scale);mc.resizeGui();select(manager,LevelStem.OVERWORLD);bounds();require(button("Void / Skyblock").active,"Inherited Overworld editor disabled");select(manager,LevelStem.NETHER);for(String label:List.of("Single Block","Layered","Void / Skyblock","Features","World Generation Rules"))require(!button(label).active,"Inherited Nether lock weakened: "+label);select(manager,LevelStem.END);require(!button("Single Block").active&&!button("World Generation Rules").active,"Inherited End lock weakened");
            var field=type.getDeclaredField("draft");field.setAccessible(true);var dimensions=new LinkedHashMap<>((Map<net.minecraft.resources.ResourceKey<LevelStem>,LevelStem>)field.get(manager));var actual=new WorldDimensions(dimensions);var registries=ui.getSettings().worldgenLoadContext();ModpackRuntime.validate(Optional.of(bundle),bundle.manifest().options(),actual,actual,registries,List.of(pack));var ow=dimensions.get(LevelStem.OVERWORLD);dimensions.put(LevelStem.OVERWORLD,new LevelStem(ow.type(),dimensions.get(LevelStem.NETHER).generator()));var allowed=new WorldDimensions(dimensions);ModpackRuntime.validate(Optional.of(bundle),bundle.manifest().options(),allowed,allowed,registries,List.of(pack));dimensions.put(LevelStem.END,new LevelStem(dimensions.get(LevelStem.END).type(),ow.generator()));boolean refused=false;try{var invalid=new WorldDimensions(dimensions);ModpackRuntime.validate(Optional.of(bundle),bundle.manifest().options(),invalid,invalid,registries,List.of(pack));}catch(IllegalStateException expected){refused=true;}require(refused,"Inherited child accepted an End override");}
        System.out.println("WA_LOCKED_CREATOR_PASS mode=inherited-partial scales=1,2 controls,allowed-overworld,locked-nether-end,tamper-refusal");
    }
    private static Button button(String label){return Minecraft.getInstance().gui.screen().children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b->b.getMessage().getString().equals(label)).findFirst().orElseThrow(()->new AssertionError("Missing "+label));}
    private static void press(String label){var b=button(label);require(b.active,"Disabled "+label);b.onPress(ENTER);}
    @SuppressWarnings({"rawtypes","unchecked"})private static void select(Screen manager,Object wanted)throws Exception{var f=manager.getClass().getDeclaredField("rows");f.setAccessible(true);var rows=(ObjectSelectionList)f.get(manager);for(Object row:rows.children()){var key=row.getClass().getDeclaredField("key");key.setAccessible(true);if(key.get(row).equals(wanted)){rows.setSelected((ObjectSelectionList.Entry)row);return;}}throw new AssertionError("Dimension missing");}
    private static void bounds(){var s=Minecraft.getInstance().gui.screen();int w=s.width,h=s.height;s.resize(320,240);for(var child:s.children())if(child instanceof AbstractWidget b&&b.visible)require(b.getX()>=0&&b.getY()>=0&&b.getRight()<=s.width&&b.getBottom()<=s.height,"Offscreen locked control");s.resize(w,h);}
    private static void require(boolean okay,String message){if(!okay)throw new AssertionError(message);}
}
