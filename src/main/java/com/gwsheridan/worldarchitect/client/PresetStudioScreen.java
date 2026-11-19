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
    private PresetStudio.Draft draft;private String name="my-preset",status="";private List<PresetStudio.Revision> revisions=List.of();private int selected,left,span;
    PresetStudioScreen(Screen parent,HolderLookup.Provider registries,WorldDimensions dimensions,WorldOptions options,Consumer<PresetStudio.Draft> apply)throws IOException{
        super(Component.literal("Preset Studio"));this.parent=parent;this.registries=registries;this.apply=apply;draft=PresetStudio.capture(PresetStudio.defaults(name,"1.0.0",options),dimensions,registries);refreshLibrary();
    }
    private void refreshLibrary(){try{revisions=PresetStudio.list(ModpackRuntime.directory());selected=Math.min(selected,Math.max(0,revisions.size()-1));}catch(IOException|RuntimeException e){status=e.getMessage();}}
    @Override protected void init(){span=Math.min(420,width-24);left=(width-span)/2;int half=(span-8)/2;
        button("←",left,38,32,()->{selected=Math.max(0,selected-1);rebuildWidgets();}).active=selected>0;
        button("→",left+span-32,38,32,()->{selected=Math.min(revisions.size()-1,selected+1);rebuildWidgets();}).active=selected+1<revisions.size();
        button("Metadata",left,108,half,this::metadata);button("Locks",left+half+8,108,half,()->minecraft.gui.setScreen(new StudioLockScreen(this,draft.manifest(),draft.preset(),manifest->{draft=draft.withManifest(manifest);status="Lock policy updated in the Studio draft.";rebuildWidgets();})));
        button("World defaults",left,132,half,this::defaults);button("Dependencies",left+half+8,132,half,()->act(()->{var checked=PresetStudio.validate(draft,registries);minecraft.gui.setScreen(new StudioReportScreen(this,"Preset dependency report",checked.report()));}));
        boolean developer=ModpackRuntime.developerMode();
        button("Save version",left,156,half,()->act(()->{var path=PresetStudio.save(ModpackRuntime.directory(),name,draft,registries);status="Saved immutable version "+path.getFileName();refreshLibrary();rebuildWidgets();})).active=developer;
        button("Export ZIP",left+half+8,156,half,()->act(()->{var path=PresetStudio.export(ModpackRuntime.directory(),name,draft.manifest().release(),registries);status="Exported "+path.getFileName()+" to config/worldarchitect/exports";rebuildWidgets();})).active=developer;
        button("Load version",left,180,half,()->act(()->{var revision=revisions.get(selected);draft=PresetStudio.load(ModpackRuntime.directory(),revision.name(),revision.release());name=revision.name();status="Loaded "+revision.label();rebuildWidgets();})).active=!revisions.isEmpty();
        button("Duplicate",left+half+8,180,half,()->{name=(name.length()>57?name.substring(0,57):name)+"-copy";draft=PresetStudio.duplicate(draft,name,"1.0.0");status="Copy is a draft. Set metadata and Save version.";rebuildWidgets();});
        button("Import ZIP",left,204,half,this::importZip).active=developer;button("Help",left+half+8,204,half,()->minecraft.gui.setScreen(new StudioReportScreen(this,"Preset Studio help",StudioReportScreen.help())));
        var use=button("Apply to world",left,height-26,half,()->act(()->{PresetStudio.forApply(draft,registries);apply.accept(draft);onClose();}));
        use.active=ModpackRuntime.locked().isEmpty();
        try{use.active&=PresetStudio.validate(draft,registries).canApply();if(!use.active)use.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Install this export as a bundle and restart to load its registered templates. Active bundle locks also restrict applying other presets.")));}catch(IOException e){use.active=false;status=e.getMessage();}
        button("Cancel",left+half+8,height-26,half,this::onClose);
        if(!developer&&status.isEmpty())status="Enable developer_mode and restart to save, import or export. Help explains setup.";
    }
    private BundleManifest metadata(List<String> values){var m=draft.manifest();return new BundleManifest(Identifier.parse(values.get(1).strip()),values.get(2).strip(),values.get(5),values.get(3).strip(),values.get(4).strip(),m.lock(),m.options(),m.editableDimensions(),m.editableFeatures(),m.editableOptions(),csv(values.get(6)),m.allowStructureLibrary());}
    private void metadata(){var m=draft.manifest();var fields=List.of(new StudioFormScreen.Field("Library name",name),new StudioFormScreen.Field("Preset registry ID",m.preset().toString()),new StudioFormScreen.Field("Title",m.title()),new StudioFormScreen.Field("Author",m.author()),new StudioFormScreen.Field("Release / version",m.release()),new StudioFormScreen.Field("Description",m.description()),new StudioFormScreen.Field("Required mod IDs · comma separated",String.join(",",m.requiredMods())));
        minecraft.gui.setScreen(new StudioFormScreen(this,"Preset metadata","Saved releases cannot be overwritten. Set a new release before saving changed settings.",fields,values->{if(!values.getFirst().matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}"))return "Use a simple library name without directories.";var parsed=metadata(values);return BundleManifest.CODEC.encodeStart(JsonOps.INSTANCE,parsed).flatMap(json->BundleManifest.CODEC.parse(JsonOps.INSTANCE,json)).error().map(error->error.message()).orElse("");},values->{name=values.getFirst();draft=draft.withManifest(metadata(values));status="Metadata updated in the Studio draft.";}));}
    private void defaults(){var m=draft.manifest();minecraft.gui.setScreen(new StudioFormScreen(this,"Preset world defaults","Enter true or false for generation toggles. Gameplay rules remain outside the preset lock.",List.of(new StudioFormScreen.Field("Seed",Long.toString(m.options().seed())),new StudioFormScreen.Field("Generate structures",Boolean.toString(m.options().generateStructures())),new StudioFormScreen.Field("Bonus chest",Boolean.toString(m.options().generateBonusChest()))),values->{Long.parseLong(values.getFirst());return bool(values.get(1))&&bool(values.get(2))?"":"Use true or false.";},values->{draft=draft.withManifest(new BundleManifest(m.preset(),m.title(),m.description(),m.author(),m.release(),m.lock(),new WorldOptions(Long.parseLong(values.getFirst()),Boolean.parseBoolean(values.get(1)),Boolean.parseBoolean(values.get(2))),m.editableDimensions(),m.editableFeatures(),m.editableOptions(),m.requiredMods(),m.allowStructureLibrary()));status="World defaults updated in the Studio draft.";}));}
    private void importZip(){minecraft.gui.setScreen(new StudioFormScreen(this,"Import preset ZIP","Place the ZIP in config/worldarchitect/imports. Import validates it; Save version keeps it in the library.",List.of(new StudioFormScreen.Field("ZIP filename","preset.zip")),values->values.getFirst().matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}\\.zip")?"":"Choose a local .zip filename.",values->act(()->{draft=PresetStudio.readImport(ModpackRuntime.directory(),values.getFirst(),registries);name=draft.manifest().preset().getPath().replace('/','_');status="Imported and validated. Choose Save version to retain it.";})));}
    private static List<String> csv(String text){return text.isBlank()?List.of():Arrays.stream(text.split(",")).map(String::strip).toList();}
    private static boolean bool(String text){return text.equals("true")||text.equals("false");}
    private Button button(String label,int x,int y,int w,Runnable run){return addRenderableWidget(Button.builder(Component.literal(label),b->run.run()).bounds(x,y,w,20).build());}
    @FunctionalInterface private interface Action{void run()throws IOException;}
    private void act(Action action){try{action.run();}catch(IOException|RuntimeException e){status=e.getMessage()==null?"Preset action failed.":e.getMessage();if(minecraft.gui.screen()==this)rebuildWidgets();}}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int x,int y,float tick){super.extractRenderState(graphics,x,y,tick);graphics.centeredText(font,title,width/2,8,0xFFF1E7CE);graphics.centeredText(font,Component.literal("Versioned authoring · generation in existing saves stays immutable"),width/2,22,0xFFCCCCCC);String selectedLabel=revisions.isEmpty()?"Local library is empty":(selected+1)+"/"+revisions.size()+" · "+revisions.get(selected).label();graphics.text(font,font.plainSubstrByWidth(selectedLabel,span-76),left+38,44,0xFFEAEAEA);graphics.text(font,font.plainSubstrByWidth("Draft: "+name+" / "+draft.manifest().release(),span),left,72,0xFFEAEAEA);graphics.text(font,font.plainSubstrByWidth(draft.manifest().preset()+" · "+draft.manifest().lock().getSerializedName(),span),left,88,0xFFCCCCCC);var lines=font.split(Component.literal(status),span);for(int i=0;i<Math.min(3,lines.size());i++)graphics.text(font,lines.get(i),left,height-62+i*10,0xFFEAEAEA);}
}
