package com.gwsheridan.worldarchitect.client;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** The editable model stays on the server; this screen holds only its bounded view. */
final class AssemblyStudioScreen extends Screen {
    private final CompoundTag data; private final List<CompoundTag> modules; private final VoxelPreview preview;
    private int selected;
    AssemblyStudioScreen(CompoundTag data){
        super(Component.literal("Assembly Studio"));this.data=data.copy();
        modules=data.getListOrEmpty("modules").stream().filter(CompoundTag.class::isInstance).map(CompoundTag.class::cast).toList();selected=Math.clamp(data.getIntOr("selected",0),0,Math.max(0,modules.size()-1));
        preview=new VoxelPreview(data.getIntArray("cells").orElse(new int[0]),data.getIntArray("size").orElse(new int[]{1,1,1}));
    }
    @Override protected void init(){
        int span=Math.min(480,width-24),left=(width-span)/2,fourth=(span-12)/4,third=(span-8)/3,half=(span-4)/2;
        add("New draft",left,27,fourth,()->discard(this::create));
        add("Open draft",left+fourth+4,27,fourth,()->discard(this::open));
        add("Save draft",left+2*(fourth+4),27,fourth,()->send("save","")).active=active()&&data.getBooleanOr("dirty",false);
        add("Settings",left+3*(fourth+4),27,fourth,this::settings).active=active();
        add("<",left,51,22,()->{selected=Math.max(0,selected-1);rebuildWidgets();}).active=selected>0;
        add(">",left+26,51,22,()->{selected=Math.min(modules.size()-1,selected+1);rebuildWidgets();}).active=selected+1<modules.size();
        String moduleLabel=modules.isEmpty()?"No modules":(selected+1)+"/"+modules.size()+" · "+modules.get(selected).getStringOr("name","");
        add(moduleLabel,left+55,51,span-174,this::details).active=!modules.isEmpty();
        add("Pools",left+span-112,51,60,()->send("pool_view","")).active=active();
        add("↶",left+span-48,51,22,()->preview.orbit(-1));add("↷",left+span-22,51,22,()->preview.orbit(1));
        add("Add module",left,height-102,third,this::addModule).active=active()&&modules.size()<16;
        add("Edit module",left+third+4,height-102,third,()->editModule(null)).active=!modules.isEmpty();
        add("Remove",left+2*(third+4),height-102,third,()->send("remove",""+selected)).active=!modules.isEmpty();
        add("Export datapack",left,height-78,half,()->export(false)).active=data.getBooleanOr("valid",false);
        add("Export preset",left+half+4,height-78,half,()->export(true)).active=data.getBooleanOr("valid",false)&&data.getBooleanOr("can_bundle",false);
        add(data.getBooleanOr("recovery_pending",false)?"Recovery available":"History / organize",left,height-26,half,()->{var view=data.copy();view.putInt("selected",selected);minecraft.gui.setScreen(new AssemblyHistoryScreen(view));}).active=active();
        add("Close",left+half+4,height-26,half,this::onClose);
    }
    private boolean active(){return data.getBooleanOr("active",false);}
    private void details(){var module=modules.get(selected);var lines=new ArrayList<String>();lines.add("Assembly: "+data.getStringOr("id",""));lines.add("Module: "+module.getStringOr("name",""));lines.add("Source: "+module.getStringOr("source","")+" · captured revision "+module.getIntOr("snapshot_revision",0));lines.add("Offset: "+Arrays.toString(module.getIntArray("offset").orElse(new int[3])));lines.add("Size: "+Arrays.toString(module.getIntArray("size").orElse(new int[3])));lines.add("Rotation: "+module.getStringOr("rotation","")+" · mirror: "+module.getStringOr("mirror",""));lines.add("Placement rules: "+module.getStringOr("rules",""));lines.add("Dependencies: "+data.getStringOr("dependencies","").replaceFirst("^$","Minecraft only"));lines.add("Editing a source template later does not replace this module's saved snapshot.");minecraft.gui.setScreen(new StudioReportScreen(this,"Module details",lines));}
    private Button add(String text,int x,int y,int width,Runnable action){return addRenderableWidget(Button.builder(Component.literal(text),b->action.run()).bounds(x,y,width,20).build());}
    private void command(String text){if(minecraft.getConnection()!=null)minecraft.getConnection().sendCommand(text);}
    private void send(String operation,String fields){command("wa_assembly "+operation+" "+data.getStringOr("token","")+" "+data.getIntOr("serial",0)+(fields.isEmpty()?"":" "+fields));}
    private static List<String> strings(CompoundTag data,String key){return data.getListOrEmpty(key).stream().map(t->t.asString().orElse("")).filter(s->!s.isEmpty()).toList();}
    private void form(String title,String help,List<StudioFormScreen.Field> fields,java.util.function.Function<List<String>,String> validator,Consumer<List<String>> save){minecraft.gui.setScreen(new StudioFormScreen(this,title,help,fields,validator,save));}
    private void create(){form("New assembly","Drafts are world-local. Existing saved versions are preserved.",List.of(new StudioFormScreen.Field("Assembly ID","builds:village"),new StudioFormScreen.Field("Biome ID","minecraft:plains")),values->{for(String value:values)Identifier.parse(value);return "";},v->command("wa_assembly new "+v.get(0).strip()+" "+v.get(1).strip()));}
    private void open(){minecraft.gui.setScreen(new StudioChoiceScreen(this,"Saved assemblies",strings(data,"drafts"),id->command("wa_assembly open "+id)));}
    private void addModule(){
        var ids=strings(data,"library");
        if(ids.isEmpty())editModule("builds:template");else minecraft.gui.setScreen(new StudioChoiceScreen(this,"Choose saved template",ids,this::editModule));
    }
    private void editModule(String added){
        var current=added==null?modules.get(selected):new CompoundTag();var offset=current.getIntArray("offset").orElse(new int[]{0,0,0});
        form(added==null?"Edit module":"Add module","Offsets are local coordinates. Rotations also transform block states and markers.",List.of(
                new StudioFormScreen.Field("Template ID",added==null?current.getStringOr("source",""):added),
                new StudioFormScreen.Field("Module name",added==null?current.getStringOr("name",""):"module_"+(modules.size()+1)),
                new StudioFormScreen.Field("Offset X",Integer.toString(offset[0])),new StudioFormScreen.Field("Offset Y",Integer.toString(offset[1])),new StudioFormScreen.Field("Offset Z",Integer.toString(offset[2])),
                new StudioFormScreen.Field("Rotation · none/cw90/cw180/ccw90",current.getStringOr("rotation","none")),new StudioFormScreen.Field("Mirror · none/left_right/front_back",current.getStringOr("mirror","none"))),v->{
                    Identifier.parse(v.get(0));if(!v.get(1).matches("[a-z0-9_-]{1,32}"))return "Use a lowercase module name.";
                    for(int i=2;i<5;i++){int n=Integer.parseInt(v.get(i));if(n<0||n>95)return "Offsets must be 0–95.";}
                    if(!List.of("none","cw90","cw180","ccw90").contains(v.get(5))||!List.of("none","left_right","front_back").contains(v.get(6)))return "Check rotation and mirror.";return "";
                },v->send(added==null?"edit":"add",(added==null?selected+" ":"")+String.join(" ",v.stream().map(String::strip).toList())));
    }
    private void settings(){form("Assembly settings","All modules must pass their own rules. Random rotation applies to the entire assembly.",List.of(
            new StudioFormScreen.Field("Biome ID",data.getStringOr("biome","minecraft:plains")),new StudioFormScreen.Field("Height offset · -64 to 64",""+data.getIntOr("y_offset",0)),
            new StudioFormScreen.Field("Maximum slope · 0 to 32",""+data.getIntOr("slope",4)),new StudioFormScreen.Field("Random assembly rotation · true/false",""+data.getBooleanOr("random",true))),v->{Identifier.parse(v.get(0));int y=Integer.parseInt(v.get(1)),s=Integer.parseInt(v.get(2));return y< -64||y>64||s<0||s>32||!List.of("true","false").contains(v.get(3))?"Check the assembly settings.":"";},v->send("settings",String.join(" ",v)));}
    private void export(boolean bundle){
        var fields=new ArrayList<StudioFormScreen.Field>();fields.add(new StudioFormScreen.Field("New export ID",data.getStringOr("id","builds:village")));
        if(bundle){fields.add(new StudioFormScreen.Field("Dimension ID","minecraft:overworld"));fields.add(new StudioFormScreen.Field("Preset release","1.0.0"));fields.add(new StudioFormScreen.Field("Seed for new worlds","18"));}
        form(bundle?"Export assembly preset":"Export assembly datapack",bundle?"Exports create a new flat-stone dimension. Other profiles are retained; the End keeps its native arena.":"The ZIP is written under generated/worldarchitect/assembly-exports. Install it in a new world or pack.",fields,v->{Identifier.parse(v.get(0));if(bundle){Identifier.parse(v.get(1));Long.parseLong(v.get(3));if(!v.get(2).matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,39}"))return "Choose a simple preset release.";}return "";},v->send(bundle?"bundle":"datapack",String.join(" ",v)));
    }
    private void discard(Runnable next){
        if(!data.getBooleanOr("dirty",false)&&!data.getBooleanOr("recovery_pending",false)){next.run();return;}
        minecraft.gui.setScreen(new StudioConfirmScreen(answer->{if(answer)next.run();else minecraft.gui.setScreen(this);},Component.literal("Discard assembly changes?"),Component.literal("Unsaved module edits and pending recovery will be discarded. Saved drafts and exports remain available."),Component.literal("Discard changes"),Component.literal("Keep editing")));
    }
    @Override public void onClose(){discard(()->{if(active())send("close","");minecraft.gui.setScreen(null);});}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float tick){
        super.extractRenderState(g,mx,my,tick);int span=Math.min(480,width-24),left=(width-span)/2,top=76,bottom=height-112;
        g.centeredText(font,title,width/2,7,0xFFF1E7CE);
        g.enableScissor(left,top,left+span,bottom);preview.draw(g,left,top,span,bottom-top);
        for(var tag:data.getListOrEmpty("markers"))if(tag instanceof CompoundTag marker){var p=marker.getIntArray("pos").orElse(new int[3]);var dot=preview.project(p[0]+.5,p[1]+.5,p[2]+.5,left,top,span,bottom-top);g.fill(dot[0]-2,dot[1]-2,dot[0]+3,dot[1]+3,0xFFFFD560);if(modules.size()<5)g.text(font,marker.getStringOr("name","")+" · "+marker.getStringOr("facing",""),dot[0]+4,dot[1],0xFFFFD560);}
        g.disableScissor();String status=data.getStringOr("status","Create a draft, then add saved templates. Limits: 16 modules, 96³ bounds, 65,536 cells.");
        var lines=font.split(Component.literal(status),span);for(int i=0;i<Math.min(2,lines.size());i++)g.text(font,lines.get(i),left,height-52+i*9,data.getBooleanOr("valid",false)?0xFFACEAB8:0xFFFFC080);
    }
}
