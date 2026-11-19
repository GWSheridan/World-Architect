package com.gwsheridan.worldarchitect.client;

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
        "Enable authoring: set {\"format\":1,\"developer_mode\":true} in config/worldarchitect/developer.json, then restart.",
        "Metadata sets the preset ID, title, author, release and required mod IDs. World defaults sets seed, structures and bonus chest. Locks chooses the permitted edits.",
        "Save version writes an immutable local archive under config/worldarchitect/studio/<name>/<release>.zip. To change a saved release, choose a new release or Duplicate. Cancel does not delete versions you explicitly saved.",
        "Load version restores a library revision into the Studio draft. Duplicate assigns a new ID and release. Apply to world changes the new-world draft; confirm Done in Dimension Manager afterward.",
        "Export ZIP copies a saved version into config/worldarchitect/exports. Put an incoming ZIP into config/worldarchitect/imports, then Import ZIP. Imports validate before entering the draft; use Save version to retain them.",
        "Native starting-island templates and selected modular structure definitions travel with the preset. New registered templates need the exported bundle installed and a restart before Apply to world is available.",
        "Partial locks permit whole dimensions, selected feature groups, and/or world options. Full locks permit no generation edits. World name, difficulty, game mode and gameplay rules remain available.",
        "Install in a clean client/server: put the ZIP in config/worldarchitect/bundles and set {\"format\":1,\"bundle\":\"your-export.zip\"} in developer.json. Restart. Use the same bundle and required mods on both sides.",
        "On dedicated servers, permitted profile edits go in config/worldarchitect/world-selection.json before first startup. Locked edits are rejected. Existing worlds keep their original archived bundle and generation settings.",
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
