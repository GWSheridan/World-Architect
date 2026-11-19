package com.gwsheridan.worldarchitect.client;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.*;

/** A bounded, searchable selection list shared by the authoring tools. */
final class StudioChoiceScreen extends Screen {
    private final Screen parent; private final List<String> choices; private final Consumer<String> choose;
    private String query="",selected; private ChoiceList rows; private Button open;
    StudioChoiceScreen(Screen parent,String title,List<String> choices,Consumer<String> choose){super(Component.literal(title));this.parent=parent;this.choices=List.copyOf(choices);this.choose=choose;}
    @Override protected void init(){
        int span=Math.min(480,width-24),left=(width-span)/2;
        var search=addRenderableWidget(new EditBox(font,left,28,span,20,Component.literal("Search")));search.setMaxLength(170);search.setValue(query);
        rows=addRenderableWidget(new ChoiceList(span,height-86));rows.setX(left);
        open=addRenderableWidget(Button.builder(Component.literal("Select"),b->{if(selected!=null)choose.accept(selected);}).bounds(left,height-26,(span-8)/2,20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL,b->onClose()).bounds(left+(span+8)/2,height-26,(span-8)/2,20).build());
        search.setResponder(s->{query=s;refresh();});refresh();setInitialFocus(search);
    }
    private void refresh(){var before=selected;var matches=choices.stream().filter(s->s.toLowerCase(Locale.ROOT).contains(query.strip().toLowerCase(Locale.ROOT))).map(Row::new).toList();rows.replaceEntries(matches);rows.setSelected(matches.stream().filter(r->r.value.equals(before)).findFirst().orElse(null));}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float tick){super.extractRenderState(g,x,y,tick);g.centeredText(font,title,width/2,9,0xFFF1E7CE);if(rows.children().isEmpty())g.centeredText(font,Component.literal("No matching entries"),width/2,62,0xFFCCCCCC);}
    private final class ChoiceList extends ObjectSelectionList<Row>{
        ChoiceList(int width,int height){super(StudioChoiceScreen.this.minecraft,width,height,54,24);}
        @Override public int getRowWidth(){return getWidth()-16;}
        @Override public void setSelected(Row row){super.setSelected(row);selected=row==null?null:row.value;open.active=row!=null;}
    }
    private final class Row extends ObjectSelectionList.Entry<Row>{
        final String value;Row(String value){this.value=value;}
        @Override public Component getNarration(){return Component.literal(value);}
        @Override public void extractContent(GuiGraphicsExtractor g,int x,int y,boolean hover,float tick){g.text(font,font.plainSubstrByWidth(value,getWidth()-8),getX()+4,getY()+7,0xFFFFFFFF);}
    }
}
