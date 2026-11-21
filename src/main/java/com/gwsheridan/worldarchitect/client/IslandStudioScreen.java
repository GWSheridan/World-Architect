package com.gwsheridan.worldarchitect.client;

import com.google.gson.JsonElement;
import com.gwsheridan.worldarchitect.authoring.PackedDraft;
import com.gwsheridan.worldarchitect.modpack.ModpackRuntime;
import com.gwsheridan.worldarchitect.structure.*;
import com.gwsheridan.worldarchitect.skyblock.IslandCaptures;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.*;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

final class IslandStudioScreen extends Screen implements AuthoringContext {
    private final Screen parent;private final HolderLookup.Provider registries;private final Consumer<IslandBuild> save;private final JsonElement initial;
    private IslandBuild build;private final ClientDraftJournal journal;private IslandModelPreview preview;private Map<BlockPos,BlockState> states;
    private boolean slice,contents,applied;private int layer,left,span,viewWidth,sideX,sideWidth,mapX,mapY,mapWidth,mapDepth;private BlockPos selected;
    private String status="Choose a slice cell to position a marker. Saved revisions are immutable.";
    IslandStudioScreen(Screen parent,HolderLookup.Provider registries,IslandBuild build,Consumer<IslandBuild> save){
        super(Component.literal("Island Studio"));this.parent=parent;this.registries=registries;this.build=build;this.save=save;initial=IslandBuild.CODEC.encodeStart(JsonOps.INSTANCE,build).getOrThrow();
        layer=build.spawn().getY();selected=build.spawn();journal=new ClientDraftJournal("island-studio:"+(parent instanceof AuthoringContext a?a.authoringKey():"library"),snapshot());refreshPreview();
    }
    @Override public String authoringKey(){return parent instanceof AuthoringContext a?a.authoringKey():"island-library";}
    private JsonElement snapshot(){return PackedDraft.pack(IslandBuild.CODEC.encodeStart(JsonOps.INSTANCE,build).getOrThrow());}
    private void restore(JsonElement json){try{change(IslandBuild.CODEC.parse(JsonOps.INSTANCE,PackedDraft.unpack(json)).getOrThrow(IOException::new));}catch(IOException error){throw new IllegalArgumentException(error.getMessage(),error);}}
    private void change(IslandBuild next)throws IOException{var validated=next.checked(registries);var document=PackedDraft.pack(IslandBuild.CODEC.encodeStart(JsonOps.INSTANCE,validated).getOrThrow());build=validated;layer=Math.clamp(layer,0,build.size().getY()-1);selected=build.spawn();refreshPreview();journal.observe(document);status="Island draft updated. Save a new revision to keep it.";}
    private void refreshPreview(){states=new HashMap<>();for(var cell:build.cells())states.put(cell.pos(),cell.state());preview=new IslandModelPreview(build);}
    @Override protected void init(){
        span=Math.min(740,width-24);left=(width-span)/2;sideWidth=Math.max(112,Math.min(196,span/3));viewWidth=span-sideWidth-12;sideX=left+viewWidth+12;
        journal.observe(snapshot());int third=(viewWidth-8)/3;
        button(slice?"3D view":"Slice view",left,36,third,()->{slice=!slice;rebuildWidgets();});button(slice?"Lower slice":"Orbit left",left+third+4,36,third,()->{if(slice)layer=Math.max(0,layer-1);else preview.orbit(-1);});button(slice?"Higher slice":"Orbit right",left+2*(third+4),36,third,()->{if(slice)layer=Math.min(build.size().getY()-1,layer+1);else preview.orbit(1);});
        String[] labels={"Import island","Capture / library","Spawn markers","Starter contents","Transform / palette","Save / history"};Runnable[] actions={this::importIsland,this::captureMenu,this::markers,this::inventory,this::transform,this::saveMenu};
        for(int i=0;i<labels.length;i++)button(labels[i],sideX,36+i*25,sideWidth,actions[i]);
        int half=(span-8)/2;button(parent==null?"Save revision":"Use island",left,height-26,half,()->{if(parent==null){saveMenu();return;}act(()->{if(journal.pending())throw new IOException("Resolve the recovery draft first.");save.accept(build.checked(registries));applied=true;journal.close(this,()->minecraft.gui.setScreen(parent));});});button("Cancel",left+half+8,height-26,half,this::onClose);
        if(journal.offer())history();
    }
    private Button button(String label,int x,int y,int w,Runnable action){return addRenderableWidget(Button.builder(Component.literal(label),b->action.run()).bounds(x,y,w,20).build());}
    private void importIsland(){LocalAssetBrowser.open(this,ModpackRuntime.directory().resolve("imports"),path->act(()->change(IslandAssets.read(path,contents,registries))));}
    private void captureMenu(){
        var labels=new ArrayList<>(List.of("Set first corner here","Set second corner here","Capture selected build","Choose Structure Library template","Include captured inventories: "+(contents?"Yes":"No")));
        minecraft.gui.setScreen(new StudioChoiceScreen(this,"Capture an island",labels,choice->{
            if(choice.startsWith("Include")){contents=!contents;minecraft.gui.setScreen(this);return;}
            if(choice.startsWith("Set ")){if(minecraft.getConnection()==null){status="Join an authoring world to set capture corners.";minecraft.gui.setScreen(this);return;}minecraft.getConnection().sendCommand(choice.startsWith("Set first")?"wa_island pos1":"wa_island pos2");minecraft.gui.setScreen(null);return;}
            var server=minecraft.getSingleplayerServer();if(server==null){status="Capture on your authoring server, then import its saved island revision. Local capture requires an integrated authoring world.";minecraft.gui.setScreen(this);return;}
            var uuid=minecraft.player.getUUID();
            server.submit(()->{try{var player=server.getPlayerList().getPlayer(uuid);if(player==null)throw new IOException("Authoring player disconnected.");IslandCaptures.allow(player);
                if(choice.startsWith("Capture")){var captured=IslandCaptures.capture(player,contents);minecraft.execute(()->{act(()->change(captured));minecraft.gui.setScreen(this);});}
                else {var entries=IslandAssets.library(server);minecraft.execute(()->minecraft.gui.setScreen(new StudioChoiceScreen(this,"Structure Library",entries.stream().map(Object::toString).toList(),selected->server.submit(()->{try{var p=server.getPlayerList().getPlayer(uuid);if(p==null)throw new IOException("Authoring player disconnected.");IslandCaptures.allow(p);var captured=IslandAssets.library(server,net.minecraft.resources.Identifier.parse(selected));minecraft.execute(()->{act(()->change(captured));minecraft.gui.setScreen(this);});}catch(Exception error){failAsync(error);}}))));}
            }catch(Exception error){failAsync(error);}});
        }));
    }
    private void failAsync(Exception error){minecraft.execute(()->{status=Objects.toString(error.getMessage(),"Capture failed.");minecraft.gui.setScreen(this);});}
    private void markers(){
        var labels=new ArrayList<String>();labels.add("Add marker at selected slice cell");build.markers().forEach((name,pos)->labels.add(name+" · "+pos.toShortString()+(name.equals(build.spawnMarker())?" · spawn":"")));
        minecraft.gui.setScreen(new StudioChoiceScreen(this,"Island spawn markers",labels,choice->{
            if(choice.startsWith("Add")){minecraft.gui.setScreen(new StudioFormScreen(this,"Name this marker","Select an air cell in Slice view. Both headroom cells must be inside the captured bounds.",List.of(new StudioFormScreen.Field("Marker name","entry")),v->v.getFirst().matches("[a-z][a-z0-9_-]{0,31}")?"":"Use a short lowercase marker name.",v->act(()->{var map=new TreeMap<>(build.markers());map.put(v.getFirst(),selected);change(new IslandBuild(build.size(),map,build.spawnMarker(),build.cells()));})));}
            else {
                String name=choice.substring(0,choice.indexOf(" · "));
                minecraft.gui.setScreen(new StudioChoiceScreen(this,name,List.of("Use as spawn","Move to selected slice cell","Remove marker"),action->act(()->{
                    var map=new TreeMap<>(build.markers());String spawn=build.spawnMarker();
                    if(action.startsWith("Use"))spawn=name;
                    else if(action.startsWith("Move"))map.put(name,selected);
                    else {if(name.equals(spawn))throw new IOException("Choose another spawn marker before removing this one.");map.remove(name);}
                    change(new IslandBuild(build.size(),map,spawn,build.cells()));minecraft.gui.setScreen(this);
                })));
            }
        }));
    }
    private void inventory(){
        var containers=build.cells().stream().filter(c->IslandBuild.container(c.state())).toList();if(containers.isEmpty()){status="Capture or import a chest or barrel before editing starter contents.";return;}
        var names=containers.stream().map(c->BlockStateParser.serialize(c.state())+" · "+c.pos().toShortString()).toList();minecraft.gui.setScreen(new StudioChoiceScreen(this,"Starter container",names,choice->slots(containers.get(names.indexOf(choice)).pos())));
    }
    private List<ItemStack> stacks(BlockPos pos)throws IOException{
        var result=new ArrayList<ItemStack>(Collections.nCopies(27,ItemStack.EMPTY));var cell=build.cells().stream().filter(c->c.pos().equals(pos)).findFirst().orElseThrow();
        for(var value:cell.data().map(d->d.getListOrEmpty("Items")).orElseGet(net.minecraft.nbt.ListTag::new)){var item=(net.minecraft.nbt.CompoundTag)value;result.set(item.getIntOr("Slot",-1),ItemStack.CODEC.parse(RegistryOps.create(NbtOps.INSTANCE,registries),item).getOrThrow(IOException::new));}return result;
    }
    private void slots(BlockPos pos){act(()->{var items=stacks(pos);var labels=new ArrayList<String>();for(int i=0;i<27;i++)labels.add(String.format(Locale.ROOT,"%02d · %s",i+1,items.get(i).isEmpty()?"Empty":items.get(i).getHoverName().getString()+" × "+items.get(i).getCount()));minecraft.gui.setScreen(new StudioChoiceScreen(this,"Starter items",labels,choice->{int slot=Integer.parseInt(choice.substring(0,2))-1;minecraft.gui.setScreen(new StudioChoiceScreen(this,"Container slot "+(slot+1),List.of("Choose item","Change amount","Clear slot"),action->{if(action.equals("Choose item"))minecraft.gui.setScreen(new ItemPickerScreen(this,item->amount(pos,slot,item)));else if(action.equals("Change amount"))amount(pos,slot,items.get(slot));else act(()->{items.set(slot,ItemStack.EMPTY);change(IslandAssets.inventory(build,pos,items,registries));minecraft.gui.setScreen(this);});}));}));});}
    private void amount(BlockPos pos,int slot,ItemStack item){if(item.isEmpty()){status="Choose an item for this slot first.";minecraft.gui.setScreen(this);return;}minecraft.gui.setScreen(new StudioFormScreen(this,"Starter stack amount",item.getHoverName().getString(),List.of(new StudioFormScreen.Field("Amount",Integer.toString(item.getCount()))),v->{int n=Integer.parseInt(v.getFirst());return n>=1&&n<=item.getMaxStackSize()?"":"Use 1–"+item.getMaxStackSize()+" items.";},v->act(()->{var items=stacks(pos);items.set(slot,item.copyWithCount(Integer.parseInt(v.getFirst())));change(IslandAssets.inventory(build,pos,items,registries));})));}
    private void transform(){minecraft.gui.setScreen(new StudioChoiceScreen(this,"Island transform",List.of("Rotate clockwise","Mirror","Replace palette state"),choice->act(()->{
        if(choice.startsWith("Rotate"))change(IslandAssets.transform(build,Rotation.CLOCKWISE_90,Mirror.NONE,registries));else if(choice.equals("Mirror"))change(IslandAssets.transform(build,Rotation.NONE,Mirror.LEFT_RIGHT,registries));
        else {var states=build.cells().stream().map(c->BlockStateParser.serialize(c.state())).distinct().sorted().toList();minecraft.gui.setScreen(new StudioChoiceScreen(this,"Replace block state",states,old->minecraft.gui.setScreen(new BlockPickerScreen(this,old,picked->act(()->{var state=BlockStateParser.parseForBlock(net.minecraft.core.registries.BuiltInRegistries.BLOCK,picked,false).blockState();change(new IslandBuild(build.size(),build.markers(),build.spawnMarker(),build.cells().stream().map(c->BlockStateParser.serialize(c.state()).equals(old)?new IslandBuild.Cell(c.pos(),state,c.data()):c).toList()));})))));return;}
        minecraft.gui.setScreen(this);
    })));}
    private void saveMenu(){minecraft.gui.setScreen(new StudioChoiceScreen(this,"Island revisions",List.of("Save island revision","History / recovery"),choice->{if(choice.startsWith("History"))history();else minecraft.gui.setScreen(new StudioFormScreen(this,"Save island revision","Saved versions cannot be overwritten. Choose a new release for another revision.",List.of(new StudioFormScreen.Field("Island name","my-island"),new StudioFormScreen.Field("Release","1.0.0")),v->v.getFirst().matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}")&&v.get(1).matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,39}")?"":"Use a simple name and release.",v->act(()->{if(!ModpackRuntime.developerMode())throw new IOException("Enable local authoring and restart before saving island revisions.");status="Saved "+IslandAssets.save(ModpackRuntime.directory(),v.getFirst(),v.get(1),build,registries).getFileName();})));}));}
    private void history(){minecraft.gui.setScreen(new AuthoringHistoryScreen(this,journal,this::snapshot,this::restore,List.of()));}
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice){if(slice&&e.button()==0&&e.x()>=mapX&&e.y()>=mapY&&e.x()<mapX+mapWidth&&e.y()<mapY+mapDepth){int x=(int)((e.x()-mapX)*build.size().getX()/mapWidth),z=(int)((e.y()-mapY)*build.size().getZ()/mapDepth);selected=new BlockPos(x,layer,z);status="Selected marker cell "+selected.toShortString();return true;}return super.mouseClicked(e,twice);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float tick){
        super.extractRenderState(g,x,y,tick);g.centeredText(font,title,width/2,8,ArchitectTheme.TEXT);g.centeredText(font,Component.literal(build.size().toShortString()+" · "+build.cells().size()+" cells · spawn "+build.spawnMarker()),width/2,24,ArchitectTheme.MUTED);
        int top=64,bottom=height-62;ArchitectTheme.panel(g,left,top,left+viewWidth,bottom,0xff121d21);
        if(!slice){preview.draw(g,left+5,top+5,viewWidth-10,Math.max(12,bottom-top-22));g.text(font,font.plainSubstrByWidth(preview.sampled()?"Sampled models · exact cells in Slice view":"Block models · inspect fluids in Slice view",viewWidth-12),left+6,bottom-12,ArchitectTheme.MUTED);}
        else {double scale=Math.max(.01,Math.min((viewWidth-12)/(double)build.size().getX(),(bottom-top-12)/(double)build.size().getZ()));mapWidth=Math.max(1,(int)(scale*build.size().getX()));mapDepth=Math.max(1,(int)(scale*build.size().getZ()));mapX=left+(viewWidth-mapWidth)/2;mapY=top+(bottom-top-mapDepth)/2;
            for(int bx=0;bx<build.size().getX();bx++)for(int bz=0;bz<build.size().getZ();bz++){var pos=new BlockPos(bx,layer,bz);var state=states.get(pos);boolean floor=state==null;if(floor)state=states.get(pos.below());int color=state==null?0xff1d2c30:(floor?0x80000000:0xff000000)|state.getMapColor(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,pos).col;int x0=mapX+bx*mapWidth/build.size().getX(),z0=mapY+bz*mapDepth/build.size().getZ();g.fill(x0,z0,mapX+(bx+1)*mapWidth/build.size().getX(),mapY+(bz+1)*mapDepth/build.size().getZ(),color);if(selected.getX()==bx&&selected.getZ()==bz)ArchitectTheme.panel(g,x0,z0,mapX+(bx+1)*mapWidth/build.size().getX(),mapY+(bz+1)*mapDepth/build.size().getZ(),0x88cfb47c);}
        }
        g.text(font,font.plainSubstrByWidth(status,span),left,height-47,ArchitectTheme.MUTED);
    }
    @Override public void onClose(){if(!applied&&!initial.equals(IslandBuild.CODEC.encodeStart(JsonOps.INSTANCE,build).getOrThrow())){minecraft.gui.setScreen(new StudioConfirmScreen(answer->{if(answer)journal.close(this,()->minecraft.gui.setScreen(parent));else minecraft.gui.setScreen(this);},Component.literal("Discard island changes?"),Component.literal("Saved island revisions remain available."),Component.literal("Discard changes"),Component.literal("Keep editing")));}else journal.close(this,()->minecraft.gui.setScreen(parent));}
    @FunctionalInterface private interface Action{void run()throws Exception;}
    private void act(Action action){try{action.run();}catch(Exception error){status=Objects.toString(error.getMessage(),"Island action failed.");if(minecraft.gui.screen() instanceof StudioFormScreen form)form.showError(status);else if(minecraft.gui.screen() instanceof StudioChoiceScreen choice)choice.showError(status);}}
}
