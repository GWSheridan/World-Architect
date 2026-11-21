package com.gwsheridan.worldarchitect.client;

import com.gwsheridan.worldarchitect.config.ContentProfile;
import com.gwsheridan.worldarchitect.config.RegionalTerrain;
import com.gwsheridan.worldarchitect.structure.StructureDistricts;
import com.gwsheridan.worldarchitect.structure.ModularStructure;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class StructureDistrictScreen extends Screen {
    private final Screen parent;private final Optional<RegionalTerrain> regions;private final Consumer<ContentProfile> save;
    private ContentProfile draft;private int selected;private String status="";
    StructureDistrictScreen(Screen parent,Optional<RegionalTerrain> regions,ContentProfile draft,Consumer<ContentProfile> save){super(Component.literal("Structure districts"));this.parent=parent;this.regions=regions;this.draft=draft;this.save=save;}
    @Override protected void init(){
        int w=Math.min(400,width-24),x=(width-w)/2;boolean any=!draft.structures().isEmpty();
        button("Select assembly",x,40,w,()->minecraft.gui.setScreen(new StudioChoiceScreen(this,"District members",labels(),value->{selected=Integer.parseInt(value.split(" · ")[0]);minecraft.gui.setScreen(this);}))).active=any;
        button("Edit district membership",x,68,w,this::edit).active=any&&draft.structures().get(Math.min(selected,draft.structures().size()-1)).structure().value() instanceof ModularStructure;
        button("Remove from district",x,96,w,()->{var rules=new ArrayList<>(draft.structures());var r=rules.get(selected);rules.set(selected,new ContentProfile.StructureRule(r.structure(),r.spacing(),r.separation()));draft=draft.withStructures(rules);rebuildWidgets();}).active=any&&draft.structures().get(Math.min(selected,draft.structures().size()-1)).district().isPresent();
        button("Placement help",x,124,w,()->minecraft.gui.setScreen(new StudioReportScreen(this,"District placement",List.of("Select modular assemblies in Features → Structures first.","Members share a native grid. Weights choose one assembly before terrain checks.","Districts with the same exclusion group reserve higher-ranked potential sites within their minimum distance.","Reservations are conservative: an unsuitable terrain site can still exclude a neighbour.","Region limits apply to every template, support and clearance cell.","Settings affect new worlds. Saved definitions remain immutable."))));
        var problem=StructureDistricts.problem(draft.structures(),regions,true);status=problem.orElse(any?labels().get(selected):"Select modular structures before creating a district.");
        button("Done",x,height-26,(w-8)/2,()->{save.accept(draft);onClose();}).active=problem.isEmpty();button("Cancel",x+(w+8)/2,height-26,(w-8)/2,this::onClose);
    }
    private List<String> labels(){var labels=new ArrayList<String>();for(int i=0;i<draft.structures().size();i++){var r=draft.structures().get(i);labels.add(i+" · "+r.structure().unwrapKey().orElseThrow().identifier()+" · "+r.district().map(StructureDistricts.Member::group).orElse("individual"));}return labels;}
    private List<ContentProfile.StructureRule> parse(List<String> values){
        var chosen=draft.structures().get(selected);var region=values.get(4).equals("none")?Optional.<String>empty():Optional.of(values.get(4));
        var member=new StructureDistricts.Member(values.get(0),values.get(1),Integer.parseInt(values.get(2)),Integer.parseInt(values.get(3)),region);member.validate().getOrThrow();
        int spacing=Integer.parseInt(values.get(5)),separation=Integer.parseInt(values.get(6));if(spacing<4||spacing>128||separation<3||separation>=spacing)throw new IllegalArgumentException("Keep spacing at 4–128 and separation at 3–127, smaller than spacing.");
        var result=new ArrayList<ContentProfile.StructureRule>();for(int i=0;i<draft.structures().size();i++){var r=draft.structures().get(i);if(i==selected)result.add(new ContentProfile.StructureRule(r.structure(),spacing,separation,Optional.of(member)));else if(r.district().filter(d->d.group().equals(member.group())).isPresent()){var old=r.district().orElseThrow();result.add(new ContentProfile.StructureRule(r.structure(),spacing,separation,Optional.of(new StructureDistricts.Member(member.group(),member.exclusion(),old.weight(),member.minDistance(),member.region()))));}else result.add(r);}
        StructureDistricts.problem(result,regions,true).ifPresent(message->{throw new IllegalArgumentException(message);});return result;
    }
    private void edit(){var r=draft.structures().get(selected);var m=r.district().orElse(new StructureDistricts.Member("settlement","settlements",1,12,Optional.empty()));
        var fields=List.of(field("District name",m.group()),field("Exclusion group",m.exclusion()),field("Assembly weight",m.weight()),field("Minimum distance · chunks",m.minDistance()),field("Region name or none",m.region().orElse("none")),field("Native spacing · chunks",r.spacing()),field("Native separation · chunks",r.separation()));
        minecraft.gui.setScreen(new StudioFormScreen(this,"District membership","Shared grid and exclusion settings update the other members of this district together.",fields,v->{try{parse(v);return "";}catch(RuntimeException e){return Objects.toString(e.getMessage(),"Check district fields.");}},v->{draft=draft.withStructures(parse(v));}));
    }
    private StudioFormScreen.Field field(String name,Object value){return new StudioFormScreen.Field(name,String.valueOf(value));}
    private Button button(String label,int x,int y,int w,Runnable action){return addRenderableWidget(Button.builder(Component.literal(label),b->action.run()).bounds(x,y,w,20).build());}
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int x,int y,float tick){super.extractRenderState(graphics,x,y,tick);graphics.centeredText(font,title,width/2,12,0xFFF1E7CE);var lines=font.split(Component.literal(status),width-24);for(int i=0;i<Math.min(3,lines.size());i++)graphics.text(font,lines.get(i),12,151+i*10,0xFFCCCCCC);}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
}
