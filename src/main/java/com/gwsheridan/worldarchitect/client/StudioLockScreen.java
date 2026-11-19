package com.gwsheridan.worldarchitect.client;

import com.google.gson.JsonObject;
import com.gwsheridan.worldarchitect.modpack.BundleManifest;
import com.gwsheridan.worldarchitect.modpack.BundleManifest.Feature;
import com.gwsheridan.worldarchitect.modpack.BundleManifest.Lock;
import com.gwsheridan.worldarchitect.modpack.BundleManifest.Option;
import com.mojang.serialization.JsonOps;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

final class StudioLockScreen extends Screen {
    private final Screen parent;private final BundleManifest original;private final JsonObject preset;private final Consumer<BundleManifest> save;
    private final List<Identifier> dimensions;private final Set<Identifier> editable=new LinkedHashSet<>();private final Map<Identifier,Set<Feature>> features=new LinkedHashMap<>();private final Set<Option> options=new LinkedHashSet<>();
    private Lock mode;private boolean library;private int index,left,span;private String problem="";
    StudioLockScreen(Screen parent,BundleManifest original,JsonObject preset,Consumer<BundleManifest> save){
        super(Component.literal("Preset lock editor"));this.parent=parent;this.original=original;this.preset=preset;this.save=save;mode=original.lock();library=original.allowStructureLibrary();
        dimensions=preset.getAsJsonObject("dimensions").keySet().stream().sorted().map(Identifier::parse).toList();editable.addAll(original.editableDimensions());original.editableFeatures().forEach((key,list)->features.put(key,new LinkedHashSet<>(list)));options.addAll(original.editableOptions());
    }
    private BundleManifest manifest(){var m=original;var chosen=new LinkedHashMap<Identifier,List<Feature>>();if(mode==Lock.PARTIAL)features.forEach((key,set)->{if(!set.isEmpty()&&!editable.contains(key))chosen.put(key,List.copyOf(set));});return new BundleManifest(m.preset(),m.title(),m.description(),m.author(),m.release(),mode,m.options(),mode==Lock.PARTIAL?List.copyOf(editable):List.of(),chosen,mode==Lock.PARTIAL?List.copyOf(options):List.of(),m.requiredMods(),library);}
    @Override protected void init(){span=Math.min(420,width-24);left=(width-span)/2;int half=(span-8)/2,third=(span-12)/3;
        button("Lock: "+mode.getSerializedName(),left,38,half,()->{mode=Lock.values()[(mode.ordinal()+1)%Lock.values().length];rebuildWidgets();});
        button("Structure Library: "+(library?"Yes":"No"),left+half+8,38,half,()->{library=!library;rebuildWidgets();});
        button("←",left,66,32,()->{index=Math.floorMod(index-1,dimensions.size());rebuildWidgets();});button("→",left+span-32,66,32,()->{index=(index+1)%dimensions.size();rebuildWidgets();});
        var id=dimensions.get(index);boolean all=editable.contains(id);boolean partial=mode==Lock.PARTIAL;
        button("Edit whole dimension: "+(all?"Yes":"No"),left,96,span,()->{if(!editable.remove(id))editable.add(id);features.remove(id);rebuildWidgets();}).active=partial;
        int featureWidth=(span-18)/4;
        for(int i=0;i<Feature.values().length;i++){var feature=Feature.values()[i];boolean active=features.getOrDefault(id,Set.of()).contains(feature);String label=feature==Feature.NATIVE_FEATURES?"Native":feature.getSerializedName();button(label+": "+(active?"Yes":"No"),left+i%4*(featureWidth+6),124+i/4*24,featureWidth,()->{var set=features.computeIfAbsent(id,key->new LinkedHashSet<>());if(!set.remove(feature))set.add(feature);rebuildWidgets();}).active=partial&&!all&&solid(id);}
        for(int i=0;i<Option.values().length;i++){var option=Option.values()[i];String label=switch(option){case SEED->"Seed";case GENERATE_STRUCTURES->"Structures";case BONUS_CHEST->"Bonus chest";};button("Edit "+label+": "+(options.contains(option)?"Yes":"No"),left+i*(third+6),180,third,()->{if(!options.remove(option))options.add(option);rebuildWidgets();}).active=partial;}
        var done=button("Done",left,height-26,half,()->{save.accept(manifest());onClose();});button("Cancel",left+half+8,height-26,half,this::onClose);
        var validation=BundleManifest.CODEC.encodeStart(JsonOps.INSTANCE,manifest()).flatMap(json->BundleManifest.CODEC.parse(JsonOps.INSTANCE,json));problem=validation.error().map(error->error.message()).orElse("");done.active=problem.isEmpty();
    }
    private boolean solid(Identifier id){String type=preset.getAsJsonObject("dimensions").getAsJsonObject(id.toString()).getAsJsonObject("generator").get("type").getAsString();return type.equals("worldarchitect:single_block")||type.equals("worldarchitect:layered");}
    private Button button(String label,int x,int y,int w,Runnable action){return addRenderableWidget(Button.builder(Component.literal(label),b->action.run()).bounds(x,y,w,20).build());}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int x,int y,float tick){super.extractRenderState(graphics,x,y,tick);graphics.centeredText(font,title,width/2,8,0xFFF1E7CE);graphics.centeredText(font,Component.literal("Partial permits only the selected edits. Full fixes generation."),width/2,22,0xFFCCCCCC);graphics.centeredText(font,Component.literal(dimensions.get(index).toString()),width/2,72,0xFFEAEAEA);var lines=font.split(Component.literal(problem),span);for(int i=0;i<Math.min(2,lines.size());i++)graphics.text(font,lines.get(i),left,height-50+i*9,0xFFFFAAAA);}
}
