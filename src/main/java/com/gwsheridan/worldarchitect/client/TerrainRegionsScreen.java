package com.gwsheridan.worldarchitect.client;

import com.gwsheridan.worldarchitect.config.*;
import com.gwsheridan.worldarchitect.config.RegionalTerrain.*;
import java.util.*;
import java.util.function.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;

/** Coordinate-map edits remain detached until Done; right-click moves only the draft region. */
final class TerrainRegionsScreen extends Screen {
    private final Screen parent; private final RegistryAccess registries; private final Consumer<Optional<RegionalTerrain>> save;
    private final TerrainShape base; private final List<Zone> zones; private int selected,centerX,centerZ,scale=4,mapX,mapY,mapSize; private String error="";
    private final ClientDraftJournal journal;
    TerrainRegionsScreen(Screen parent,TerrainShape base,RegistryAccess registries,Consumer<Optional<RegionalTerrain>> save){
        super(Component.literal("Regional terrain · version 3"));this.parent=parent;this.base=base;this.registries=registries;this.save=save;
        zones=new ArrayList<>(base.regions().map(RegionalTerrain::zones).orElse(List.of()));if(!zones.isEmpty())focus(zones.getFirst());
        journal=new ClientDraftJournal("regions:"+(parent instanceof AuthoringContext context?context.authoringKey():"terrain"),snapshot());
    }
    @Override protected void init(){
        int left=12,span=width-24,half=(span-12)/2,right=left+half+12;mapX=left;mapY=56;mapSize=Math.max(40,Math.min(half,height-116));
        journal.observe(snapshot());
        add("History / organize",right,28,half,this::history);
        add("Select region",left,28,half,()->minecraft.gui.setScreen(new StudioChoiceScreen(this,"Regions · highest priority first",zones.stream().map(z->z.name()+" · priority "+z.priority()).toList(),value->{selected=index(value.split(" · ")[0]);focus(current());minecraft.gui.setScreen(this);}))).active=!zones.isEmpty();
        add("Add rectangle",right,56,half,()->addRegion(Mask.RECTANGLE)).active=zones.size()<RegionalTerrain.MAX_REGIONS;
        add("Add circle",right,80,half,()->addRegion(Mask.CIRCLE)).active=zones.size()<RegionalTerrain.MAX_REGIONS;
        add("Bounds & priority",right,104,half,this::bounds).active=!zones.isEmpty();add("Terrain & biome",right,128,half,this::terrain).active=!zones.isEmpty();
        add("Remove region",right,152,half,()->{zones.remove(selected);selected=Math.max(0,selected-1);rebuildWidgets();}).active=!zones.isEmpty();
        add("Map view",right,176,half,this::view);
        int footer=(span-8)/2;add("Done",left,height-26,footer,()->{save.accept(zones.isEmpty()?Optional.empty():Optional.of(new RegionalTerrain(zones)));onClose();}).active=!journal.pending();add("Cancel",left+footer+8,height-26,footer,this::onClose);
        if(journal.offer())history();
    }
    private com.google.gson.JsonElement snapshot(){return Zone.CODEC.listOf(0,32).encodeStart(net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,registries),zones).getOrThrow();}
    private void restore(com.google.gson.JsonElement value){var next=Zone.CODEC.listOf(0,32).parse(net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,registries),value).getOrThrow();if(!next.isEmpty())new RegionalTerrain(next).validate().getOrThrow();zones.clear();zones.addAll(next);selected=Math.clamp(selected,0,Math.max(0,zones.size()-1));}
    private void history(){minecraft.gui.setScreen(new AuthoringHistoryScreen(this,journal,this::snapshot,this::restore,List.of(
        new AuthoringHistoryScreen.Action("Duplicate selected region",()->!zones.isEmpty()&&zones.size()<32,this::duplicate),
        new AuthoringHistoryScreen.Action("Move selected region earlier",()->!zones.isEmpty()&&selected>0,()->{Collections.swap(zones,selected,selected-1);selected--;}),
        new AuthoringHistoryScreen.Action("Move selected region later",()->selected+1<zones.size(),()->{Collections.swap(zones,selected,selected+1);selected++;})
    )));}
    private void duplicate(){var z=current();int n=1;String prefix=z.name().substring(0,Math.min(24,z.name().length()))+"_copy";while(indexOfName(prefix+n))n++;zones.add(new Zone(prefix+n,z.mask(),z.minX(),z.minZ(),z.maxX(),z.maxZ(),z.priority(),z.transition(),z.elevation(),z.relief(),z.terrain(),z.biome()));selected=zones.size()-1;}
    private boolean indexOfName(String name){return zones.stream().anyMatch(z->z.name().equals(name));}
    private int index(String name){for(int i=0;i<zones.size();i++)if(zones.get(i).name().equals(name))return i;return 0;}
    private Zone current(){return zones.get(Math.clamp(selected,0,zones.size()-1));}
    private void focus(Zone z){centerX=(int)(((long)z.minX()+z.maxX())/2);centerZ=(int)(((long)z.minZ()+z.maxZ())/2);}
    private Button add(String text,int x,int y,int width,Runnable action){return addRenderableWidget(Button.builder(Component.literal(text),b->action.run()).bounds(x,y,width,20).build());}
    private void addRegion(Mask mask){int suffix=1;while(zones.stream().map(Zone::name).toList().contains("region_"+suffix))suffix++;
        int x=Math.clamp(centerX,-RegionalTerrain.WORLD_BOUND+128,RegionalTerrain.WORLD_BOUND-128),z=Math.clamp(centerZ,-RegionalTerrain.WORLD_BOUND+128,RegionalTerrain.WORLD_BOUND-128);
        zones.add(new Zone("region_"+suffix,mask,x-128,z-128,x+128,z+128,0,32,base.elevation(),base.relief(),base.advanced().filter(t->t.landform()!=AdvancedTerrain.Landform.FLOATING_ISLANDS).orElse(new AdvancedTerrain(AdvancedTerrain.Landform.PLATEAUS,192,0)),Optional.empty()));selected=zones.size()-1;focus(current());bounds();}
    private void form(String title,List<StudioFormScreen.Field> fields,Function<List<String>,Zone> parse){
        minecraft.gui.setScreen(new StudioFormScreen(this,title,"Changes remain in this draft. Existing worlds retain their saved generation.",fields,v->{try{var zone=parse.apply(v);var copy=new ArrayList<>(zones);copy.set(selected,zone);new RegionalTerrain(copy).validate().getOrThrow();return "";}catch(RuntimeException e){return e.getMessage()==null?"Check the region fields.":e.getMessage();}},v->{zones.set(selected,parse.apply(v));focus(current());}));
    }
    private StudioFormScreen.Field f(String name,Object value){return new StudioFormScreen.Field(name,""+value);}
    private int integer(List<String> values,int i){return Integer.parseInt(values.get(i));}
    private void bounds(){var z=current();form(z.mask().getSerializedName()+" bounds",List.of(f("Name",z.name()),f("Priority",z.priority()),f("Minimum X",z.minX()),f("Minimum Z",z.minZ()),f("Maximum X",z.maxX()),f("Maximum Z",z.maxZ()),f("Transition blocks",z.transition())),v->new Zone(v.get(0),z.mask(),integer(v,2),integer(v,3),integer(v,4),integer(v,5),integer(v,1),integer(v,6),z.elevation(),z.relief(),z.terrain(),z.biome()));}
    private void terrain(){var z=current();form("Region terrain and biome",List.of(f("Elevation",z.elevation()),f("Relief",z.relief()),f("Landform",z.terrain().landform().getSerializedName()),f("Scale",z.terrain().scale()),f("Seed offset",z.terrain().seedSalt()),f("Biome ID or none",z.biome().flatMap(b->b.unwrapKey()).map(k->k.identifier().toString()).orElse("none"))),v->{
        var biome=v.get(5).equals("none")?Optional.<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>>empty():Optional.<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>>of(registries.lookupOrThrow(Registries.BIOME).get(ResourceKey.create(Registries.BIOME,Identifier.parse(v.get(5)))).orElseThrow(()->new IllegalArgumentException("Choose an installed biome ID.")));
        return new Zone(z.name(),z.mask(),z.minX(),z.minZ(),z.maxX(),z.maxZ(),z.priority(),z.transition(),integer(v,0),integer(v,1),new AdvancedTerrain(AdvancedTerrain.Landform.valueOf(v.get(2).toUpperCase(Locale.ROOT)),integer(v,3),Long.parseLong(v.get(4))),biome);
    });}
    private void view(){minecraft.gui.setScreen(new StudioFormScreen(this,"Region map view","The map shows coordinate masks. Left-click selects; right-click moves the selected draft region.",List.of(f("Centre X",centerX),f("Centre Z",centerZ),f("Blocks per cell",scale)),v->{try{return Math.abs((long)integer(v,0))<=RegionalTerrain.WORLD_BOUND&&Math.abs((long)integer(v,1))<=RegionalTerrain.WORLD_BOUND&&integer(v,2)>=1&&integer(v,2)<=65536?"":"Keep coordinates within world bounds and scale at 1–65536.";}catch(RuntimeException e){return "Enter integer coordinates and scale.";}},v->{centerX=integer(v,0);centerZ=integer(v,1);scale=integer(v,2);}));}
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean twice){
        if(event.x()>=mapX&&event.x()<mapX+mapSize&&event.y()>=mapY&&event.y()<mapY+mapSize){
            int x=centerX+((int)((event.x()-mapX)*48/mapSize)-24)*scale,z=centerZ+((int)((event.y()-mapY)*48/mapSize)-24)*scale;
            if(event.button()==InputConstants.MOUSE_BUTTON_RIGHT&&!zones.isEmpty()){
                var a=current();int dx=x-(int)(((long)a.minX()+a.maxX())/2),dz=z-(int)(((long)a.minZ()+a.maxZ())/2);
                var moved=new Zone(a.name(),a.mask(),a.minX()+dx,a.minZ()+dz,a.maxX()+dx,a.maxZ()+dz,a.priority(),a.transition(),a.elevation(),a.relief(),a.terrain(),a.biome());
                if(moved.validate().isSuccess()){zones.set(selected,moved);journal.observe(snapshot());error="";}else error="Move refused: outside world bounds.";
            }else if(event.button()==InputConstants.MOUSE_BUTTON_LEFT){for(var a:new RegionalTerrain(zones).ordered())if(a.edgeDistance(x,z)>=0){selected=index(a.name());break;}}
            return true;
        }return super.mouseClicked(event,twice);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float tick){super.extractRenderState(g,x,y,tick);g.centeredText(font,title,width/2,8,0xFFF1E7CE);
        var ordered=new RegionalTerrain(zones).ordered();for(int row=0;row<48;row++)for(int col=0;col<48;col++){
            int wx=centerX+(col-24)*scale,wz=centerZ+(row-24)*scale,color=0xFF252B32;
            for(var zone:ordered)if(zone.edgeDistance(wx,wz)>=0){color=0xFF000000|(zone.name().hashCode()&0x007F7F7F)|0x00303030;if(!zones.isEmpty()&&zone.name().equals(current().name())&&zone.edgeDistance(wx,wz)<scale*2)color=0xFFFFCF50;break;}
            g.fill(mapX+col*mapSize/48,mapY+row*mapSize/48,mapX+(col+1)*mapSize/48,mapY+(row+1)*mapSize/48,color);
        }
        String label=zones.isEmpty()?"No regions":current().name();g.text(font,font.plainSubstrByWidth(label,mapSize),mapX,mapY+mapSize+4,0xFFFFFFFF);
        g.text(font,font.plainSubstrByWidth(error.isEmpty()?"Coordinate masks · "+scale+" blocks/cell":error,mapSize),mapX,mapY+mapSize+15,0xFFCCCCCC);
    }
    @Override public void onClose(){journal.close(this,()->minecraft.gui.setScreen(parent));}
}
