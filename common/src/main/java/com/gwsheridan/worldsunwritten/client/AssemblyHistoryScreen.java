package com.gwsheridan.worldsunwritten.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/** Every history operation uses the server's session token and serial. */
final class AssemblyHistoryScreen extends Screen {
    private final CompoundTag data;
    AssemblyHistoryScreen(CompoundTag data) { super(Component.literal("Assembly history and recovery")); this.data=data.copy(); }
    private void send(String operation,String args) { if(minecraft.getConnection()!=null)minecraft.getConnection().sendCommand("worldsunwritten assembly "+operation+" "+data.getStringOr("token","")+" "+data.getIntOr("serial",0)+(args.isEmpty()?"":" "+args)); }
    @Override protected void init() {
        int span=Math.min(420,width-24),left=(width-span)/2,half=(span-8)/2,index=data.getIntOr("selected",0),count=data.getListOrEmpty("modules").size();
        button("Undo",left,34,half,data.getBooleanOr("can_undo",false),()->send("undo",""));
        button("Redo",left+half+8,34,half,data.getBooleanOr("can_redo",false),()->send("redo",""));
        button("Restore recovery",left,58,half,data.getBooleanOr("can_recover",false),()->send("recover",""));
        button("Discard recovery",left+half+8,58,half,data.getBooleanOr("recovery_pending",false),()->send("discard_recovery",""));
        button("Duplicate selected module",left,86,span,count>0&&count<16,()->send("duplicate",""+index));
        button("Move module earlier",left,110,span,index>0,()->send("move",index+" "+(index-1)));
        button("Move module later",left,134,span,index+1<count,()->send("move",index+" "+(index+1)));
        button("Back",left,height-26,span,true,this::onClose);
    }
    private void button(String text,int x,int y,int width,boolean enabled,Runnable action){addRenderableWidget(Button.builder(Component.literal(text),b->action.run()).bounds(x,y,width,20).build()).active=enabled;}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){send("refresh","");}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float tick){super.extractRenderState(g,x,y,tick);g.centeredText(font,title,width/2,8,0xFFF1E7CE);int span=Math.min(420,width-24),left=(width-span)/2;var lines=font.split(Component.literal(data.getStringOr("recovery_problem","").isEmpty()?"32 checkpoints · shared server memory budget · saves retain revision checks":data.getStringOr("recovery_problem","")),span);for(int i=0;i<Math.min(2,lines.size());i++)g.text(font,lines.get(i),left,height-52+i*10,0xFFEAEAEA);}
}
