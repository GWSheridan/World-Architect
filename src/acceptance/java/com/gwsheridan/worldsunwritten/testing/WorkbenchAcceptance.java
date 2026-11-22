package com.gwsheridan.worldsunwritten.testing;

import com.google.gson.*;
import com.gwsheridan.worldsunwritten.inspection.SavedWorldInspection;
import com.gwsheridan.worldsunwritten.modpack.ModpackRuntime;
import com.gwsheridan.worldsunwritten.spawn.SafeSpawns;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Optional fixture only. Gameplay installations cannot start workbench jobs. */
final class WorkbenchAcceptance {
    static void check(MinecraftServer server)throws Exception {
        var path=ModpackRuntime.directory().resolve("workbench-job.json");if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>262144)throw new IllegalArgumentException("Missing bounded workbench job");
        var job=JsonParser.parseString(Files.readString(path)).getAsJsonObject();long started=System.nanoTime();int chunks=0;
        var dims=job.getAsJsonArray("dimensions");require(dims.size()>0&&dims.size()<=16,"Dimension budget exceeded");var selected=new HashSet<String>();
        for(var dim:dims){String id=dim.getAsString();require(selected.add(id)&&server.getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(id)))!=null,"Missing or repeated dimension "+id);}
        var regions=job.getAsJsonArray("regions");require(regions.size()<=32,"Region budget exceeded");
        // Validate the entire request before generating any requested chunk.
        for(var value:regions){var r=value.getAsJsonObject();require(selected.contains(r.get("dimension").getAsString()),"Unselected region dimension");int x=r.get("chunk_x").getAsInt(),z=r.get("chunk_z").getAsInt(),w=r.get("width").getAsInt(),d=r.get("depth").getAsInt();require(Math.abs((long)x)<=1874980&&Math.abs((long)z)<=1874980&&w>=1&&w<=16&&d>=1&&d<=16,"Region bounds exceeded");chunks+=w*d;}require(chunks<=256,"Chunk budget exceeded");
        var probes=job.getAsJsonArray("probes");require(probes.size()<=128,"Probe budget exceeded");for(var value:probes){var p=value.getAsJsonObject();require(selected.contains(p.get("dimension").getAsString()),"Unselected probe dimension");require(Math.abs(p.get("x").getAsLong())<=29999680&&Math.abs(p.get("z").getAsLong())<=29999680&&p.get("y").getAsInt()>=-2032&&p.get("y").getAsInt()<=2031,"Probe bounds exceeded");}
        require(SavedWorldInspection.read(server.getWorldPath(LevelResource.DATA)).healthy(),"Saved generation is inconsistent");
        for(var value:regions){var r=value.getAsJsonObject();var level=server.getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(r.get("dimension").getAsString())));int x=r.get("chunk_x").getAsInt(),z=r.get("chunk_z").getAsInt();for(int dx=0;dx<r.get("width").getAsInt();dx++)for(int dz=0;dz<r.get("depth").getAsInt();dz++)level.getChunk(x+dx,z+dz);}
        for(var value:probes){var p=value.getAsJsonObject();var level=server.getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(p.get("dimension").getAsString())));var pos=new BlockPos(p.get("x").getAsInt(),p.get("y").getAsInt(),p.get("z").getAsInt());require(level.isInsideBuildHeight(pos),"Probe is outside dimension height");var chunk=level.getChunkAt(pos);
            if(p.has("block"))require(level.getBlockState(pos)==net.minecraft.commands.arguments.blocks.BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK,p.get("block").getAsString(),false).blockState(),"Block probe failed at "+pos);
            if(p.has("biome"))require(level.getBiome(pos).unwrapKey().orElseThrow().identifier().equals(Identifier.parse(p.get("biome").getAsString())),"Biome probe failed at "+pos);
            if(p.has("structure")){var structure=server.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,Identifier.parse(p.get("structure").getAsString()))).value();var start=chunk.getStartForStructure(structure);require(start!=null&&start.isValid(),"Structure-start probe failed at "+pos);}
        }
        if(job.get("safe_spawn").getAsBoolean()){var spawn=server.overworld().getRespawnData();var level=server.getLevel(spawn.dimension());require(level!=null&&SafeSpawns.nearby(level,spawn.pos()).isPresent(),"No safe initial spawn within four blocks of the saved spawn");}
        var report=new JsonObject();report.addProperty("format",1);report.addProperty("status","passed");report.addProperty("name",job.get("name").getAsString());report.addProperty("requested_chunks",chunks);report.addProperty("probes",probes.size());report.addProperty("elapsed_ms",(System.nanoTime()-started)/1000000L);report.addProperty("heap_used_at_completion_bytes",Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory());
        Files.writeString(ModpackRuntime.directory().resolve("workbench-observed.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");System.out.println("WA_WORKBENCH_PASS chunks="+chunks+" probes="+probes.size());
    }
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
