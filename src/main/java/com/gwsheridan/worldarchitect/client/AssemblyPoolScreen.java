package com.gwsheridan.worldarchitect.client;

import com.gwsheridan.worldarchitect.structure.ProceduralPool;
import java.util.*;
import java.util.function.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;

final class AssemblyPoolScreen extends Screen {
    private final CompoundTag data; private final Optional<ProceduralPool> pool; private final List<String> modules;
    AssemblyPoolScreen(CompoundTag data){super(Component.literal("Assembly Studio · Procedural pools"));this.data=data.copy();pool=data.getCompound("pool").map(tag->ProceduralPool.CODEC.parse(NbtOps.INSTANCE,tag).getOrThrow());modules=data.getListOrEmpty("modules").stream().map(CompoundTag.class::cast).map(m->m.getStringOr("name","")).toList();}
    private void send(String op,String args){if(minecraft.getConnection()!=null)minecraft.getConnection().sendCommand("wa_assembly "+op+" "+data.getStringOr("token","")+" "+data.getIntOr("serial",0)+(args.isEmpty()?"":" "+args));}
    @Override protected void init(){int span=Math.min(420,width-24),left=(width-span)/2,half=(span-8)/2,top=height<260?48:64;
        add(pool.isPresent()?"Pool settings":"Enable pool",left,top,span,this::settings).active=!modules.isEmpty();
        add("Add connection",left,top+24,half,()->connection(null)).active=pool.isPresent();add("Edit connection",left+half+8,top+24,half,this::editConnection).active=pool.map(p->!p.ports().isEmpty()).orElse(false);
        add("Add choice",left,top+48,half,()->choice(null)).active=pool.isPresent();add("Edit choice",left+half+8,top+48,half,this::editChoice).active=pool.map(p->!p.choices().isEmpty()).orElse(false);
        add("Remove connection",left,top+72,half,this::removeConnection).active=pool.map(p->!p.ports().isEmpty()).orElse(false);add("Remove choice",left+half+8,top+72,half,this::removeChoice).active=pool.map(p->!p.choices().isEmpty()).orElse(false);
        add("Sample layout seed",left,top+96,half,()->form("Sample layout seed","Sample seeds are local previews. Generated layouts also incorporate world seed and chunk coordinates.",List.of(f("Layout seed",data.getLongOr("preview_seed",0))),v->{Long.parseLong(v.get(0));return "";},v->send("preview_seed",v.get(0))));
        add("Use static assembly",left+half+8,top+96,half,()->send("static","")).active=pool.isPresent();
        add("Back to assembly",left,height-26,span,this::onClose);
    }
    private Button add(String text,int x,int y,int width,Runnable action){return addRenderableWidget(Button.builder(Component.literal(text),b->action.run()).bounds(x,y,width,20).build());}
    private StudioFormScreen.Field f(String label,Object value){return new StudioFormScreen.Field(label,""+value);}
    private void form(String title,String help,List<StudioFormScreen.Field> fields,Function<List<String>,String> validate,Consumer<List<String>> save){minecraft.gui.setScreen(new StudioFormScreen(this,title,help,fields,v->{for(String s:v)if(s.isBlank()||s.chars().anyMatch(Character::isWhitespace))return "Enter one value per field.";return validate.apply(v);},save));}
    private boolean name(String s){return s.matches("[a-z0-9_-]{1,32}");}
    private String first(){return modules.isEmpty()?"module_1":modules.getFirst();}
    private void settings(){var p=pool.orElse(new ProceduralPool(first(),8,128,0,List.of(),List.of()));form("Procedural pool settings","Root names a source module. Complete layouts require matching connections and end caps.",List.of(f("Root module",p.root()),f("Maximum modules · 2–16",p.maxModules()),f("Attempt limit · 1–256",p.attempts()),f("Seed offset",p.salt())),v->{int max=Integer.parseInt(v.get(1)),attempts=Integer.parseInt(v.get(2));Long.parseLong(v.get(3));return modules.contains(v.get(0))&&max>=2&&max<=16&&attempts>=1&&attempts<=256?"":"Choose an existing module and valid budgets.";},v->send("pool",String.join(" ",v)));}
    private void connection(ProceduralPool.Port p){form("Add/update connection","Positions use the transformed source template. A matching module/name replaces that connection.",List.of(f("Module",p==null?first():p.module()),f("Connection name",p==null?"door":p.name()),f("Local X",p==null?0:p.position().getX()),f("Local Y",p==null?1:p.position().getY()),f("Local Z",p==null?1:p.position().getZ()),f("Facing · north/south/east/west",p==null?"west":p.facing().getSerializedName()),f("Target pool",p==null?"rooms":p.pool()),f("Channel",p==null?"default":p.channel())),v->{for(int i:new int[]{0,1,6,7})if(!name(v.get(i)))return "Use simple lowercase names.";if(!modules.contains(v.get(0)))return "Choose an existing source module.";for(int i=2;i<=4;i++){int n=Integer.parseInt(v.get(i));if(n<0||n>31)return "Source coordinates must be 0–31.";}return List.of("north","south","east","west").contains(v.get(5))?"":"Choose a horizontal facing.";},v->send("connection",String.join(" ",v)));}
    private List<String> portLabels(){return pool.orElseThrow().ports().stream().map(p->p.module()+"/"+p.name()).toList();}
    private void editConnection(){minecraft.gui.setScreen(new StudioChoiceScreen(this,"Edit connection",portLabels(),v->{var a=v.split("/");connection(pool.orElseThrow().ports().stream().filter(p->p.module().equals(a[0])&&p.name().equals(a[1])).findFirst().orElseThrow());}));}
    private void removeConnection(){minecraft.gui.setScreen(new StudioChoiceScreen(this,"Remove connection",portLabels(),v->send("remove_connection",v.replace('/',' '))));}
    private void choice(ProceduralPool.Choice c){form("Add/update pool choice","Regular choices extend a layout. End caps have one connection. Matching pool/module/role replaces its weight.",List.of(f("Pool",c==null?"rooms":c.pool()),f("Module",c==null?first():c.module()),f("Weight · 1–100",c==null?1:c.weight()),f("End cap · true/false",c!=null&&c.endcap())),v->{int w=Integer.parseInt(v.get(2));return name(v.get(0))&&modules.contains(v.get(1))&&w>=1&&w<=100&&List.of("true","false").contains(v.get(3))?"":"Check pool, module, weight and role.";},v->send("choice",String.join(" ",v)));}
    private List<String> choices(){var p=pool.orElseThrow();var labels=new ArrayList<String>();for(int i=0;i<p.choices().size();i++){var c=p.choices().get(i);labels.add(i+" · "+c.pool()+" / "+c.module()+" · "+(c.endcap()?"end cap":"regular")+" · weight "+c.weight());}return labels;}
    private void editChoice(){minecraft.gui.setScreen(new StudioChoiceScreen(this,"Edit pool choice",choices(),v->choice(pool.orElseThrow().choices().get(Integer.parseInt(v.split(" · ")[0])))));}
    private void removeChoice(){minecraft.gui.setScreen(new StudioChoiceScreen(this,"Remove pool choice",choices(),v->send("remove_choice",v.split(" · ")[0])));}
    @Override public void onClose(){send("pool_back","");}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float tick){super.extractRenderState(g,x,y,tick);g.centeredText(font,title,width/2,8,0xFFF1E7CE);g.centeredText(font,Component.literal(pool.map(p->p.ports().size()+" connections · "+p.choices().size()+" choices").orElse("Static assembly · enable a pool to begin")),width/2,26,0xFFCCCCCC);}
}
