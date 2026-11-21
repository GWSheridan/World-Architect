package com.gwsheridan.worldarchitect.client;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Rebuilds cleanly when resized and keeps long action descriptions reviewable. */
final class StudioConfirmScreen extends Screen implements ArchitectPanelLayout {
    private final Consumer<Boolean> answer;private final Component message,yes,no;private int page;
    StudioConfirmScreen(Consumer<Boolean> answer,Component title,Component message){this(answer,title,message,net.minecraft.network.chat.CommonComponents.GUI_YES,net.minecraft.network.chat.CommonComponents.GUI_NO);}
    StudioConfirmScreen(Consumer<Boolean> answer,Component title,Component message,Component yes,Component no){super(title);this.answer=answer;this.message=message;this.yes=yes;this.no=no;}
    private int span(){return Math.min(420,width-24);}
    private int perPage(){return Math.max(1,(height-100)/10);}
    private java.util.List<net.minecraft.util.FormattedCharSequence> lines(){return font.split(message,span());}
    private int pages(){return Math.max(1,(lines().size()+perPage()-1)/perPage());}
    @Override protected void init(){int span=span(),left=(width-span)/2,half=(span-8)/2;page=Math.min(page,pages()-1);
        if(pages()>1){addRenderableWidget(Button.builder(Component.literal("Previous page"),b->{page=Math.max(0,page-1);rebuildWidgets();}).bounds(left,height-52,half,20).build()).active=page>0;
        addRenderableWidget(Button.builder(Component.literal("Next page"),b->{page=Math.min(pages()-1,page+1);rebuildWidgets();}).bounds(left+half+8,height-52,half,20).build()).active=page+1<pages();}
        addRenderableWidget(Button.builder(yes,b->answer.accept(true)).bounds(left,height-26,half,20).build());
        addRenderableWidget(Button.builder(no,b->answer.accept(false)).bounds(left+half+8,height-26,half,20).build());
    }
    @Override public void onClose(){answer.accept(false);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void extractPanels(GuiGraphicsExtractor g){int left=(width-span())/2;ArchitectTheme.panel(g,left-10,29,left+span()+10,Math.min(height-65,48+Math.min(perPage(),lines().size())*10),ArchitectTheme.PANEL);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float tick){super.extractRenderState(g,x,y,tick);int left=(width-span())/2;var heading=font.split(title,span());for(int i=0;i<Math.min(2,heading.size());i++)g.text(font,heading.get(i),left,8+i*10,0xFFF1E7CE);var lines=lines();for(int i=0;i<perPage()&&page*perPage()+i<lines.size();i++)g.text(font,lines.get(page*perPage()+i),left,38+i*10,0xFFEAEAEA);if(pages()>1)g.text(font,(page+1)+" / "+pages(),left,height-65,0xFFCCCCCC);}
}
