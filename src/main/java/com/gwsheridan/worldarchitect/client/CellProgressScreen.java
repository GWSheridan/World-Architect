package com.gwsheridan.worldarchitect.client;

import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/** Server-provided view only. Every selection is checked against current ownership and credits. */
final class CellProgressScreen extends Screen {
    private final CompoundTag view;
    CellProgressScreen(CompoundTag view){super(Component.literal("World Expansion"));this.view=view.copy();}
    @Override protected void init(){int w=Math.min(400,width-24),x=(width-w)/2;
        addRenderableWidget(Button.builder(Component.literal("Choose frontier cell"),b->{var cells=new LinkedHashMap<String,String>();for(var value:view.getListOrEmpty("frontier")){var c=(CompoundTag)value;String coords=c.getIntOr("q",0)+" "+c.getIntOr("r",0);cells.put(c.getIntOr("q",0)+", "+c.getIntOr("r",0)+" · "+c.getStringOr("type","Cell"),coords);}minecraft.gui.setScreen(new StudioChoiceScreen(this,"Spend one expansion credit",List.copyOf(cells.keySet()),label->send("wa_cells choose "+cells.get(label))));}).bounds(x,64,w,20).build()).active=view.getBooleanOr("choice",false)&&view.getIntOr("credits",0)>0;
        addRenderableWidget(Button.builder(Component.literal("Objectives"),b->{var lines=new ArrayList<String>();for(var value:view.getListOrEmpty("goals")){var goal=(CompoundTag)value;lines.add((goal.getBooleanOr("complete",false)?"✓ ":"○ ")+goal.getStringOr("title","")+" · "+goal.getStringOr("trigger",""));if(!goal.getStringOr("prerequisites","").isBlank())lines.add("Requires: "+goal.getStringOr("prerequisites",""));}minecraft.gui.setScreen(new StudioReportScreen(this,"Expansion objectives",lines));}).bounds(x,89,w,20).build());
        addRenderableWidget(Button.builder(Component.literal("Return home"),b->send("wa_cells home")).bounds(x,114,w,20).build());
        addRenderableWidget(Button.builder(Component.literal("Refresh"),b->send("wa_cells panel")).bounds(x,139,w,20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"),b->onClose()).bounds(x,height-26,w,20).build());
    }
    private void send(String command){if(minecraft.getConnection()!=null)minecraft.getConnection().sendCommand(command);minecraft.gui.setScreen(null);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float tick){super.extractRenderState(g,x,y,tick);g.centeredText(font,title,width/2,12,ArchitectTheme.TEXT);g.centeredText(font,Component.literal("Territory "+view.getIntOr("territory",0)+" · "+view.getIntOr("credits",0)+" expansion credits"),width/2,39,ArchitectTheme.MUTED);}
}
