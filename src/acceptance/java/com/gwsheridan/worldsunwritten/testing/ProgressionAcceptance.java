package com.gwsheridan.worldsunwritten.testing;

import com.gwsheridan.worldsunwritten.config.EndProgression;
import com.gwsheridan.worldsunwritten.worldgen.ManagedGenerator;
import com.gwsheridan.worldsunwritten.worldgen.GenerationPreview;
import com.mojang.serialization.JsonOps;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

final class ProgressionAcceptance {
    private ProgressionAcceptance() {}
    static void register(RegisterCommandsEvent event) {
        var root = Commands.literal("dragon_test").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
        for (String action : new String[] {"state", "audit", "kill", "ritual", "abort", "gates", "entry", "exit", "travel", "unsafe", "rewards"}) {
            root.then(Commands.literal(action).executes(context -> {
                var server = context.getSource().getServer(); var level = server.getLevel(Level.END);
                try {
                    var fight = level.getDragonFight();
                    if (fight == null) throw new AssertionError("No native fight controller");
                    var json = EnderDragonFight.CODEC.encodeStart(JsonOps.INSTANCE, fight).getOrThrow().getAsJsonObject();
                    if(action.equals("unsafe")) {
                        var source=new BlockPos(4000,100,4000);var target=new BlockPos(5000,150,5000);
                        for(BlockPos clear:BlockPos.betweenClosed(target.offset(-17,-17,-17),target.offset(17,17,17)))level.setBlock(clear,Blocks.AIR.defaultBlockState(),2);
                        net.minecraft.world.level.levelgen.feature.EndGatewayFeature.knownExit(target,true).place(level,level.getChunkSource().getGenerator(),net.minecraft.util.RandomSource.create(),source);
                        var gateway=(net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity)level.getBlockEntity(source);
                        var unexpected=gateway.getPortalPosition(level,source);
                        if(unexpected!=null)throw new AssertionError("Unsafe gateway accepted "+unexpected+" floor="+level.getBlockState(BlockPos.containing(unexpected).below()));
                        level.setBlockAndUpdate(target.above(15),Blocks.END_STONE.defaultBlockState());
                        var safe=gateway.getPortalPosition(level,source);
                        if(safe==null || !com.gwsheridan.worldsunwritten.spawn.SafeSpawns.safe(level,BlockPos.containing(safe)) || safe.y!=166)throw new AssertionError("Safe vertical fallback missing");
                        context.getSource().sendSuccess(()->Component.literal("DRAGON_GATEWAY_SAFETY_PASS"),false);
                    } else if (action.equals("entry") || action.equals("exit")) {
                        for(var player:server.getPlayerList().getPlayers()) {
                            var from=action.equals("entry") ? server.overworld() : level;
                            var transition=((net.minecraft.world.level.block.EndPortalBlock)Blocks.END_PORTAL).getPortalDestination(from,player,BlockPos.ZERO);
                            if(transition==null || player.teleport(transition)==null) throw new AssertionError("Portal transition failed");
                        }
                    } else if(action.equals("rewards")) {
                        int eggs=0;for(BlockPos pos:BlockPos.betweenClosed(new BlockPos(-8,50,-8),new BlockPos(8,100,8)))if(level.getBlockState(pos).is(Blocks.DRAGON_EGG))eggs++;
                        int xp=level.getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class,new net.minecraft.world.phys.AABB(-128,0,-128,128,256,128)).stream().mapToInt(net.minecraft.world.entity.ExperienceOrb::getValue).sum();
                        if(eggs!=1 || xp<500)throw new AssertionError("Rewards eggs="+eggs+" xp="+xp);
                        final String reward="DRAGON_REWARDS_PASS eggs="+eggs+" xp="+xp;context.getSource().sendSuccess(()->Component.literal(reward),false);
                    } else if(action.equals("travel")) {
                        var player=server.getPlayerList().getPlayers().getFirst();BlockPos gate=null;
                        for(int x=-100;x<=100 && gate==null;x++)for(int z=-100;z<=100;z++)if(level.getBlockState(new BlockPos(x,75,z)).is(Blocks.END_GATEWAY)){gate=new BlockPos(x,75,z);break;}
                        if(gate==null)throw new AssertionError("Missing gateway");
                        var transition=((net.minecraft.world.level.block.EndGatewayBlock)Blocks.END_GATEWAY).getPortalDestination(level,player,gate);
                        if(transition==null || player.teleport(transition)==null)throw new AssertionError("Outbound gateway failed");
                        BlockPos arrival=player.blockPosition();
                        if(Math.abs(arrival.getX())<500 && Math.abs(arrival.getZ())<500 || !com.gwsheridan.worldsunwritten.spawn.SafeSpawns.safe(level,arrival))throw new AssertionError("Unsafe outer gateway");
                        BlockPos returning=null;
                        for(BlockPos candidate:BlockPos.betweenClosed(arrival.offset(-20,-20,-20),arrival.offset(20,20,20)))if(level.getBlockState(candidate).is(Blocks.END_GATEWAY)){returning=candidate.immutable();break;}
                        if(returning==null)throw new AssertionError("Missing return gateway");
                        var back=((net.minecraft.world.level.block.EndGatewayBlock)Blocks.END_GATEWAY).getPortalDestination(level,player,returning);
                        if(back==null || player.teleport(back)==null || !EndProgression.contains(player.blockPosition().getX(),player.blockPosition().getZ()))throw new AssertionError("Return gateway failed");
                        context.getSource().sendSuccess(()->Component.literal("DRAGON_GATEWAY_ROUNDTRIP_PASS"),false);
                    } else if (action.equals("audit")) {
                        var ops=net.minecraft.resources.RegistryOps.create(JsonOps.INSTANCE,server.registryAccess());
                        var legacyJson=com.google.gson.JsonParser.parseString("{\"min_y\":0,\"height\":256}");
                        var legacy=com.gwsheridan.worldsunwritten.config.DimensionProfile.CODEC.parse(ops,legacyJson).getOrThrow();
                        if(legacy.endProgression().isPresent() || !legacyJson.equals(com.gwsheridan.worldsunwritten.config.DimensionProfile.CODEC.encodeStart(ops,legacy).getOrThrow()))throw new AssertionError("Legacy encoding changed");
                        for(String invalid:new String[]{"{\"version\":2,\"mode\":\"vanilla_arena\"}","{\"version\":1,\"mode\":\"unknown\"}"})if(EndProgression.CODEC.parse(ops,com.google.gson.JsonParser.parseString(invalid)).isSuccess())throw new AssertionError("Invalid arena policy accepted");
                        var generator = level.getChunkSource().getGenerator();
                        var arena = ((ManagedGenerator) generator).endProgression().orElseThrow();
                        var preview = GenerationPreview.forStem(server.getWorldGenSettings().dimensions().dimensions().get(net.minecraft.world.level.dimension.LevelStem.END), level.getSeed()).orElseThrow();
                        for (int x : new int[] {-257,-256,-16,16,255,256,512}) {
                            var chunk = level.getChunk(x >> 4, 0);
                            int expected = generator.getBaseHeight(x, 0, net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, level, level.getChunkSource().randomState());
                            if (preview.sample(x,0).surfaceY() != expected-1) throw new AssertionError("Preview mismatch " + x);
                            if (EndProgression.contains(x, 0)) {
                                int nativeHeight = arena.generator().getBaseHeight(x,0,net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,level,arena.state(level.getSeed()));
                                if (nativeHeight != expected || !chunk.getNoiseBiome(x >> 2,16,0).is(net.minecraft.world.level.biome.Biomes.THE_END)) throw new AssertionError("Arena sample mismatch " + x);
                                var column=arena.generator().getBaseColumn(x,0,level,arena.state(level.getSeed()));
                                for(int y=0;y<256;y++)if(level.getBlockState(new BlockPos(x,y,0))!=column.getBlock(y))throw new AssertionError("Native block mismatch "+x+","+y+" actual="+level.getBlockState(new BlockPos(x,y,0))+" expected="+column.getBlock(y));
                            } else if (generator instanceof com.gwsheridan.worldsunwritten.worldgen.VoidGenerator ? !level.getBlockState(new BlockPos(x,40,0)).isAir() : !level.getBlockState(new BlockPos(x,40,0)).is(Blocks.PURPUR_BLOCK)) throw new AssertionError("Wrong outer terrain " + x);
                        }
                        for (int cx=-16;cx<16;cx++) for(int cz=-16;cz<16;cz++) {
                            var chunk = level.getChunk(cx,cz);
                            if(chunk.getAllStarts().values().stream().anyMatch(s -> s.isValid())) throw new AssertionError("Custom structure in arena");
                        }
                        context.getSource().sendSuccess(() -> Component.literal("DRAGON_ARENA_AUDIT_PASS"),false);
                    } else if (action.equals("kill")) {
                        var player = server.getPlayerList().getPlayers().getFirst();
                        var dragon = level.getDragons().getFirst();
                        dragon.getPhaseManager().setPhase(EnderDragonPhase.SITTING_SCANNING);
                        dragon.hurt(level, dragon.head, level.damageSources().playerAttack(player), 10000);
                    } else if (action.equals("ritual")) {
                        var a = json.getAsJsonArray("exit_portal_location"); var pos = new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt()).above();
                        for (Direction direction : Direction.Plane.HORIZONTAL) {
                            var p=pos.relative(direction,3); level.addFreshEntity(new EndCrystal(level,p.getX()+0.5,p.getY(),p.getZ()+0.5));
                        }
                        fight.tryRespawn();
                    } else if (action.equals("abort")) {
                        var a=json.getAsJsonArray("exit_portal_location"); var p=new BlockPos(a.get(0).getAsInt()+3,a.get(1).getAsInt()+1,a.get(2).getAsInt());
                        level.getEntitiesOfClass(EndCrystal.class,new net.minecraft.world.phys.AABB(p).inflate(1)).getFirst().kill(level);
                    } else if (action.equals("gates")) {
                        for(int x=-100;x<=100;x++) for(int z=-100;z<=100;z++) if(level.getBlockState(new BlockPos(x,75,z)).is(Blocks.END_GATEWAY)) {
                            final String gate="DRAGON_GATE "+x+" 75 "+z;context.getSource().sendSuccess(()->Component.literal(gate),false);
                        }
                    }
                    final String state="DRAGON_STATE "+EnderDragonFight.CODEC.encodeStart(JsonOps.INSTANCE,fight).getOrThrow()+" ENTITIES "+level.getDragons().size();
                    context.getSource().sendSuccess(()->Component.literal(state),false);
                    return 1;
                } catch (Throwable error) { throw new IllegalStateException("DRAGON_TEST_FAILED "+action,error); }
            }));
        }
        event.getDispatcher().register(root);
    }
}
