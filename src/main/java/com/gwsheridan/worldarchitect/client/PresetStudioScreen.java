package com.gwsheridan.worldarchitect.client;

import com.google.gson.JsonElement;
import com.gwsheridan.worldarchitect.modpack.*;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;

final class PresetStudioScreen extends Screen {
    private final Screen parent;private final HolderLookup.Provider registries;private final Consumer<PresetStudio.Draft> apply;
    private PresetStudio.Draft draft;private PresetStudio.Draft initial;private boolean applied;private String name="my-preset",status="";private List<PresetStudio.Revision> revisions=List.of();private int selected,left,span;
    private final ClientDraftJournal journal;
    PresetStudioScreen(Screen parent,HolderLookup.Provider registries,WorldDimensions dimensions,WorldOptions options,Consumer<PresetStudio.Draft> apply)throws IOException{
        super(Component.literal("Preset Studio"));this.parent=parent;this.registries=registries;this.apply=apply;draft=PresetStudio.capture(PresetStudio.defaults(name,"1.0.0",options),dimensions,registries);initial=draft;journal=new ClientDraftJournal("preset-studio",snapshot());refreshLibrary();
    }
    private void refreshLibrary(){try{revisions=PresetStudio.list(ModpackRuntime.directory());selected=Math.min(selected,Math.max(0,revisions.size()-1));}catch(IOException|RuntimeException e){status=e.getMessage();}}
    @Override protected void init(){span=Math.min(420,width-24);left=(width-span)/2;int half=(span-8)/2;int top=height<300?94:108,row=height<300?21:24;
        journal.observe(snapshot());
        button("History",left+span-88,38,88,this::history);
        button("Workbench",left+span-184,38,88,this::workbench).active=ModpackRuntime.developerMode();
        button("Browse saved presets",left,38,span-192,()->minecraft.gui.setScreen(new PresetLibraryScreen(this,revisions,revision->confirmReplace(()->act(()->{
            draft=PresetStudio.load(ModpackRuntime.directory(),revision.name(),revision.release());name=revision.name();initial=draft;status="Loaded "+revision.label();minecraft.gui.setScreen(this);
        })))));
        button("Metadata",left,top,half,this::metadata);button("Locks",left+half+8,top,half,()->minecraft.gui.setScreen(new StudioLockScreen(this,draft.manifest(),draft.preset(),manifest->{draft=draft.withManifest(manifest);status="Lock policy updated in the Studio draft.";rebuildWidgets();})));
        button("World defaults",left,top+1*row,half,this::defaults);button("Dependencies",left+half+8,top+1*row,half,()->act(()->{var checked=PresetStudio.validate(draft,registries);minecraft.gui.setScreen(new StudioReportScreen(this,"Preset dependency report",checked.report()));}));
        boolean developer=ModpackRuntime.developerMode();
        button("Save version",left,top+2*row,half,()->act(()->{var path=PresetStudio.save(ModpackRuntime.directory(),name,draft,registries);status="Saved immutable version "+path.getFileName();refreshLibrary();rebuildWidgets();})).active=developer;
        button("Export ZIP",left+half+8,top+2*row,half,()->act(()->{var path=PresetStudio.export(ModpackRuntime.directory(),name,draft.manifest().release(),registries);status="Exported "+path.getFileName()+" to config/worldarchitect/exports";rebuildWidgets();})).active=developer;
        button("Validate draft",left,top+3*row,half,()->act(()->{var checked=PresetStudio.validate(draft,registries);status=checked.report().getLast();rebuildWidgets();}));
        button("Duplicate",left+half+8,top+3*row,half,()->act(()->{String nextName=(name.length()>57?name.substring(0,57):name)+"-copy";var copy=PresetStudio.validate(PresetStudio.duplicate(draft,nextName,"1.0.0"),registries).draft();name=nextName;draft=copy;status="Copy is a draft. Set metadata and Save version.";rebuildWidgets();}));
        button("Import ZIP",left,top+4*row,half,()->confirmReplace(this::importZip)).active=developer;button("Tools / help",left+half+8,top+4*row,half,this::tools);
        var use=button("Apply to world",left,height-26,half,()->act(()->{PresetStudio.forApply(draft,registries);apply.accept(draft);applied=true;onClose();}));
        use.active=ModpackRuntime.locked().isEmpty()&&!journal.pending();
        try{use.active&=PresetStudio.validate(draft,registries).canApply();if(!use.active)use.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Install this export as a bundle and restart to load its registered templates. Active bundle locks also restrict applying other presets.")));}catch(IOException e){use.active=false;status=e.getMessage();}
        button("Cancel",left+half+8,height-26,half,this::onClose);
        if(!developer&&status.isEmpty())status="Enable developer_mode and restart to save, import or export. Help explains setup.";
        if(journal.offer())history();
    }
    private JsonElement snapshot(){return com.gwsheridan.worldarchitect.authoring.PresetDraftDocument.encode(name,draft);}
    private void restore(JsonElement value){
        if(ModpackRuntime.locked().isPresent())throw new IllegalArgumentException("The active bundle locks this editor.");
        try{var restored=com.gwsheridan.worldarchitect.authoring.PresetDraftDocument.decode(value);var checked=PresetStudio.validate(restored,registries);name=com.gwsheridan.worldarchitect.authoring.PresetDraftDocument.name(value);draft=checked.draft();}
        catch(IOException e){throw new IllegalArgumentException(e.getMessage(),e);}
    }
    private void history(){minecraft.gui.setScreen(new AuthoringHistoryScreen(this,journal,this::snapshot,this::restore,List.of()));}
    private void tools(){
        var choices=new ArrayList<>(List.of("Help","Structure site inspector"));if(ModpackRuntime.developerMode())choices.add("Derive from saved preset");if(draft.manifest().inheritance().isPresent()){choices.add("Inherited preset report");if(ModpackRuntime.developerMode())choices.add("Edit inherited override");}
        minecraft.gui.setScreen(new StudioChoiceScreen(this,"Preset Studio tools",choices,choice->{switch(choice){
            case "Help"->minecraft.gui.setScreen(new StudioReportScreen(this,"Preset Studio help",StudioReportScreen.help()));
            case "Structure site inspector"->siteInspector();
            case "Derive from saved preset"->derive();
            case "Inherited preset report"->minecraft.gui.setScreen(new StudioReportScreen(this,"Inherited preset",draft.manifest().inheritance().orElseThrow().report(draft)));
            case "Edit inherited override"->override();
            default->throw new IllegalArgumentException("Unknown Studio tool");
        }}));
    }
    private void derive(){confirmReplace(()->{refreshLibrary();minecraft.gui.setScreen(new PresetLibraryScreen(this,revisions,revision->act(()->{
        String fingerprint=PresetStudio.fingerprint(ModpackRuntime.directory(),revision.name(),revision.release());
        minecraft.gui.setScreen(new StudioFormScreen(this,"Derive a preset","The child captures this parent's exact revision, resources and locks. Exported children are self-contained.",List.of(new StudioFormScreen.Field("Child library name",revision.name()+"-child"),new StudioFormScreen.Field("Child release","1.0.0")),v->v.get(0).matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}")&&v.get(1).matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,39}")?"":"Use a simple library name and release.",v->act(()->{var child=PresetStudio.derive(ModpackRuntime.directory(),revision.name(),revision.release(),fingerprint,v.get(0),v.get(1),registries);name=v.get(0);draft=child;status="Derived a detached child. Review inherited overrides, then Save version.";})));
    })));});}
    private void override(){
        minecraft.gui.setScreen(new StudioFormScreen(this,"Inherited override","Use a JSON pointer, escaping / in dimension IDs as ~1. Validation enforces the parent's locks.",List.of(new StudioFormScreen.Field("Field pointer","/dimensions/minecraft:overworld/generator/profile/elevation"),new StudioFormScreen.Field("JSON value","72"),new StudioFormScreen.Field("Remove field · true/false","false")),v->{try{if(!bool(v.get(2)))return "Use true or false for Remove field.";PresetInheritance.override(draft.preset(),v.get(0),com.google.gson.JsonParser.parseString(v.get(1)),Boolean.parseBoolean(v.get(2)));return "";}catch(RuntimeException e){return Objects.toString(e.getMessage(),"Check the pointer and JSON value.");}},v->act(()->{draft=PresetStudio.override(draft,v.get(0),com.google.gson.JsonParser.parseString(v.get(1)),Boolean.parseBoolean(v.get(2)),registries);status="Validated override applied to this child draft. Save under a new version.";})));
    }
    private void workbench(){act(()->{var saved=PresetStudio.load(ModpackRuntime.directory(),name,draft.manifest().release());var first=com.gwsheridan.worldarchitect.authoring.PresetDraftDocument.encode(name,PresetStudio.validate(saved,registries).draft());var current=com.gwsheridan.worldarchitect.authoring.PresetDraftDocument.encode(name,PresetStudio.validate(draft,registries).draft());if(!first.equals(current))throw new IOException("Save changed settings under a new version before authoring its tests.");var source=com.gwsheridan.worldarchitect.workbench.WorkbenchAuthoring.source(name,draft.manifest().release(),registries);minecraft.gui.setScreen(new WorkbenchScenarioScreen(this,source,registries));});}
    private void siteInspector(){act(()->{
        if(minecraft.getConnection()==null)throw new IOException("Join an authoring world, import this saved preset on its server, then use /wa_site studio "+name+" "+draft.manifest().release());
        var saved=PresetStudio.load(ModpackRuntime.directory(),name,draft.manifest().release());
        if(!com.gwsheridan.worldarchitect.authoring.PresetDraftDocument.encode(name,PresetStudio.validate(saved,registries).draft()).equals(com.gwsheridan.worldarchitect.authoring.PresetDraftDocument.encode(name,PresetStudio.validate(draft,registries).draft())))throw new IOException("Save the changed draft as a new version before inspecting sites.");
        var source=com.gwsheridan.worldarchitect.workbench.WorkbenchAuthoring.source(name,draft.manifest().release(),registries);minecraft.getConnection().sendCommand("wa_site studio "+name+" "+draft.manifest().release()+" "+source.sha256());
    });}
    private BundleManifest metadata(List<String> values){var m=draft.manifest();return new BundleManifest(Identifier.parse(values.get(1).strip()),values.get(2).strip(),values.get(5),values.get(3).strip(),values.get(4).strip(),m.lock(),m.options(),m.editableDimensions(),m.editableFeatures(),m.editableOptions(),csv(values.get(6)),m.allowStructureLibrary(),m.inheritance());}
    private void metadata(){var m=draft.manifest();var fields=List.of(new StudioFormScreen.Field("Library name",name),new StudioFormScreen.Field("Preset registry ID",m.preset().toString()),new StudioFormScreen.Field("Title",m.title()),new StudioFormScreen.Field("Author",m.author()),new StudioFormScreen.Field("Release / version",m.release()),new StudioFormScreen.Field("Description",m.description()),new StudioFormScreen.Field("Required mod IDs · comma separated",String.join(",",m.requiredMods())));
        minecraft.gui.setScreen(new StudioFormScreen(this,"Preset metadata","Saved releases cannot be overwritten. Set a new release before saving changed settings.",fields,values->{if(!values.getFirst().matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}"))return "Use a simple library name without directories.";var parsed=metadata(values);return BundleManifest.CODEC.encodeStart(JsonOps.INSTANCE,parsed).flatMap(json->BundleManifest.CODEC.parse(JsonOps.INSTANCE,json)).error().map(error->error.message()).orElse("");},values->{name=values.getFirst();draft=draft.withManifest(metadata(values));status="Metadata updated in the Studio draft.";}));}
    private BundleManifest defaultsManifest(BundleManifest m,List<String> values){return new BundleManifest(m.preset(),m.title(),m.description(),m.author(),m.release(),m.lock(),new WorldOptions(Long.parseLong(values.getFirst()),Boolean.parseBoolean(values.get(1)),Boolean.parseBoolean(values.get(2))),m.editableDimensions(),m.editableFeatures(),m.editableOptions(),m.requiredMods(),m.allowStructureLibrary(),m.inheritance());}
    private void defaults(){
        var m=draft.manifest();var fields=List.of(new StudioFormScreen.Field("Seed",Long.toString(m.options().seed())),new StudioFormScreen.Field("Generate structures",Boolean.toString(m.options().generateStructures())),new StudioFormScreen.Field("Bonus chest",Boolean.toString(m.options().generateBonusChest())));
        minecraft.gui.setScreen(new StudioFormScreen(this,"Preset world defaults","Enter true or false for generation toggles. Inherited locks still apply.",fields,values->{
            Long.parseLong(values.getFirst());if(!bool(values.get(1))||!bool(values.get(2)))return "Use true or false.";
            return BundleManifest.CODEC.encodeStart(JsonOps.INSTANCE,defaultsManifest(m,values)).error().map(error->error.message()).orElse("");
        },values->{draft=draft.withManifest(defaultsManifest(m,values));status="World defaults updated in the Studio draft.";}));
    }
    private void importZip(){minecraft.gui.setScreen(new StudioFormScreen(this,"Import preset ZIP","Place the ZIP in config/worldarchitect/imports. Import validates it; Save version keeps it in the library.",List.of(new StudioFormScreen.Field("ZIP filename","preset.zip")),values->values.getFirst().matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}\\.zip")?"":"Choose a local .zip filename.",values->act(()->{draft=PresetStudio.readImport(ModpackRuntime.directory(),values.getFirst(),registries);name=draft.manifest().preset().getPath().replace('/','_');status="Imported and validated. Choose Save version to retain it.";})));}
    private static List<String> csv(String text){return text.isBlank()?List.of():Arrays.stream(text.split(",")).map(String::strip).toList();}
    private static boolean bool(String text){return text.equals("true")||text.equals("false");}
    private Button button(String label,int x,int y,int w,Runnable run){return addRenderableWidget(Button.builder(Component.literal(label),b->run.run()).bounds(x,y,w,20).build());}
    @FunctionalInterface private interface Action{void run()throws IOException;}
    private void act(Action action){try{action.run();}catch(IOException|RuntimeException e){status=e.getMessage()==null?"Preset action failed.":e.getMessage();if(minecraft.gui.screen()==this)rebuildWidgets();}}
    private boolean dirty(){return !BundleManifest.CODEC.encodeStart(JsonOps.INSTANCE,initial.manifest()).result().equals(BundleManifest.CODEC.encodeStart(JsonOps.INSTANCE,draft.manifest()).result())||!initial.preset().equals(draft.preset())||!sameTemplates(initial,draft);}
    private static boolean sameTemplates(PresetStudio.Draft a,PresetStudio.Draft b){var first=a.templates();var second=b.templates();return first.keySet().equals(second.keySet())&&first.entrySet().stream().allMatch(e->java.util.Arrays.equals(e.getValue(),second.get(e.getKey())));}
    private void confirmReplace(Runnable next){if(!dirty()){next.run();return;}minecraft.gui.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(discard->{if(discard)next.run();else minecraft.gui.setScreen(this);},Component.literal("Replace Studio draft?"),Component.literal("Unsaved draft changes will be discarded. Saved library versions remain available."),Component.literal("Continue"),Component.literal("Keep editing")));}
    @Override public void onClose(){if(!applied&&dirty())confirmReplace(()->journal.close(this,()->minecraft.gui.setScreen(parent)));else journal.close(this,()->minecraft.gui.setScreen(parent));}
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int x,int y,float tick){super.extractRenderState(graphics,x,y,tick);graphics.centeredText(font,title,width/2,8,0xFFF1E7CE);graphics.centeredText(font,Component.literal("Versioned presets · new worlds only"),width/2,22,0xFFCCCCCC);String selectedLabel=revisions.isEmpty()?"Local library is empty":(selected+1)+"/"+revisions.size()+" · "+revisions.get(selected).label();graphics.text(font,font.plainSubstrByWidth("Draft: "+name+" / "+draft.manifest().release(),span),left,height<300?64:72,0xFFEAEAEA);graphics.text(font,font.plainSubstrByWidth(draft.manifest().preset()+" · "+draft.manifest().lock().getSerializedName(),span),left,height<300?78:88,0xFFCCCCCC);var lines=font.split(Component.literal(status),span);int statusTop=Math.max(height<300?201:230,height-62);for(int i=0;i<Math.min((height-30-statusTop)/10,lines.size());i++)graphics.text(font,lines.get(i),left,statusTop+i*10,0xFFEAEAEA);}
}
