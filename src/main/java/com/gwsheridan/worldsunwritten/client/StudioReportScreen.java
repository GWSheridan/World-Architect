package com.gwsheridan.worldsunwritten.client;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

final class StudioReportScreen extends Screen {
    private final Screen parent;
    private final List<String> paragraphs;
    private int page;
    StudioReportScreen(Screen parent,String title,List<String> paragraphs){super(Component.literal(title));this.parent=parent;this.paragraphs=List.copyOf(paragraphs);}
    static List<String> help(){return List.of(
        "Preset Studio authors new-world presets. It never edits saved-world generation or its snapshots.",
        "Enable authoring: set {\"format\":1,\"developer_mode\":true} in config/worldsunwritten/developer.json, then restart.",
        "Metadata sets the preset ID, title, author, release and required mod IDs. World defaults sets seed, structures and bonus chest. Locks chooses the permitted edits.",
        "Save version writes an immutable local archive under config/worldsunwritten/studio/<name>/<release>.zip. To change a saved release, choose a new release or Duplicate. Cancel does not delete versions you explicitly saved.",
        "Browse saved presets searches the local library by name, title, version, author and lock. Load selected restores a revision; a changed draft prompts before replacement. Duplicate assigns a new ID and release. Apply to world changes the dimension-manager draft; confirm Done afterward.",
        "Export ZIP copies a saved version into config/worldsunwritten/exports. Put an incoming ZIP into config/worldsunwritten/imports, then Import ZIP. Imports validate before entering the draft; use Save version to retain them.",
        "Native starting-island templates and selected modular structure definitions travel with the preset. New registered templates need the exported bundle installed and a restart before Apply to world is available.",
        "Partial locks permit whole dimensions, selected feature groups, and/or world options. Full locks permit no generation edits. World name, difficulty, game mode and gameplay rules remain available.",
        "Tools / help can derive a child from a saved parent revision. The child pins its ID, version and archive SHA-256, and includes all templates. A parent archive is not needed to install the exported child.",
        "Inherited preset report shows ancestry and effective override paths. Edit inherited override accepts a JSON pointer and value. A child can tighten parent locks, but cannot weaken them or replace locked inherited resources. Save changes as a new revision.",
        "Terrain regions support connected corridors with rounded ends. Features → Structure districts gives selected modular assemblies weighted grids, region limits and deterministic minimum spacing. These are new-world generation settings.",
        "Install in a clean client/server: put the ZIP in config/worldsunwritten/bundles and set {\"format\":1,\"bundle\":\"your-export.zip\"} in developer.json. Restart. Use the same bundle and required mods on both sides.",
        "On dedicated servers, permitted profile edits go in config/worldsunwritten/world-selection.json before first startup. Locked edits are rejected. Existing worlds keep their original archived bundle and generation settings.",
        "Dependencies lists installed required mods, registry resource namespaces, adapters and rule-condition mod pins. External biomes and dimension types still need their supplying mods/datapacks. A namespace is not necessarily a mod ID.");}
    @Override protected void init(){int left=Math.max(12,(width-420)/2),span=Math.min(420,width-24),half=(span-8)/2;
        addRenderableWidget(Button.builder(Component.literal("Previous page"),b->{page=Math.max(0,page-1);}).bounds(left,height-52,half,20).build());
        addRenderableWidget(Button.builder(Component.literal("Next page"),b->{page=Math.min(pages()-1,page+1);}).bounds(left+half+8,height-52,half,20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK,b->onClose()).bounds(left,height-26,span,20).build());}
    private List<net.minecraft.util.FormattedCharSequence> lines(){return paragraphs.stream().flatMap(text->java.util.stream.Stream.concat(font.split(Component.literal(text),Math.min(420,width-24)).stream(),java.util.stream.Stream.of(net.minecraft.util.FormattedCharSequence.EMPTY))).toList();}
    private int perPage(){return Math.max(1,(height-90)/10);}
    private int pages(){return Math.max(1,(lines().size()+perPage()-1)/perPage());}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int x,int y,float tick){super.extractRenderState(graphics,x,y,tick);int left=Math.max(12,(width-420)/2);graphics.centeredText(font,title,width/2,8,0xFFF1E7CE);graphics.centeredText(font,Component.literal((page+1)+" / "+pages()),width/2,22,0xFFCCCCCC);var lines=lines();for(int i=0;i<perPage()&&page*perPage()+i<lines.size();i++)graphics.text(font,lines.get(page*perPage()+i),left,36+i*10,0xFFEAEAEA);}
}
