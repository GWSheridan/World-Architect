package com.gwsheridan.worldarchitect.client;

import com.google.gson.*;
import com.gwsheridan.worldarchitect.workbench.*;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Edits detached inputs. Export is the only server request; no control starts test servers. */
final class WorkbenchScenarioScreen extends Screen {
    @FunctionalInterface private interface Exporter{void export(String name,JsonObject document)throws IOException;}
    private static WeakReference<WorkbenchScenarioScreen> active=new WeakReference<>(null);
    private final Screen parent;
    private final String token;
    private final int serial;
    private final Exporter exporter;
    private final JsonObject binding;
    private final ClientDraftJournal journal;
    private JsonObject document;
    private int selected,left,span;
    private String status="Exported scenarios run in disposable instances through the source runner.";
    WorkbenchScenarioScreen(Screen parent,WorkbenchAuthoring.Source source,HolderLookup.Provider registries){
        this(parent,"",0,source.scenario(),(name,doc)->WorkbenchAuthoring.export(source,name,doc,registries));
    }
    WorkbenchScenarioScreen(CompoundTag view){
        this(null,view.getStringOr("token",""),view.getIntOr("serial",0),JsonParser.parseString(view.getStringOr("scenario","{}")).getAsJsonObject(),null);
    }
    private WorkbenchScenarioScreen(Screen parent,String token,int serial,JsonObject input,Exporter local){
        super(Component.literal("Test workbench · Scenario editor"));this.parent=parent;this.token=token;this.serial=serial;document=WorkbenchScenario.validate(input);binding=document.getAsJsonObject("bundle").deepCopy();
        exporter=local==null?(name,doc)->ClientPacketDistributor.sendToServer(new WorkbenchEditPayload(token,serial,"export",name,doc.toString())):local;
        journal=new ClientDraftJournal("workbench:"+binding.get("sha256").getAsString(),document);active=new WeakReference<>(this);
    }
    static void acknowledge(CompoundTag view){var screen=active.get();if(screen==null||!screen.token.equals(view.getStringOr("token","")))return;screen.status=view.getStringOr("status","Export response received.");if(view.getBooleanOr("success",false))screen.journal.close();}
    private JsonArray runs(){return document.getAsJsonArray("runs");}
    private JsonObject run(){return runs().get(selected).getAsJsonObject();}
    @Override protected void init(){
        span=Math.min(420,width-24);left=(width-span)/2;int half=(span-8)/2;journal.observe(document);
        add("<",left,34,26,()->{selected--;rebuildWidgets();}).active=selected>0;
        add(">",left+30,34,26,()->{selected++;rebuildWidgets();}).active=selected+1<runs().size();
        add(run().get("name").getAsString(),left+64,34,span-64,()->minecraft.gui.setScreen(new StudioChoiceScreen(this,"Scenario runs",java.util.stream.IntStream.range(0,runs().size()).mapToObj(i->i+" · "+runs().get(i).getAsJsonObject().get("name").getAsString()).toList(),v->{selected=index(v);minecraft.gui.setScreen(this);})));
        add("Run settings",left,58,span,this::runSettings);
        add("Chunk areas",left,82,half,this::areas);add("Probes",left+half+8,82,half,this::probes);
        add("Duplicate run",left,106,half,()->{var copy=run().deepCopy();int n=1;while(hasName("run_"+n))n++;copy.addProperty("name","run_"+n);var next=document.deepCopy();next.getAsJsonArray("runs").add(copy);replace(next);selected=runs().size()-1;rebuildWidgets();}).active=runs().size()<16;
        add("Remove run",left+half+8,106,half,()->{runs().remove(selected);selected=Math.min(selected,runs().size()-1);rebuildWidgets();}).active=runs().size()>1;
        add("Budgets",left,130,half,this::budgets);add("History / recovery",left+half+8,130,half,this::history);
        add("Export scenario",left,height-26,half,this::export).active=!journal.pending();add("Cancel",left+half+8,height-26,half,this::onClose);
        if(journal.offer())history();
    }
    private boolean hasName(String name){for(var r:runs())if(r.getAsJsonObject().get("name").getAsString().equals(name))return true;return false;}
    private void replace(JsonElement candidate){var checked=WorkbenchScenario.validate(candidate);if(!checked.getAsJsonObject("bundle").equals(binding))throw new IllegalArgumentException("The preset bundle binding cannot be changed.");document=checked;selected=Math.clamp(selected,0,runs().size()-1);}
    private void history(){minecraft.gui.setScreen(new AuthoringHistoryScreen(this,journal,()->document,this::replace,List.of(
        new AuthoringHistoryScreen.Action("Move selected run earlier",()->selected>0,()->moveRun(-1)),new AuthoringHistoryScreen.Action("Move selected run later",()->selected+1<runs().size(),()->moveRun(1)))));}
    private void moveRun(int delta){var list=new ArrayList<JsonElement>();runs().forEach(list::add);Collections.swap(list,selected,selected+delta);var array=new JsonArray();list.forEach(array::add);document.add("runs",array);selected+=delta;}
    private Button add(String name,int x,int y,int width,Runnable action){return addRenderableWidget(Button.builder(Component.literal(name),b->{try{action.run();}catch(RuntimeException e){status=e.getMessage();}}).bounds(x,y,width,20).build());}
    private StudioFormScreen.Field f(String name,Object value){return new StudioFormScreen.Field(name,String.valueOf(value));}
    private static int index(String text){return Integer.parseInt(text.split(" · ")[0]);}
    private static boolean bool(String text){if(!text.equals("true")&&!text.equals("false"))throw new IllegalArgumentException("Use true or false.");return Boolean.parseBoolean(text);}
    private void form(String title,String help,List<StudioFormScreen.Field> fields,Function<List<String>,JsonObject> edit){
        minecraft.gui.setScreen(new StudioFormScreen(this,title,help,fields,v->{try{WorkbenchScenario.validate(edit.apply(v));return "";}catch(RuntimeException e){return e.getMessage();}},v->replace(edit.apply(v))));
    }
    private JsonObject withRun(JsonObject run){var copy=document.deepCopy();copy.getAsJsonArray("runs").set(selected,run);return copy;}
    private void runSettings(){var r=run();var dims=new ArrayList<String>();r.getAsJsonArray("dimensions").forEach(v->dims.add(v.getAsString()));form("Scenario run","Seeds are exact signed 64-bit values. Removing a dimension requires removing its areas and probes first.",List.of(f("Name",r.get("name").getAsString()),f("Seed",r.get("seed").getAsString()),f("Dimension IDs · comma separated",String.join(",",dims)),f("Startup · accept/reject",r.get("expect_startup").getAsString()),f("Expected error · required for rejection",r.has("expected_error")?r.get("expected_error").getAsString():""),f("Check safe spawn · true/false",r.get("safe_spawn")),f("Restart · true/false",r.get("restart"))),v->{var next=r.deepCopy();next.addProperty("name",v.get(0));next.addProperty("seed",v.get(1));var ids=new JsonArray();for(String id:v.get(2).split(","))ids.add(id.strip());next.add("dimensions",ids);next.addProperty("expect_startup",v.get(3));if(v.get(4).isBlank())next.remove("expected_error");else next.addProperty("expected_error",v.get(4));next.addProperty("safe_spawn",bool(v.get(5)));next.addProperty("restart",bool(v.get(6)));return withRun(next);});}
    private void budgets(){var b=document.getAsJsonObject("budgets");form("Test budgets","The runner enforces 30–900 seconds per stage and 768–4096 MiB heap. A scenario requests at most 2048 chunks.",List.of(f("Stage timeout · seconds",b.get("timeout_seconds")),f("Heap · MiB",b.get("heap_mib"))),v->{var next=document.deepCopy();next.getAsJsonObject("budgets").addProperty("timeout_seconds",Integer.parseInt(v.get(0)));next.getAsJsonObject("budgets").addProperty("heap_mib",Integer.parseInt(v.get(1)));return next;});}
    private List<String> labels(String key){var result=new ArrayList<String>();var entries=run().getAsJsonArray(key);for(int i=0;i<entries.size();i++)result.add(i+" · "+entries.get(i).getAsJsonObject().get("dimension").getAsString());return result;}
    private void areas(){menu("regions",32,()->area(-1),this::area);}
    private void probes(){menu("probes",128,()->probe(-1),this::probe);}
    private void menu(String key,int limit,Runnable create,IntConsumer edit){var choices=new ArrayList<String>();if(run().getAsJsonArray(key).size()<limit)choices.add("Add");if(!labels(key).isEmpty()){choices.add("Edit");choices.add("Remove");}minecraft.gui.setScreen(new StudioChoiceScreen(this,key,choices,action->{if(action.equals("Add"))create.run();else minecraft.gui.setScreen(new StudioChoiceScreen(this,action+" "+key,labels(key),value->{int i=index(value);if(action.equals("Edit"))edit.accept(i);else{run().getAsJsonArray(key).remove(i);minecraft.gui.setScreen(this);}}));}));}
    private JsonObject withEntry(String key,int index,JsonObject entry){var next=run().deepCopy();var array=next.getAsJsonArray(key);if(index<0)array.add(entry);else array.set(index,entry);return withRun(next);}
    private void area(int index){var value=index<0?JsonParser.parseString("{\"chunk_x\":256,\"chunk_z\":256,\"width\":2,\"depth\":2}").getAsJsonObject():run().getAsJsonArray("regions").get(index).getAsJsonObject();String dim=value.has("dimension")?value.get("dimension").getAsString():run().getAsJsonArray("dimensions").get(0).getAsString();form("Requested chunk area","Each run requests at most 256 chunks. Width and depth are 1–16 chunks.",List.of(f("Dimension",dim),f("Chunk X",value.get("chunk_x")),f("Chunk Z",value.get("chunk_z")),f("Width",value.get("width")),f("Depth",value.get("depth"))),v->{var entry=new JsonObject();entry.addProperty("dimension",v.get(0));for(int i=1;i<5;i++)entry.addProperty(List.of("chunk_x","chunk_z","width","depth").get(i-1),Integer.parseInt(v.get(i)));return withEntry("regions",index,entry);});}
    private void probe(int index){var value=index<0?new JsonObject():run().getAsJsonArray("probes").get(index).getAsJsonObject();String dim=value.has("dimension")?value.get("dimension").getAsString():run().getAsJsonArray("dimensions").get(0).getAsString();form("Block, biome or structure probe","Supply at least one expected value. Structure probes require a valid start in that chunk. Runtime mismatches fail the scenario.",List.of(f("Dimension",dim),f("Block X",value.has("x")?value.get("x"):0),f("Block Y",value.has("y")?value.get("y"):64),f("Block Z",value.has("z")?value.get("z"):0),f("Block state · optional",value.has("block")?value.get("block").getAsString():""),f("Biome ID · optional",value.has("biome")?value.get("biome").getAsString():""),f("Structure ID · optional",value.has("structure")?value.get("structure").getAsString():"")),v->{var entry=new JsonObject();entry.addProperty("dimension",v.get(0));for(int i=1;i<4;i++)entry.addProperty(List.of("x","y","z").get(i-1),Integer.parseInt(v.get(i)));for(int i=4;i<7;i++)if(!v.get(i).isBlank())entry.addProperty(List.of("block","biome","structure").get(i-4),v.get(i));return withEntry("probes",index,entry);});}
    private void export(){minecraft.gui.setScreen(new StudioFormScreen(this,"Export test scenario","The selected saved bundle is fingerprinted. Existing scenario files cannot be overwritten. Run the exported file with the source workbench.",List.of(f("New export name","pack-test")),v->v.get(0).matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}")?"":"Choose a simple export name.",v->{try{exporter.export(v.get(0),WorkbenchScenario.validate(document));status=token.isEmpty()?"Scenario and bound bundle exported to config/worldarchitect/exports.":"Export requested; waiting for the server's validation.";if(token.isEmpty())journal.close();}catch(IOException|RuntimeException e){status=e.getMessage();}}));}
    @Override public void onClose(){journal.close(this,()->{if(!token.isEmpty())ClientPacketDistributor.sendToServer(new WorkbenchEditPayload(token,serial,"close","","{}"));minecraft.gui.setScreen(parent);});}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float tick){super.extractRenderState(g,x,y,tick);g.centeredText(font,title,width/2,8,0xFFF1E7CE);var lines=font.split(Component.literal(status),span);for(int i=0;i<Math.min(3,lines.size());i++)g.text(font,lines.get(i),left,height-64+i*10,0xFFEAEAEA);}
}
