package com.gwsheridan.worldarchitect.packtest;

import com.gwsheridan.worldarchitect.modpack.*;
import com.gwsheridan.worldarchitect.skyblock.*;
import com.gwsheridan.worldarchitect.worldgen.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Optional disposable-world fixture; never included in the gameplay JAR. */
@Mod("wa_pack_test")
public final class PackTestMod {
    public PackTestMod(){NeoForge.EVENT_BUS.addListener(PackTestMod::register);}
    private static void register(RegisterCommandsEvent event){event.getDispatcher().register(Commands.literal("wa_pack_test").requires(source->source.getEntity()==null&&Commands.hasPermission(Commands.LEVEL_OWNERS).test(source)).then(Commands.argument("stage",com.mojang.brigadier.arguments.IntegerArgumentType.integer(1,2)).executes(c->{try{check(c.getSource().getServer(),com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"stage"));return 1;}catch(Throwable error){error.printStackTrace();System.err.println("WA_PACK_TEST_FAILED "+error.getMessage());return 0;}})));}
    private static void check(MinecraftServer server,int stage)throws Exception{
        require(!ModpackRuntime.developerMode(),"Export enabled developer mode");var bundle=WorldBundles.active(server).orElseThrow(()->new IllegalStateException("No pack was installed"));
        String bundleHash=PresetInheritance.fingerprint(BundleManifest.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,bundle.manifest()).getOrThrow().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));var data=server.getWorldPath(LevelResource.DATA);var proof=data.resolve("worldarchitect-pack-test.dat");var before=stage==2?NbtIo.readCompressed(proof,NbtAccounter.create(2*1024*1024)):new CompoundTag();var after=new CompoundTag();after.putString("bundle",bundleHash);int markers=0,cells=0,containers=0;
        require(stage==1||before.getStringOr("bundle","").equals(bundleHash),"Preset changed after restart");
        var consumed=stage==2?before.getCompoundOrEmpty("consumed"):new CompoundTag();
        for(var level:server.getAllLevels())if(level.getChunkSource().getGenerator() instanceof VoidGenerator sky&&sky.layout().isPresent()){
            var layout=sky.layout().orElseThrow();for(int slot:new LinkedHashSet<>(List.of(0,Math.min(1,layout.personalSlots())))){
                var marker=IslandSpawns.position(level,layout,slot).orElseThrow(()->new IllegalStateException("Unsafe island marker"));require(level.getBlockState(marker).isAir()&&level.getBlockState(marker.above()).isAir(),"Marker lacks headroom");markers++;
                if(sky instanceof SkyblockGenerator rich){rich.validateAssets(server.registryAccess());var build=rich.catalog().entry(slot,server.getWorldGenSettings().options().seed()).build();var origin=layout.origin(slot);
                    for(var cell:build.cells()){var p=origin.offset(cell.pos());require(level.getBlockState(p).equals(cell.state()),"Island block differs at "+p);cells++;if(cell.data().isPresent()){var entity=level.getBlockEntity(p);require(entity!=null,"Missing saved metadata");if(entity instanceof Container container){containers++;for(var tag:cell.data().orElseThrow().getListOrEmpty("Items")){var item=(CompoundTag)tag;int index=item.getIntOr("Slot",-1);var expected=ItemStack.CODEC.parse(net.minecraft.resources.RegistryOps.create(NbtOps.INSTANCE,server.registryAccess()),item).getOrThrow();String key=level.dimension().identifier()+"/"+p.toShortString()+"/"+index;var actual=container.getItem(index);int count=expected.getCount()-(consumed.contains(key)?1:0);require(actual.getCount()==count&&(count==0||ItemStack.isSameItemSameComponents(expected,actual)),"Starter contents changed or refilled");if(stage==1){container.removeItem(index,1);container.setChanged();consumed.putBoolean(key,true);}}}}}
                }
            }
            if(sky instanceof SkyblockGenerator rich&&rich.catalog().personalSlots()>0)IslandAllocations.prepareCatalog(data,rich.catalog(),server.getWorldGenSettings().options().seed(),false);
            if(sky.endProgression().isEmpty()){var empty=level.getChunk(-1024,-1024);for(var section:empty.getSections())require(section.hasOnlyAir(),"Void contains unexpected terrain");}
        }
        for(String name:List.of("worldarchitect-generation-v1.dat","minecraft/world_gen_settings.dat")){var path=data.resolve(name);require(Files.isRegularFile(path),"Generation snapshot missing");String hash=PresetInheritance.fingerprint(Files.readAllBytes(path));after.putString(name,hash);require(stage==1||hash.equals(before.getStringOr(name,"")),"Generation snapshot changed after restart");}
        if(bundle.manifest().pack().isPresent()){
            var policy=bundle.manifest().pack().orElseThrow();var current=com.gwsheridan.worldarchitect.spawn.WorldAdministration.get(server);var ow=net.minecraft.resources.Identifier.parse("minecraft:overworld");var destination=Optional.of(com.gwsheridan.worldarchitect.spawn.SpawnPolicy.simple(com.gwsheridan.worldarchitect.spawn.SpawnPolicy.Kind.FIXED,ow,new BlockPos(0,128,0)));
            var protection=current.protection();var changes=Map.of("initial",current.withPolicy("initial",destination,ow),"respawn",current.withPolicy("respawn",destination,ow),"portals",current.withPolicy("portal",destination,ow),"protection",current.withProtection(new com.gwsheridan.worldarchitect.spawn.WorldAdministration.Protection(!protection.breaking(),protection.placement(),protection.containers(),protection.shared(),protection.containerBlocks())));
            var settingsFile=data.resolve("worldarchitect-administration-v1.dat");var settingsBytes=Files.readAllBytes(settingsFile);
            for(var entry:changes.entrySet())if(!policy.allows(bundle.manifest().lock(),entry.getKey())&&!current.equals(entry.getValue())){try{com.gwsheridan.worldarchitect.spawn.WorldAdministration.save(server,entry.getValue());throw new IllegalStateException("Locked runtime group accepted a change: "+entry.getKey());}catch(IllegalStateException expected){require(expected.getMessage().contains("locked"),"Unexpected policy failure");}require(Arrays.equals(settingsBytes,Files.readAllBytes(settingsFile)),"Rejected runtime edit wrote settings");}
        }
        after.put("consumed",consumed);NbtIo.writeCompressed(after,proof);
        System.out.println("WA_PACK_TEST_PASS stage="+stage+" markers="+markers+" cells="+cells+" containers="+containers+" lock="+bundle.manifest().lock().getSerializedName());
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
