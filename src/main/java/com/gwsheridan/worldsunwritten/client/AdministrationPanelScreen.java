package com.gwsheridan.worldsunwritten.client;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Every mutation requests a server-owned confirmation; the screen never edits saved data. */
final class AdministrationPanelScreen extends Screen {
    private enum Tab {ISLANDS,TEAMS,SPAWNS,PROTECTION;Component label(){return Component.literal(switch(this){case ISLANDS->"Islands";case TEAMS->"Teams";case SPAWNS->"Spawns";case PROTECTION->"Protection";});}}
    private static Tab preferred=Tab.ISLANDS;
    private record Info(String title,String detail,String key){}
    private final CompoundTag data;private final boolean administrator;private Tab tab;private Info selected;private InfoList rows;
    AdministrationPanelScreen(CompoundTag data){super(Component.literal("Worlds Unwritten · Administration"));this.data=data.copy();administrator=data.getBooleanOr("administrator",false);tab=administrator?preferred:Tab.ISLANDS;}
    private void command(String text){if(minecraft.getConnection()!=null)minecraft.getConnection().sendCommand(text);}
    private void request(String text){command("wa_admin stage "+data.getStringOr("token","")+" "+text);}
    @Override protected void init(){
        int span=Math.min(480,width-24),left=(width-span)/2,half=(span-4)/2;
        if(administrator)addRenderableWidget(CycleButton.builder(Tab::label,tab).withValues(Tab.values()).displayOnlyValue().create(left,28,120,20,Component.literal("Section"),(b,value)->{tab=value;preferred=value;selected=null;rebuildWidgets();}));
        else add("Your island",left,28,120,()->{}).active=false;
        add("<",left+126,28,22,()->command("wa_admin page "+(data.getIntOr("page",0)-1))).active=administrator&&data.getIntOr("page",0)>0;
        add(">",left+152,28,22,()->command("wa_admin page "+(data.getIntOr("page",0)+1))).active=administrator&&data.getIntOr("page",0)+1<data.getIntOr("pages",1);
        add("Refresh",left+span-72,28,72,()->command("wa_admin page "+data.getIntOr("page",0)));
        rows=addRenderableWidget(new InfoList(span,height-(tab==Tab.PROTECTION?204:178)));rows.setX(left);var items=items().stream().map(Row::new).toList();rows.replaceEntries(items);rows.setSelected(items.isEmpty()?null:items.getFirst());
        if(!administrator){add("Home",left,height-52,span,()->{command("worldsunwritten island home");minecraft.gui.setScreen(null);}).active=data.getBooleanOr("islands",false);}
        else switch(tab){
            case ISLANDS->{add("Reassign island",left,height-78,half,this::reassign).active=data.getBooleanOr("islands",false);add("Swap assignments",left+half+4,height-78,half,this::swap).active=data.getBooleanOr("islands",false);}
            case TEAMS->{add("Create team",left,height-78,half,this::createTeam);add("Membership",left+half+4,height-78,half,this::membership);add("Assign team island",left,height-52,span,this::assignTeam).active=data.getBooleanOr("islands",false);}
            case SPAWNS->{add("Set spawn policy",left,height-78,half,this::spawn);add("Clear override",left+half+4,height-78,half,this::clearSpawn);add("View policies",left,height-52,span,()->minecraft.gui.setScreen(new StudioReportScreen(this,"Current spawn policies",strings("policies"))));}
            case PROTECTION->{
                add("Container types",left,height-104,span,this::container).active=data.getBooleanOr("islands",false);
                String[] keys={"breaking","placement","containers","shared"};for(int i=0;i<keys.length;i++){String key=keys[i];add(key+": "+(data.getBooleanOr(key,false)?"on":"off"),left+(i%2)*(half+4),height-78+(i/2)*26,half,()->request("worldsunwritten island protection "+key+" "+!data.getBooleanOr(key,false))).active=data.getBooleanOr("islands",false);}
            }
        }
        add("Close",left,height-26,span,this::onClose);
    }
    private List<Info> items(){
        var rows=new ArrayList<Info>();if(!administrator){rows.add(new Info("Personal island: "+data.getIntOr("personal",0),"Team: "+data.getStringOr("own_team","none")+" · team island "+data.getIntOr("team_slot",0),""));return rows;}
        switch(tab){
            case ISLANDS->{for(var value:data.getListOrEmpty("players"))if(value instanceof CompoundTag p)rows.add(new Info(p.getStringOr("name","")+" · island "+p.getIntOr("slot",0),p.getStringOr("id",""),p.getStringOr("id","")));}
            case TEAMS->{for(var value:data.getListOrEmpty("teams"))if(value instanceof CompoundTag t)rows.add(new Info(t.getStringOr("name","")+" · island "+t.getIntOr("slot",0),t.getIntOr("members",0)+" members",t.getStringOr("name","")));}
            case SPAWNS->{for(var value:data.getListOrEmpty("policies")){String line=value.asString().orElse("");int split=line.indexOf(": ");rows.add(new Info(split<0?line:line.substring(0,split),split<0?"":line.substring(split+2),""));}}
            case PROTECTION->{for(var value:data.getListOrEmpty("container_blocks")){String id=value.asString().orElse("");rows.add(new Info(id,"Protected container type",id));}}
        }
        if(rows.isEmpty())rows.add(new Info("No entries on this page","Refresh after changing server state.",""));return rows;
    }
    private Button add(String title,int x,int y,int width,Runnable action){return addRenderableWidget(Button.builder(Component.literal(title),b->action.run()).bounds(x,y,width,20).build());}
    private void form(String title,String help,List<StudioFormScreen.Field> fields,java.util.function.Function<List<String>,String> action){minecraft.gui.setScreen(new StudioFormScreen(this,title,help,fields,v->{for(String value:v)if(value.isBlank()||value.codePoints().anyMatch(c->Character.isWhitespace(c)||Character.isSpaceChar(c)||Character.isISOControl(c)))return "Enter one non-empty value per field.";try { com.gwsheridan.worldsunwritten.skyblock.AdministrationAction.parse(action.apply(v), Set.copyOf(strings("online"))); return ""; } catch (RuntimeException e) { return e.getMessage()==null?"Check the action fields.":e.getMessage(); }},v->request(action.apply(v.stream().map(String::strip).toList()))));}
    private String player(){return selected!=null&&!selected.key.isEmpty()?selected.key:strings("players").stream().findFirst().orElse("PlayerName");}
    private List<String> strings(String key){return data.getListOrEmpty(key).stream().map(t->t.asString().orElse("")).filter(s->!s.isEmpty()).toList();}
    private int free(){return Math.max(1,data.getIntOr("first_free",1));}
    private void reassign(){form("Reassign island","Choose an online name or saved UUID. Blocks and builds stay in place.",List.of(new StudioFormScreen.Field("Player name or UUID",player()),new StudioFormScreen.Field("Available slot",""+free())),v->"worldsunwritten island reassign "+String.join(" ",v));}
    private void swap(){
        var ids=data.getListOrEmpty("players").stream().filter(CompoundTag.class::isInstance).map(CompoundTag.class::cast).map(p->p.getStringOr("id","")).toList();
        form("Swap assignments","Both players must have existing assignments. The server commits the exchange atomically.",List.of(new StudioFormScreen.Field("First player or UUID",player()),new StudioFormScreen.Field("Second player or UUID",ids.size()>1?ids.get(1):"SecondPlayer")),v->"worldsunwritten island swap "+String.join(" ",v));
    }
    private String team(){return selected!=null&&!selected.key.isEmpty()?selected.key:"builders";}
    private void createTeam(){form("Create scoreboard team","Team membership and island reservations remain separate controls.",List.of(new StudioFormScreen.Field("Team name","builders")),v->"team add "+v.getFirst());}
    private void assignTeam(){form("Assign team island","The team must exist and the slot must be available. Existing personal assignments remain allocated.",List.of(new StudioFormScreen.Field("Team name",team()),new StudioFormScreen.Field("Available island slot",""+free())),v->"worldsunwritten island team assign "+String.join(" ",v));}
    private void membership(){minecraft.gui.setScreen(new StudioChoiceScreen(this,"Team membership",List.of("Join team","Leave team"),choice->{
        var fields=new ArrayList<StudioFormScreen.Field>();fields.add(new StudioFormScreen.Field("Online player name",strings("online").stream().findFirst().orElse("PlayerName")));if(choice.equals("Join team"))fields.add(new StudioFormScreen.Field("Team name",team()));
        form(choice,"Protection access is checked again when membership changes, including open containers.",fields,v->choice.equals("Join team")?"team join "+v.get(1)+" "+v.get(0):"team leave "+v.getFirst());
    }));}
    private void container(){form("Protected container types","Add or remove a registered block ID. The server rechecks open container access.",List.of(new StudioFormScreen.Field("Action · add/remove","add"),new StudioFormScreen.Field("Block ID",selected!=null&&!selected.key.isEmpty()?selected.key:"minecraft:chest")),v->"worldsunwritten island protection container "+String.join(" ",v));}
    private void spawn(){minecraft.gui.setScreen(new StudioChoiceScreen(this,"Spawn policy kind",List.of("fixed","island","team","marker","generated"),kind->{
        var fields=new ArrayList<StudioFormScreen.Field>();fields.add(new StudioFormScreen.Field("Event · initial/respawn/portal","respawn"));fields.add(new StudioFormScreen.Field("Dimension ID","minecraft:overworld"));
        if(kind.equals("marker")||kind.equals("generated"))fields.add(new StudioFormScreen.Field("Structure or template ID","builds:hall"));
        if(kind.equals("marker"))fields.add(new StudioFormScreen.Field("Marker name","entry"));
        if(!kind.equals("island")&&!kind.equals("team")){fields.add(new StudioFormScreen.Field("X","0"));fields.add(new StudioFormScreen.Field("Y","65"));fields.add(new StudioFormScreen.Field("Z","0"));}
        if(kind.equals("marker")){fields.add(new StudioFormScreen.Field("Rotation","none"));fields.add(new StudioFormScreen.Field("Mirror","none"));}
        if(kind.equals("generated")){fields.add(new StudioFormScreen.Field("Module name","main"));fields.add(new StudioFormScreen.Field("Marker name","entry"));}
        form("Set "+kind+" spawn","Destinations must be safe. Personal respawn points and bundle locks keep their existing precedence.",fields,v->"worldsunwritten spawn set "+v.get(0)+" "+kind+" "+String.join(" ",v.subList(1,v.size())));
    }));}
    private void clearSpawn(){form("Clear spawn override","Clear an administrative policy and return to the existing fallback behaviour.",List.of(new StudioFormScreen.Field("Event · initial/respawn/portal","respawn"),new StudioFormScreen.Field("Dimension ID","minecraft:overworld")),v->"worldsunwritten spawn clear "+String.join(" ",v));}
    @Override public void onClose(){if(administrator)command("wa_admin close "+data.getStringOr("token",""));minecraft.gui.setScreen(null);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float tick){
        super.extractRenderState(g,mx,my,tick);int span=Math.min(480,width-24),left=(width-span)/2;g.centeredText(font,title,width/2,8,0xFFF1E7CE);
        String summary=data.getBooleanOr("islands",false)?data.getIntOr("used",0)+" / "+data.getIntOr("capacity",0)+" personal slots reserved · page "+(data.getIntOr("page",0)+1)+" / "+data.getIntOr("pages",1):"This world has no personal island allocations.";
        g.text(font,font.plainSubstrByWidth(summary,span),left,56,0xFFCCCCCC);
        String status=data.getStringOr("status","");if(status.isEmpty())status=administrator?"Changes require confirmation and a fresh server check.":"Home uses your current safe island spawn.";
        g.text(font,font.plainSubstrByWidth(status,span),left,height-(tab==Tab.PROTECTION?117:94),0xFFEAEAEA);
    }
    private final class InfoList extends ObjectSelectionList<Row>{
        InfoList(int width,int height){super(AdministrationPanelScreen.this.minecraft,width,Math.max(40,height),76,32);}
        @Override public int getRowWidth(){return getWidth()-16;}
        @Override public void setSelected(Row row){super.setSelected(row);selected=row==null?null:row.info;}
    }
    private final class Row extends ObjectSelectionList.Entry<Row>{
        final Info info;Row(Info info){this.info=info;}
        @Override public Component getNarration(){return Component.literal(info.title+" · "+info.detail);}
        @Override public void extractContent(GuiGraphicsExtractor g,int x,int y,boolean hover,float tick){g.text(font,font.plainSubstrByWidth(info.title,getWidth()-8),getX()+4,getY()+4,0xFFFFFFFF);g.text(font,font.plainSubstrByWidth(info.detail,getWidth()-8),getX()+4,getY()+17,0xFFCCCCCC);}
    }
}
