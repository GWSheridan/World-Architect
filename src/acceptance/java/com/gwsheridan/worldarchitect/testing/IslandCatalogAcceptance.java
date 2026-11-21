package com.gwsheridan.worldarchitect.testing;

import com.google.gson.*;
import com.gwsheridan.worldarchitect.modpack.*;
import com.gwsheridan.worldarchitect.skyblock.*;
import com.gwsheridan.worldarchitect.spawn.*;
import com.gwsheridan.worldarchitect.structure.*;
import com.gwsheridan.worldarchitect.worldgen.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;

/** Rich generation is checked against the immutable assets actually installed in this world. */
final class IslandCatalogAcceptance {
    static void check(MinecraftServer server,int stage)throws Exception{
        require(server.overworld().getChunkSource().getGenerator() instanceof SkyblockGenerator,"Expected catalog generator");
        var generator=(SkyblockGenerator)server.overworld().getChunkSource().getGenerator();generator.validateAssets(server.registryAccess());
        var catalog=generator.catalog();var layout=generator.layout(server.getWorldGenSettings().options().seed());var data=server.getWorldPath(LevelResource.DATA);
        var report=new TreeMap<String,String>();int cells=0,containers=0,markers=0;var consumed=new HashSet<String>();var file=data.resolve("worldarchitect-island-test.json");JsonObject previous=stage==2?JsonParser.parseString(Files.readString(file)).getAsJsonObject():new JsonObject();if(stage==2)previous.getAsJsonArray("consumed").forEach(v->consumed.add(v.getAsString()));
        // Stage two also opens the last previously unexplored slot, whose starter contents must be fresh.
        var slots=new LinkedHashSet<Integer>(List.of(0,Math.min(1,catalog.personalSlots()),Math.min(2,catalog.personalSlots())));if(stage==2)slots.add(catalog.personalSlots());
        for(int slot:slots){var build=catalog.entry(slot,server.getWorldGenSettings().options().seed()).build();var origin=layout.origin(slot);var signature=new StringBuilder();
            for(var cell:build.cells()){
                var pos=origin.offset(cell.pos());var actual=server.overworld().getBlockState(pos);require(actual.equals(cell.state()),"Island state differs at "+pos+": "+actual+" / "+cell.state());signature.append(pos).append(actual);cells++;
                if(cell.data().isPresent()){
                    var entity=server.overworld().getBlockEntity(pos);require(entity!=null,"Missing island metadata at "+pos);var saved=entity.saveWithoutMetadata(server.registryAccess());
                    if(entity instanceof Container container){containers++;var expected=cell.data().orElseThrow().getListOrEmpty("Items");for(var value:expected){var tag=(CompoundTag)value;int index=tag.getIntOr("Slot",-1);var stack=ItemStack.CODEC.parse(net.minecraft.resources.RegistryOps.create(NbtOps.INSTANCE,server.registryAccess()),tag).getOrThrow();var actualStack=container.getItem(index);int count=stack.getCount()-(consumed.contains(slot+":"+cell.pos().toShortString()+":"+index)?1:0);require(actualStack.getCount()==count&&(count==0||ItemStack.isSameItemSameComponents(actualStack,stack)),"Starter contents changed or refilled at "+pos+" slot "+index);}
                        if(stage==1&&!expected.isEmpty()){int index=((CompoundTag)expected.getFirst()).getIntOr("Slot",-1);container.removeItem(index,1);container.setChanged();consumed.add(slot+":"+cell.pos().toShortString()+":"+index);}
                    }else for(String key:cell.data().orElseThrow().keySet())if(!key.equals("id"))require(Objects.equals(saved.get(key),cell.data().orElseThrow().get(key)),"Decorative metadata differs at "+pos+" / "+key);
                }
            }
            for(var marker:build.markers().values()){var pos=origin.offset(marker);require(server.overworld().getBlockState(pos).isAir()&&server.overworld().getBlockState(pos.above()).isAir()&&!server.overworld().getBlockState(pos.below()).isAir(),"Unsafe saved island marker");markers++;}
            require(IslandSpawns.position(server.overworld(),layout,slot).isPresent(),"Missing safe island spawn");report.put(Integer.toString(slot),PresetInheritance.fingerprint(signature.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
        for(int cx:new int[]{-128,1024})for(int cz:new int[]{-128,1024}){var chunk=server.overworld().getChunk(cx,cz);for(var section:chunk.getSections())require(section.hasOnlyAir(),"Far void contains terrain");}
        if(stage==1){var root=new JsonObject();root.add("blocks",new Gson().toJsonTree(report));root.add("consumed",new Gson().toJsonTree(consumed));Files.writeString(file,root.toString(),StandardOpenOption.CREATE_NEW);}else {for(var e:previous.getAsJsonObject("blocks").entrySet())require(e.getValue().getAsString().equals(report.get(e.getKey())),"Saved island blocks changed after restart");}
        if(catalog.personalSlots()>0)IslandAllocations.prepareCatalog(data,catalog,server.getWorldGenSettings().options().seed(),false);
        if(catalog.mode().equals("choice"))for(var player:server.getPlayerList().getPlayers())require(IslandSelections.assigned(server,generator,player.getUUID(),player.getPlainTextName())>0,"Connected player remained unassigned: "+player.getPlainTextName());
        require(WorldBundles.active(server).orElseThrow().manifest().pack().isPresent(),"Runtime defaults were not installed");
        var policy=WorldBundles.active(server).orElseThrow().manifest().pack().orElseThrow();var current=WorldAdministration.get(server);var before=Files.readAllBytes(data.resolve("worldarchitect-administration-v1.dat"));
        if(!catalog.teams()&&catalog.personalSlots()>0&&policy.allows(WorldBundles.active(server).orElseThrow().manifest().lock(),"initial")){
            var ledger=Files.readAllBytes(data.resolve("worldarchitect-islands-v1.dat"));var teamPolicy=SpawnPolicy.simple(SpawnPolicy.Kind.TEAM,net.minecraft.resources.Identifier.parse("minecraft:overworld"),BlockPos.ZERO);
            try{com.gwsheridan.worldarchitect.api.SpawnApi.set(server.createCommandSourceStack(),com.gwsheridan.worldarchitect.api.SpawnApi.Phase.INITIAL,teamPolicy);throw new AssertionError("API accepted a team policy for personal allocation");}catch(java.io.IOException expected){require(expected.getMessage().contains("team island allocation"),"Unexpected team policy rejection");}
            require(server.getCommands().getDispatcher().execute("wa_spawn set initial team minecraft:overworld",server.createCommandSourceStack())==0,"Command accepted a team policy for personal allocation");require(Arrays.equals(before,Files.readAllBytes(data.resolve("worldarchitect-administration-v1.dat")))&&Arrays.equals(ledger,Files.readAllBytes(data.resolve("worldarchitect-islands-v1.dat"))),"Rejected team policy changed settings or ownership");
        }
        if(!policy.allows(WorldBundles.active(server).orElseThrow().manifest().lock(),"protection")){var old=current.protection();try{WorldAdministration.save(server,current.withProtection(new WorldAdministration.Protection(!old.breaking(),old.placement(),old.containers(),old.shared(),old.containerBlocks())));throw new AssertionError("Locked policy accepted a change");}catch(IllegalStateException expected){}require(Arrays.equals(before,Files.readAllBytes(data.resolve("worldarchitect-administration-v1.dat"))),"Rejected policy edit changed saved data");}
        System.out.println("WA18_ISLAND_CATALOG_PASS stage="+stage+" cells="+cells+" containers="+containers+" markers="+markers+" empty_chunks=4 starter_no_refill=true");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
