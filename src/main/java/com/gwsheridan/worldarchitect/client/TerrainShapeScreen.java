package com.gwsheridan.worldarchitect.client;

import com.gwsheridan.worldarchitect.config.*;
import com.gwsheridan.worldarchitect.config.AdvancedTerrain.Landform;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.*;
import org.jspecify.annotations.Nullable;

final class TerrainShapeScreen extends Screen implements AuthoringContext {
    @Override public String authoringKey() { return parent instanceof AuthoringContext context ? context.authoringKey() : "terrain"; }
    private static final List<Optional<Landform>> LANDFORMS=Stream.concat(Stream.of(Optional.<Landform>empty()),Arrays.stream(Landform.values()).map(Optional::of)).toList();
    private final Screen parent;private final Consumer<TerrainShape> save;private final RegistryAccess registries;private final int minimum,maximum;
    private String elevation,relief,scale,seedSalt;private Optional<Landform> landform;private Optional<RegionalTerrain> regions;
    private int left,top,half;private EditBox elevationField,reliefField,scaleField,saltField;private CycleButton<Optional<Landform>> landformButton;private Button done;
    TerrainShapeScreen(Screen parent,TerrainShape initial,int minY,int height,RegistryAccess registries,Consumer<TerrainShape> save){
        super(Component.translatable("worldarchitect.terrain.title"));this.parent=parent;this.save=save;this.registries=registries;minimum=minY;maximum=minY+height-4;
        elevation=""+initial.elevation();relief=""+initial.relief();scale=""+initial.advanced().map(AdvancedTerrain::scale).orElse(AdvancedTerrain.DEFAULT_SCALE);seedSalt=""+initial.advanced().map(AdvancedTerrain::seedSalt).orElse(0L);landform=initial.advanced().map(AdvancedTerrain::landform);regions=initial.regions();
    }
    @Override protected void init(){int span=Math.min(300,width-20);left=(width-span)/2;top=Math.max(2,(height-202)/2);half=(span-8)/2;
        elevationField=field("elevation",elevation,left,32,5);reliefField=field("relief",relief,left+half+8,32,5);
        landformButton=addRenderableWidget(CycleButton.builder(TerrainShapeScreen::landformName,landform).withValues(LANDFORMS).create(left,top+56,span,20,Component.translatable("worldarchitect.terrain.landform"),(b,v)->{landform=v;refresh();}));
        scaleField=field("scale",scale,left,94,4);saltField=field("seed_salt",seedSalt,left+half+8,94,20);
        int third=(span-16)/3;quick("flat",TerrainShape.FLAT,left,third);quick("hills",TerrainShape.HILLS,left+third+8,third);quick("highlands",TerrainShape.HIGHLANDS,left+2*(third+8),third);
        addRenderableWidget(Button.builder(Component.literal("Regional terrain ("+regions.map(r->r.zones().size()).orElse(0)+")"),b->{
            try {var draft=new TerrainShape(Integer.parseInt(elevation),Integer.parseInt(relief),landform.map(v->new AdvancedTerrain(v,Integer.parseInt(scale),Long.parseLong(seedSalt))),regions);
                minecraft.gui.setScreen(new TerrainRegionsScreen(this,draft,registries,next->{regions=next;if(next.isPresent()&&landform.isEmpty()){landform=Optional.of(Landform.PLATEAUS);}}));
            }catch(NumberFormatException ignored){ }
        }).bounds(left,top+142,span,20).build());
        done=addRenderableWidget(Button.builder(CommonComponents.GUI_DONE,b->{var shape=parse();if(shape!=null){save.accept(shape);onClose();}}).bounds(left,top+178,half,20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL,b->onClose()).bounds(left+half+8,top+178,half,20).build());
        elevationField.setResponder(v->{elevation=v;refresh();});reliefField.setResponder(v->{relief=v;refresh();});scaleField.setResponder(v->{scale=v;refresh();});saltField.setResponder(v->{seedSalt=v;refresh();});refresh();
    }
    private EditBox field(String name,String text,int x,int y,int limit){var f=addRenderableWidget(new EditBox(font,x,top+y,half,20,Component.translatable("worldarchitect.terrain."+name)));f.setMaxLength(limit);f.setValue(text);return f;}
    private void quick(String name,TerrainShape shape,int x,int width){addRenderableWidget(Button.builder(Component.translatable("worldarchitect.terrain."+name),b->{elevationField.setValue(""+Math.clamp(shape.elevation(),minimum,maximum));reliefField.setValue(""+shape.relief());landform=regions.isPresent()?Optional.of(Landform.PLATEAUS):Optional.empty();landformButton.setValue(landform);refresh();}).bounds(x,top+118,width,20).build());}
    private void refresh(){scaleField.active=landform.isPresent();saltField.active=landform.isPresent();done.active=parse()!=null;}
    private @Nullable TerrainShape parse(){try{int y=Integer.parseInt(elevation),variation=Integer.parseInt(relief),span=Integer.parseInt(scale);long salt=Long.parseLong(seedSalt);
        if(y<minimum||y>maximum||variation<0||variation>TerrainShape.MAX_RELIEF)return null;
        if(landform.isPresent()&&(span<AdvancedTerrain.MIN_SCALE||span>AdvancedTerrain.MAX_SCALE||landform.orElseThrow()==Landform.FLOATING_ISLANDS&&y<=minimum))return null;
        var shape=new TerrainShape(y,variation,landform.map(v->new AdvancedTerrain(v,span,salt)),regions);return shape.validate().isSuccess()?shape:null;
    }catch(RuntimeException e){return null;}}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float tick){super.extractRenderState(g,mouseX,mouseY,tick);g.centeredText(font,title,width/2,top,0xFFF1E7CE);label(g,"elevation",left,19);label(g,"relief",left+half+8,19);label(g,"scale",left,81);label(g,"seed_salt",left+half+8,81);g.text(font,Component.translatable("worldarchitect.terrain.range",minimum,maximum),left,top+167,done.active?0xFFCCCCCC:0xFFFFAAAA);}
    private void label(GuiGraphicsExtractor g,String name,int x,int y){g.text(font,Component.translatable("worldarchitect.terrain."+name),x,top+y,0xFFFFFFFF);}
    private static Component landformName(Optional<Landform> landform){return Component.translatable("worldarchitect.terrain.landform."+landform.map(Landform::getSerializedName).orElse("legacy"));}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
}
