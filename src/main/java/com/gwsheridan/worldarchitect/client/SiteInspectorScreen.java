package com.gwsheridan.worldarchitect.client;

import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** A server-owned, read-only view of one completed or explicitly incomplete site sample. */
final class SiteInspectorScreen extends Screen {
    private static final LinkedHashSet<String> CLOSED=new LinkedHashSet<>();
    static void show(CompoundTag view){if(!CLOSED.contains(view.getStringOr("token","")))net.minecraft.client.Minecraft.getInstance().gui.setScreen(new SiteInspectorScreen(view));}
    private final CompoundTag data;private final VoxelPreview preview;
    SiteInspectorScreen(CompoundTag data){super(Component.literal("Structure site inspector"));this.data=data.copy();preview=new VoxelPreview(data.getIntArray("cells").orElse(new int[0]),data.getIntArray("size").orElse(new int[]{1,1,1}));}
    private List<String> values(String key){return data.getListOrEmpty(key).stream().map(t->t.asString().orElse("")).filter(s->!s.isEmpty()).toList();}
    @Override protected void init(){int span=Math.min(480,width-24),left=(width-span)/2,quarter=(span-12)/4;
        button("Coordinates",left,28,quarter,this::coordinates);
        button("Dimension",left+quarter+4,28,quarter,()->minecraft.gui.setScreen(new StudioChoiceScreen(this,"Managed dimensions",values("dimensions"),v->{data.putString("dimension",v);coordinates();})));
        button("Assembly",left+2*(quarter+4),28,quarter,()->minecraft.gui.setScreen(new StudioChoiceScreen(this,"Assemblies",values("structures"),v->{data.putString("structure",v);coordinates();})));
        button("Report",left+3*(quarter+4),28,quarter,()->{var rows=new ArrayList<>(values("rows"));rows.add("Saved preset: "+data.getStringOr("name","")+" / "+data.getStringOr("release",""));rows.add("Source SHA-256: "+data.getStringOr("sha256",""));rows.add("Seed: "+data.getStringOr("seed",""));minecraft.gui.setScreen(new StudioReportScreen(this,"Site placement report",rows));});
        button("Orbit left",left,height-26,(span-8)/3,()->preview.orbit(-1));button("Orbit right",left+(span-8)/3+4,height-26,(span-8)/3,()->preview.orbit(1));button("Close",left+2*((span-8)/3+4),height-26,(span-8)/3,this::onClose);
    }
    private void button(String label,int x,int y,int width,Runnable action){addRenderableWidget(Button.builder(Component.literal(label),b->action.run()).bounds(x,y,width,20).build());}
    private void coordinates(){var dims=values("dimensions");var structures=values("structures");minecraft.gui.setScreen(new StudioFormScreen(this,"Inspect candidate site","Chunk coordinates sample base terrain without creating chunks. The saved preset version stays fixed.",List.of(
        new StudioFormScreen.Field("Dimension",data.getStringOr("dimension",dims.isEmpty()?"minecraft:overworld":dims.getFirst())),new StudioFormScreen.Field("Assembly",data.getStringOr("structure",structures.isEmpty()?"builds:tower":structures.getFirst())),
        new StudioFormScreen.Field("Exact seed",data.getStringOr("seed","18")),new StudioFormScreen.Field("Chunk X",""+data.getIntOr("chunk_x",0)),new StudioFormScreen.Field("Chunk Z",""+data.getIntOr("chunk_z",0))),v->{
            Identifier.parse(v.get(0));Identifier.parse(v.get(1));Long.parseLong(v.get(2));for(int i=3;i<5;i++)if(Math.abs((long)Integer.parseInt(v.get(i)))*16>29_999_792)return "Keep chunks inside world bounds.";
            return dims.contains(v.get(0))&&structures.contains(v.get(1))?"":"Choose a listed dimension and assembly.";
        },v->{data.putString("dimension",v.get(0));data.putString("structure",v.get(1));data.putString("seed",v.get(2));data.putInt("chunk_x",Integer.parseInt(v.get(3)));data.putInt("chunk_z",Integer.parseInt(v.get(4)));request();}));}
    private void request(){if(minecraft.getConnection()==null)return;String fields=data.getStringOr("dimension","")+" "+data.getStringOr("structure","")+" "+data.getStringOr("seed","")+" "+data.getIntOr("chunk_x",0)+" "+data.getIntOr("chunk_z",0);
        minecraft.getConnection().sendCommand("wa_site request "+data.getStringOr("token","")+" "+data.getIntOr("serial",0)+" "+fields);data.putInt("serial",data.getIntOr("serial",0)+1);data.putString("status","pending");data.remove("cells");data.remove("section");
    }
    @Override public void onClose(){CLOSED.add(data.getStringOr("token",""));while(CLOSED.size()>32)CLOSED.removeFirst();if(minecraft.getConnection()!=null)minecraft.getConnection().sendCommand("wa_site close "+data.getStringOr("token",""));minecraft.gui.setScreen(null);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float tick){super.extractRenderState(g,mx,my,tick);int span=Math.min(480,width-24),left=(width-span)/2,half=(span-12)/2,top=82,bottom=height-36;
        g.centeredText(font,title,width/2,8,0xFFF1E7CE);String status=data.getStringOr("status","ready");g.text(font,font.plainSubstrByWidth(status+" · "+data.getStringOr("dimension","")+" · chunk "+data.getIntOr("chunk_x",0)+", "+data.getIntOr("chunk_z",0),span),left,54,0xFFDDDDDD);
        g.text(font,"Assembly",left,68,0xFFDDDDDD);g.text(font,"Terrain section",left+half+12,68,0xFFDDDDDD);
        if(status.equals("pending")){g.centeredText(font,Component.literal("Sampling…"),width/2,top+20,0xFFDDDDDD);return;}
        g.enableScissor(left,top,left+half,bottom);preview.draw(g,left,top,half,bottom-top);
        for(String key:List.of("markers","ports"))for(var value:data.getListOrEmpty(key))if(value instanceof CompoundTag t){var p=t.getIntArray("pos").orElse(new int[0]);if(p.length==3){var dot=preview.project(p[0]+.5,p[1]+.5,p[2]+.5,left,top,half,bottom-top);g.fill(dot[0]-2,dot[1]-2,dot[0]+3,dot[1]+3,key.equals("markers")?0xFFFFD560:0xFF55DDDD);}}
        g.disableScissor();var size=data.getIntArray("section_size").orElse(new int[0]);var section=data.getIntArray("section").orElse(new int[0]);
        if(size.length==2&&size[0]>0&&size[0]<=96&&size[1]>0&&size[1]<=112&&section.length==size[0]*size[1]){
            int x0=left+half+12;for(int y=0;y<size[1];y++)for(int x=0;x<size[0];x++)g.fill(x0+x*half/size[0],top+y*(bottom-top)/size[1],x0+(x+1)*half/size[0],top+(y+1)*(bottom-top)/size[1],section[x+y*size[0]]);
            var origin=data.getIntArray("origin").orElse(new int[0]);int localZ=origin.length==3?data.getIntOr("section_z",0)-origin[2]:0;var cells=data.getIntArray("cells").orElse(new int[0]);
            for(int i=0;i+1<cells.length;i+=2){int p=cells[i],x=p&127,y=(p>>7)&127,z=(p>>14)&127;if(z!=localZ||origin.length!=3)continue;int row=size[1]-1-(origin[1]+y-data.getIntOr("section_min_y",0));if(x<size[0]&&row>=0&&row<size[1])g.fill(x0+x*half/size[0],top+row*(bottom-top)/size[1],x0+(x+1)*half/size[0],top+(row+1)*(bottom-top)/size[1],0xFFF1C16A);}
        }
    }
}
