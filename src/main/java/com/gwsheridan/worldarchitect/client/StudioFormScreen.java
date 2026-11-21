package com.gwsheridan.worldarchitect.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

final class StudioFormScreen extends Screen {
    record Field(String label, String value) {}
    private final Screen parent;
    private final String help;
    private final List<Field> fields;
    private final List<String> values;
    private final Function<List<String>, String> validate;
    private final Consumer<List<String>> save;
    private Button apply;
    private String error = "";
    StudioFormScreen(Screen parent, String title, String help, List<Field> fields, Function<List<String>, String> validate, Consumer<List<String>> save) {
        super(Component.literal(title)); this.parent=parent;this.help=help;this.fields=List.copyOf(fields);this.validate=validate;this.save=save;
        values=new ArrayList<>(fields.stream().map(Field::value).toList());
    }
    @Override protected void init() {
        int span=Math.min(420,width-24),left=(width-span)/2,half=(span-8)/2,first=height<260?58:68,row=height<260?26:34;
        apply=addRenderableWidget(Button.builder(Component.literal("Apply"),b->{try{error="";save.accept(List.copyOf(values));if(error.isEmpty())onClose();}catch(RuntimeException e){showError(e.getMessage()==null?"Unable to apply these values.":e.getMessage());}}).bounds(left,height-26,half,20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL,b->onClose()).bounds(left+half+8,height-26,half,20).build());
        for(int i=0;i<fields.size();i++){
            int index=i;var field=fields.get(i);var box=addRenderableWidget(new EditBox(font,left+i%2*(half+8),first+i/2*row,half,18,Component.literal(field.label())));
            box.setMaxLength(1500);box.setValue(values.get(i));box.setResponder(value->{values.set(index,value);refresh();});
        }
        refresh();
    }
    private void refresh(){try{error=validate.apply(List.copyOf(values));}catch(RuntimeException e){error=e.getMessage()==null?"Check the entered values.":e.getMessage();}apply.active=error.isEmpty();}
    void showError(String message){error=message;}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float tick){
        super.extractRenderState(graphics,mouseX,mouseY,tick);int span=Math.min(420,width-24),left=(width-span)/2,half=(span-8)/2,first=height<260?58:68,row=height<260?26:34;
        graphics.centeredText(font,title,width/2,8,0xFFF1E7CE);var lines=font.split(Component.literal(help),span);
        for(int i=0;i<Math.min(2,lines.size());i++)graphics.text(font,lines.get(i),left,24+i*9,0xFFCCCCCC);
        for(int i=0;i<fields.size();i++)graphics.text(font,font.plainSubstrByWidth(fields.get(i).label(),half),left+i%2*(half+8),first+i/2*row-10,0xFFEAEAEA);
        var errors=font.split(Component.literal(error),span);for(int i=0;i<Math.min(2,errors.size());i++)graphics.text(font,errors.get(i),left,height-50+i*9,0xFFFFAAAA);
    }
}
