package com.gwsheridan.worldsunwritten.client;

import com.gwsheridan.worldsunwritten.rules.*;
import com.gwsheridan.worldsunwritten.worldgen.GenerationPreview;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.*;
import net.minecraft.resources.Identifier;

/** Fixed-size incremental sampling uses the same conditions and seeded evaluator as generation. */
final class RuleInspectorScreen extends Screen {
    private static final int GRID=48,BOUND=29_990_000;
    private final Screen parent;private final Identifier dimension;private final long seed;private final RuleSet rules;private final GenerationPreview preview;
    private final RuleSet.Context[] contexts=new RuleSet.Context[GRID*GRID];private final int[] heights=new int[GRID*GRID];private final long[] eligible=new long[GRID*GRID];
    private final Set<String> mods=Set.copyOf(RuleDependencies.loaded());private RuleInspection.Overlay overlay=RuleInspection.Overlay.ALL;
    private int selected,centerX,centerZ,planeY,step=4,next,mapLeft,mapSide;private boolean surface;private String problem;
    RuleInspectorScreen(Screen parent,Identifier dimension,long seed,RuleSet rules,GenerationPreview preview){
        super(Component.literal("Rule Inspector"));this.parent=parent;this.dimension=dimension;this.seed=seed;this.rules=rules;this.preview=preview;planeY=Math.clamp(64,preview.minY(),preview.maxY());
        problem=rules.validate().error().map(e->e.message()).orElse(null);
    }
    @Override protected void init(){
        int span=Math.min(420,width-24),left=(width-span)/2;mapSide=Math.min(Math.min(280,span),height-158);mapLeft=(width-mapSide)/2;
        add("<",left,27,22,()->select(-1)).active=selected>0;add(">",left+26,27,22,()->select(1)).active=selected+1<rules.ordered().size();
        addRenderableWidget(CycleButton.builder(RuleInspectorScreen::label,overlay).withValues(RuleInspection.Overlay.values()).displayOnlyValue().create(left+56,27,132,20,Component.literal("Coverage"),(b,value)->overlay=value));
        add("Order & warnings",left+span-102,27,102,()->minecraft.gui.setScreen(new StudioReportScreen(this,"Rule order and dependencies",RuleInspection.report(rules,preview.minY(),preview.maxY()))));
        add("Coordinates",left,53,88,this::coordinates);add(surface?"Y: surface":"Y: "+planeY,left+94,53,90,()->{surface=!surface;resample();rebuildWidgets();});
        add("−",left+span-56,53,26,()->{step=Math.min(256,step*2);resample();});add("+",left+span-26,53,26,()->{step=Math.max(1,step/2);resample();});
        String[] arrows={"←","↑","↓","→"};int[][] movement={{-1,0},{0,-1},{0,1},{1,0}};
        for(int i=0;i<4;i++){int index=i;add(arrows[i],left+i*28,height-26,24,()->{centerX=Math.clamp(centerX+movement[index][0]*step*GRID/2,-BOUND,BOUND);centerZ=Math.clamp(centerZ+movement[index][1]*step*GRID/2,-BOUND,BOUND);resample();});}
        add("Explain",left+114,height-26,62,this::explain).active=problem==null&&!rules.isEmpty();
        add("Back",left+span-74,height-26,74,this::onClose);
    }
    private static Component label(RuleInspection.Overlay overlay){return Component.literal(switch(overlay){case ALL->"All conditions";case BIOME->"Biome filter";case HEIGHT->"Height filter";case REGION->"Region filter";case DISTANCE->"Distance filter";});}
    private Button add(String title,int x,int y,int width,Runnable action){return addRenderableWidget(Button.builder(Component.literal(title),b->action.run()).bounds(x,y,width,20).build());}
    private void select(int direction){selected=Math.clamp(selected+direction,0,Math.max(0,rules.ordered().size()-1));rebuildWidgets();}
    private void resample(){Arrays.fill(contexts,null);next=0;}
    private RuleSet.Context context(int x,int z,GenerationPreview.Sample sample){int y=surface&&sample.occupied()?Math.clamp(sample.surfaceY()+1,preview.minY(),preview.maxY()):planeY;var biome=preview.biomeAt(x,y,z).orElseThrow(()->new IllegalStateException("A sampled biome has no registry ID."));return new RuleSet.Context(dimension,biome,x,y,z,seed,mods,preview.regions());}
    @Override public void tick(){
        super.tick();if(problem!=null||rules.isEmpty())return;long until=System.nanoTime()+3_000_000L;
        try{for(int count=0;next<contexts.length&&count<64&&System.nanoTime()<until;count++,next++){
            int x=centerX+(next%GRID-GRID/2)*step,z=centerZ+(next/GRID-GRID/2)*step;var sample=preview.sample(x,z);var context=context(x,z,sample);contexts[next]=context;heights[next]=sample.surfaceY();eligible[next]=rules.matches(context);
        }}catch(RuntimeException e){problem=e.getMessage()==null?"Cannot sample this profile.":e.getMessage();}
    }
    private void coordinates(){minecraft.gui.setScreen(new StudioFormScreen(this,"Inspection coordinates","Coverage samples this dimension. Surface mode uses first-air Y; empty columns use the fixed Y.",List.of(
            new StudioFormScreen.Field("Centre X",""+centerX),new StudioFormScreen.Field("Centre Z",""+centerZ),new StudioFormScreen.Field("Fixed Y",""+planeY)),v->{int x=Integer.parseInt(v.get(0)),z=Integer.parseInt(v.get(1)),y=Integer.parseInt(v.get(2));return Math.abs((long)x)>BOUND||Math.abs((long)z)>BOUND||y<preview.minY()||y>preview.maxY()?"Choose coordinates inside the dimension and preview bounds.":"";},v->{centerX=Integer.parseInt(v.get(0));centerZ=Integer.parseInt(v.get(1));planeY=Integer.parseInt(v.get(2));resample();}));}
    private void explain(){
        var sample=preview.sample(centerX,centerZ);var context=context(centerX,centerZ,sample);var report=new ArrayList<>(RuleInspection.explain(rules,context));
        if(preview.endArena()&&com.gwsheridan.worldsunwritten.config.EndProgression.contains(centerX,centerZ))report.addFirst("Native End arena: custom generation actions are excluded at this coordinate.");
        minecraft.gui.setScreen(new StudioReportScreen(this,"Coordinate explanation",report));
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float tick){
        super.extractRenderState(g,mx,my,tick);int span=Math.min(420,width-24),left=(width-span)/2;g.centeredText(font,title,width/2,7,0xFFF1E7CE);
        int top=79;g.fill(mapLeft-1,top-1,mapLeft+mapSide+1,top+mapSide+1,0xFF7588A0);
        for(int i=0;i<contexts.length;i++){
            int color=0xFF202733;var c=contexts[i];if(c!=null&&!rules.isEmpty()){
                boolean match=overlay==RuleInspection.Overlay.ALL?(eligible[i]&1L<<selected)!=0:RuleInspection.overlay(overlay,rules,selected,c);
                double light=.6+Math.clamp((heights[i]-preview.minY())/(double)Math.max(1,preview.maxY()-preview.minY()),0,1)*.4;
                int rgb=preview.endArena()&&com.gwsheridan.worldsunwritten.config.EndProgression.contains(c.x(),c.z())?0x479BA8:match?0x69BB79:0xAD666E;
                color=0xFF000000|(int)((rgb>>16&255)*light)<<16|(int)((rgb>>8&255)*light)<<8|(int)((rgb&255)*light);
            }
            int x=i%GRID,z=i/GRID;g.fill(mapLeft+x*mapSide/GRID,top+z*mapSide/GRID,mapLeft+(x+1)*mapSide/GRID,top+(z+1)*mapSide/GRID,color);
        }
        String line=problem!=null?problem:rules.isEmpty()?"No rules to inspect.":(selected+1)+"/"+rules.ordered().size()+" · "+rules.ordered().get(selected).id()+" · "+next+"/2304 samples";
        if(mx>=mapLeft&&mx<mapLeft+mapSide&&my>=top&&my<top+mapSide){var c=contexts[(my-top)*GRID/mapSide*GRID+(mx-mapLeft)*GRID/mapSide];if(c!=null)line=c.x()+", "+c.y()+", "+c.z()+" · "+c.biome();}
        g.text(font,font.plainSubstrByWidth(line,span),left,height-65,problem==null?0xFFEAEAEA:0xFFFFAAAA);
        g.text(font,font.plainSubstrByWidth("Green: matches · red: rejected · cyan: reserved End arena",span),left,height-53,0xFFCCCCCC);
        g.text(font,font.plainSubstrByWidth("Condition coverage · "+step+" blocks/cell · "+dimension,span),left,height-42,0xFFCCCCCC);
    }
}
