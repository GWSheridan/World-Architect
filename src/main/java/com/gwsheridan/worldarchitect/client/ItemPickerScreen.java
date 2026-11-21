package com.gwsheridan.worldarchitect.client;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

final class ItemPickerScreen extends Screen {
    private final Screen parent;private final Consumer<ItemStack> save;private final List<ItemStack> items;
    private ItemStack selected=ItemStack.EMPTY;private String query="";private Rows rows;private Button use;
    ItemPickerScreen(Screen parent,Consumer<ItemStack> save){super(Component.literal("Choose a starter item"));this.parent=parent;this.save=save;items=BuiltInRegistries.ITEM.stream().map(ItemStack::new).filter(s->!s.isEmpty()).sorted(Comparator.comparing(s->BuiltInRegistries.ITEM.getKey(s.getItem()).toString())).toList();}
    @Override protected void init(){int span=Math.min(500,width-24),left=(width-span)/2;
        var search=addRenderableWidget(new EditBox(font,left,30,span,20,Component.literal("Search items")));search.setHint(Component.literal("Search by name, mod or registry ID"));search.setValue(query);
        rows=addRenderableWidget(new Rows(span,height-91));rows.setX(left);
        use=addRenderableWidget(Button.builder(Component.literal("Select item"),b->{minecraft.gui.setScreen(parent);save.accept(selected.copy());}).bounds(left,height-26,(span-8)/2,20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"),b->onClose()).bounds(left+(span+8)/2,height-26,(span-8)/2,20).build());search.setResponder(s->{query=s;refresh();});refresh();setInitialFocus(search);
    }
    private void refresh(){String q=query.strip().toLowerCase(Locale.ROOT);rows.replaceEntries(items.stream().filter(s->(s.getHoverName().getString()+" "+BuiltInRegistries.ITEM.getKey(s.getItem())).toLowerCase(Locale.ROOT).contains(q)).map(Row::new).toList());use.active=false;}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float t){super.extractRenderState(g,x,y,t);g.centeredText(font,title,width/2,8,ArchitectTheme.TEXT);}
    private final class Rows extends ObjectSelectionList<Row>{Rows(int w,int h){super(ItemPickerScreen.this.minecraft,w,h,56,30);}@Override public int getRowWidth(){return getWidth()-16;}@Override public void setSelected(Row row){super.setSelected(row);selected=row==null?ItemStack.EMPTY:row.item;use.active=row!=null;}}
    private final class Row extends ObjectSelectionList.Entry<Row>{final ItemStack item;Row(ItemStack item){this.item=item;}@Override public Component getNarration(){return item.getHoverName();}@Override public void extractContent(GuiGraphicsExtractor g,int x,int y,boolean hover,float t){g.item(item,getX()+4,getY()+6);g.text(font,font.plainSubstrByWidth(item.getHoverName().getString(),getWidth()-30),getX()+25,getY()+4,ArchitectTheme.TEXT);g.text(font,font.plainSubstrByWidth(BuiltInRegistries.ITEM.getKey(item.getItem()).toString(),getWidth()-30),getX()+25,getY()+16,ArchitectTheme.MUTED);}}
}
