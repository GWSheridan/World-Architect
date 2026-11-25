package com.gwsheridan.worldsunwritten.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Shared surfaces and focus states; labels remain native, accessible game text. */
public final class UnwrittenTheme {
    public static final int TEXT=0xffe8ece9, MUTED=0xffaebbb6, ACCENT=0xffcfb47c, PANEL=0xf21b2426;
    public static final Identifier SURFACE=Identifier.fromNamespaceAndPath("worldsunwritten","textures/gui/studio_slate.png");
    public static final Identifier ISLAND=Identifier.fromNamespaceAndPath("worldsunwritten","textures/gui/skyblock.png");
    private UnwrittenTheme() {}
    public static boolean owns(Screen screen){return screen!=null&&screen.getClass().getName().startsWith("com.gwsheridan.worldsunwritten.client.");}
    public static boolean active(){return owns(Minecraft.getInstance().gui.screen());}
    public static void background(Screen screen, GuiGraphicsExtractor graphics){
        if(!owns(screen))return;
        var g=graphics;int w=screen.width,h=screen.height;
        float side=Math.max(w,h),u=w/side,v=h/side;g.blit(SURFACE,0,0,w,h,(1-u)/2,(1+u)/2,(1-v)/2,(1+v)/2);
        g.fill(0,0,w,h,0x5a080f12);
        g.fill(0,0,w,23,0xff10191b);g.fill(0,23,w,24,0xff6c6652);
        int left=w,right=0,top=h,bottom=0;
        for(var child:screen.children())if(child instanceof AbstractWidget widget&&widget.visible&&widget.getY()>=24&&widget.getY()<h-35){
            left=Math.min(left,widget.getX());right=Math.max(right,widget.getRight());top=Math.min(top,widget.getY());bottom=Math.max(bottom,Math.min(h-40,widget.getBottom()));
        }
        if(screen instanceof ArchitectPanelLayout layout)layout.extractPanels(g);
        else if(right>left)panel(g,Math.max(8,left-12),Math.max(29,top-18),Math.min(w-8,right+12),Math.min(h-36,bottom+12),PANEL);
        g.fill(0,h-34,w,h,0xff111b1e);g.fill(0,h-35,w,h-34,0xff45514f);
        if(w>=650)g.text(Minecraft.getInstance().font,Component.literal("WORLDS UNWRITTEN"),12,8,ACCENT);
    }
    public static void panel(GuiGraphicsExtractor g,int x,int y,int right,int bottom,int color){
        if(right<=x||bottom<=y)return;
        g.fill(x,y,right,bottom,color);g.fill(x,y,right,y+1,0xff46514e);g.fill(x,y,x+1,bottom,0xff384340);g.fill(right-1,y,right,bottom,0xff111a1b);g.fill(x,bottom-1,right,bottom,0xff111a1b);
    }
    public static void button(GuiGraphicsExtractor g,AbstractWidget widget){
        int x=widget.getX(),y=widget.getY(),right=x+widget.getWidth(),bottom=y+widget.getHeight();boolean focus=widget.isHoveredOrFocused();
        String label=widget.getMessage().getString().toLowerCase(java.util.Locale.ROOT);
        boolean primary=label.equals("done")||label.startsWith("apply")||label.startsWith("save ")||label.startsWith("export ")||label.startsWith("create ")||label.equals("continue")||label.equals("use project")||label.equals("use island")||label.equals("choose island");
        int color=!widget.active?0xff202829:focus?0xff3d514a:primary?0xff34483d:0xff273438;
        panel(g,x,y,right,bottom,color);
        if(widget.active&&focus){g.fill(x,y,x+2,bottom,ACCENT);g.fill(x,y,right,y+1,ACCENT);g.fill(x,bottom-1,right,bottom,0xff7b8067);}
    }
}
