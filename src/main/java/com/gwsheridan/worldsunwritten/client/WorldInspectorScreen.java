package com.gwsheridan.worldsunwritten.client;

import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

final class WorldInspectorScreen extends Screen {
    private final CompoundTag data; private int page;
    WorldInspectorScreen(CompoundTag data) { super(Component.literal("Worlds Unwritten · World Inspector")); this.data = data.copy(); }
    private void command(String value) { if (minecraft.getConnection() != null) minecraft.getConnection().sendCommand(value); }
    @Override protected void init() {
        int span = Math.min(480,width-24), left = (width-span)/2, third = (span-12)/4;
        add("Refresh",left,28,third,()->command("worldsunwritten inspect"));
        add("Installed",left+third+4,28,third,()->command("worldsunwritten inspect installed")).active=data.getBooleanOr("installed",false);
        add("Compare preset",left+2*(third+4),28,third,()->minecraft.gui.setScreen(new StudioChoiceScreen(this,"Compare saved preset",strings("presets"),value->{String[] parts=value.split(" / ",2);command("worldsunwritten inspect compare "+parts[0]+" "+parts[1]);}))).active=!strings("presets").isEmpty();
        add("Export report",left+3*(third+4),28,third,()->command(data.getStringOr("comparison","").isEmpty()?"worldsunwritten inspect report":data.getStringOr("comparison","").equals("@installed")?"worldsunwritten inspect installed report":"worldsunwritten inspect compare "+data.getStringOr("comparison","")+" report"));
        int half=(span-8)/2;add("Previous",left,height-52,half,()->page=Math.max(0,page-1));add("Next",left+half+8,height-52,half,()->page=Math.min(pages()-1,page+1));add("Close",left,height-26,span,this::onClose);
    }
    private Button add(String text,int x,int y,int width,Runnable action){return addRenderableWidget(Button.builder(Component.literal(text),b->action.run()).bounds(x,y,width,20).build());}
    private List<String> strings(String key){return data.getListOrEmpty(key).stream().map(t->t.asString().orElse("")).toList();}
    private List<net.minecraft.util.FormattedCharSequence> lines(){return strings("rows").stream().flatMap(s->font.split(Component.literal(s),Math.min(480,width-24)).stream()).toList();}
    private int perPage(){return Math.max(1,(height-122)/10);} private int pages(){return Math.max(1,(lines().size()+perPage()-1)/perPage());}
    @Override public void onClose(){minecraft.gui.setScreen(null);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float tick){super.extractRenderState(g,x,y,tick);page=Math.min(page,pages()-1);g.centeredText(font,title,width/2,8,0xFFF1E7CE);var lines=lines();for(int i=0;i<perPage()&&page*perPage()+i<lines.size();i++)g.text(font,lines.get(page*perPage()+i),(width-Math.min(480,width-24))/2,60+i*10,0xFFEAEAEA);g.centeredText(font,Component.literal((page+1)+" / "+pages()),width/2,height-66,0xFFCCCCCC);}
}
