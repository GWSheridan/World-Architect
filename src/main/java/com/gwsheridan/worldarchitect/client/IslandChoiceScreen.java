package com.gwsheridan.worldarchitect.client;

import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Bounded public previews and counts supplied by the server; no client-owned allocation. */
final class IslandChoiceScreen extends Screen {
    private final CompoundTag data;
    private final List<CompoundTag> types;
    private int selected, left, span, detail, detailWidth;
    private IslandModelPreview preview;
    private Button choose;
    private String status;
    IslandChoiceScreen(CompoundTag data){
        super(Component.literal("Choose your starting island"));this.data=data.copy();
        types=data.getListOrEmpty("types").stream().filter(CompoundTag.class::isInstance).map(v->((CompoundTag)v).copy()).limit(16).toList();
        if(types.isEmpty())throw new IllegalArgumentException("Missing island types.");
        status=data.getStringOr("status","");updatePreview();
    }
    private void updatePreview(){var type=types.get(selected);var cells=type.getIntArray("cells").orElse(new int[0]);var states=type.getIntArray("states").orElse(new int[0]);var size=type.getIntArray("size").orElse(new int[]{1,1,1});if(cells.length>8192||cells.length!=states.length*2||size.length!=3||Arrays.stream(size).anyMatch(n->n<1||n>64))throw new IllegalArgumentException("Invalid island preview.");var blocks=new ArrayList<com.gwsheridan.worldarchitect.structure.IslandBuild.Cell>();for(int i=0;i<states.length;i++){int p=cells[i*2];blocks.add(new com.gwsheridan.worldarchitect.structure.IslandBuild.Cell(new net.minecraft.core.BlockPos(p&127,p>>7&127,p>>14&127),net.minecraft.world.level.block.Block.stateById(states[i]),Optional.empty()));}preview=new IslandModelPreview(new net.minecraft.core.BlockPos(size[0],size[1],size[2]),blocks);}
    @Override protected void init(){
        span=Math.min(660,width-24);left=(width-span)/2;int listWidth=Math.max(100,Math.min(190,span/3));detail=left+listWidth+14;detailWidth=span-listWidth-14;
        var list=addRenderableWidget(new Types(listWidth,height-91));list.setX(left);
        var rows=new ArrayList<Row>();for(int i=0;i<types.size();i++)rows.add(new Row(i));list.replaceEntries(rows);list.setSelected(rows.get(selected));
        int half=(span-8)/2;
        choose=addRenderableWidget(Button.builder(Component.literal("Choose island"),b->{if(minecraft.getConnection()!=null){choose.active=false;status="Waiting for the server to confirm your island…";minecraft.getConnection().sendCommand("wa_island choose "+data.getStringOr("token","")+" "+data.getStringOr("catalog","")+" "+types.get(selected).getStringOr("id",""));}}).bounds(left,height-26,half,20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"),b->onClose()).bounds(left+half+8,height-26,half,20).build());
        choose.active=types.get(selected).getIntOr("available",0)>0;
        addRenderableWidget(Button.builder(Component.literal("↶"),b->preview.orbit(-1)).bounds(detail,48,28,20).build());
        addRenderableWidget(Button.builder(Component.literal("↷"),b->preview.orbit(1)).bounds(detail+32,48,28,20).build());
    }
    @Override public void onClose(){minecraft.gui.setScreen(null);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float tick){
        super.extractRenderState(g,x,y,tick);g.centeredText(font,title,width/2,8,ArchitectTheme.TEXT);
        g.centeredText(font,Component.literal(data.getBooleanOr("team_mode",false)?"Saved for this world · Shared with your team":"Saved for this world · Your personal island"),width/2,26,ArchitectTheme.MUTED);
        int top=75,previewHeight=Math.max(54,Math.min(190,height-158));
        ArchitectTheme.panel(g,detail,top,detail+detailWidth,top+previewHeight,0xff121d21);
        preview.draw(g,detail+4,top+4,detailWidth-8,previewHeight-8);
        var type=types.get(selected);g.text(font,font.plainSubstrByWidth(type.getStringOr("title","Island"),Math.max(20,detailWidth-68)),detail+68,54,ArchitectTheme.ACCENT);
        var lines=font.split(Component.literal(type.getStringOr("description","")),detailWidth);
        for(int i=0;i<Math.min(3,lines.size())&&top+previewHeight+8+i*10<height-60;i++)g.text(font,lines.get(i),detail,top+previewHeight+8+i*10,ArchitectTheme.MUTED);
        if(!status.isEmpty())g.text(font,font.plainSubstrByWidth(status,span),left,height-47,ArchitectTheme.ACCENT);
    }
    private final class Types extends ObjectSelectionList<Row>{
        Types(int w,int h){super(IslandChoiceScreen.this.minecraft,w,h,48,36);}
        @Override public int getRowWidth(){return getWidth()-12;}
        @Override public void setSelected(Row row){super.setSelected(row);if(row!=null){selected=row.index;updatePreview();if(choose!=null)choose.active=types.get(selected).getIntOr("available",0)>0;}}
    }
    private final class Row extends ObjectSelectionList.Entry<Row>{
        final int index;Row(int index){this.index=index;}
        @Override public Component getNarration(){var type=types.get(index);return Component.literal(type.getStringOr("title","Island")+", "+type.getIntOr("available",0)+" available");}
        @Override public void extractContent(GuiGraphicsExtractor g,int x,int y,boolean hover,float tick){
            var type=types.get(index);var id=Identifier.tryParse(type.getStringOr("icon","minecraft:grass_block"));
            if(id!=null&&BuiltInRegistries.ITEM.containsKey(id))g.item(new ItemStack(BuiltInRegistries.ITEM.getValue(id)),getX()+4,getY()+8);
            g.text(font,font.plainSubstrByWidth(type.getStringOr("title","Island"),getWidth()-30),getX()+25,getY()+7,ArchitectTheme.TEXT);
            int count=type.getIntOr("available",0);g.text(font,font.plainSubstrByWidth(count>0?count+" free":"Full",getWidth()-30),getX()+25,getY()+19,count>0?ArchitectTheme.MUTED:0xffd79b89);
        }
    }
}
