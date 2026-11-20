package com.gwsheridan.worldarchitect.testing;

import com.gwsheridan.worldarchitect.structure.NativeTemplate;

import java.io.IOException;
import java.util.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Dedicated-server assertions in a separate mod, never in the gameplay JAR. */
@Mod("wa_validation")
public final class PlacementAcceptance {
    public PlacementAcceptance() { NeoForge.EVENT_BUS.addListener(PlacementAcceptance::register); NeoForge.EVENT_BUS.addListener(ProgressionAcceptance::register); }
    private static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wa_validate").requires(Commands.hasPermission(Commands.LEVEL_OWNERS))
                .then(Commands.literal("workbench").executes(c -> {try {WorkbenchAcceptance.check(c.getSource().getServer());return 1;}catch(Exception e){throw new IllegalStateException("WA_WORKBENCH_FAILED",e);}}))
                .then(Commands.literal("cycle13").executes(c -> { try { Cycle13Acceptance.fresh(c.getSource().getServer()); return 1; } catch(Exception e) {throw new IllegalStateException("WA_CYCLE13_FAILED",e);} }))
                .then(Commands.literal("cycle13_restart").executes(c -> { try { Cycle13Acceptance.restart(c.getSource().getServer()); return 1; } catch(Exception e) {throw new IllegalStateException("WA_CYCLE13_RESTART_FAILED",e);} }))
                .then(Commands.literal("regional_generation").then(Commands.argument("stage",com.mojang.brigadier.arguments.IntegerArgumentType.integer(1,2)).executes(c -> {Cycle13Acceptance.regions(c.getSource().getServer(),com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"stage"));return 1;})))
                .then(Commands.literal("pool_generation").then(Commands.argument("structure",net.minecraft.commands.arguments.IdentifierArgument.id()).then(Commands.argument("dimension",net.minecraft.commands.arguments.IdentifierArgument.id()).then(Commands.argument("region",com.mojang.brigadier.arguments.IntegerArgumentType.integer(1,10000)).executes(c->{try{Cycle13Acceptance.generated(c.getSource().getServer(),net.minecraft.commands.arguments.IdentifierArgument.getId(c,"structure"),net.minecraft.commands.arguments.IdentifierArgument.getId(c,"dimension"),com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"region"));return 1;}catch(Exception e){throw new IllegalStateException("WA_POOL_GENERATION_FAILED",e);}})))))
                .then(Commands.literal("structures").executes(c -> {
                    try { PreviewAcceptance.check(c.getSource().getServer()); RuleInspectionAcceptance.check(c.getSource().getServer()); AssemblyAcceptance.fresh(c.getSource().getServer()); Cycle13Acceptance.fresh(c.getSource().getServer()); structures(c.getSource().getServer().overworld()); c.getSource().sendSuccess(() -> Component.literal("WA_VALIDATION_STRUCTURES_PASS"), false); return 1; }
                    catch (Throwable e) { e.printStackTrace(); throw new IllegalStateException("WA_VALIDATION_STRUCTURES_FAILED", e); }
                }))
                .then(Commands.literal("assembly_generation").then(Commands.argument("structure",net.minecraft.commands.arguments.IdentifierArgument.id()).then(Commands.argument("dimension",net.minecraft.commands.arguments.IdentifierArgument.id()).then(Commands.argument("region",com.mojang.brigadier.arguments.IntegerArgumentType.integer(1,10000)).executes(c->{try{AssemblyAcceptance.generated(c.getSource().getServer(),net.minecraft.commands.arguments.IdentifierArgument.getId(c,"structure"),net.minecraft.commands.arguments.IdentifierArgument.getId(c,"dimension"),com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"region"));return 1;}catch(Exception e){throw new IllegalStateException("WA_ASSEMBLY_GENERATION_FAILED",e);}})))))
                .then(Commands.literal("restart").executes(c -> {
                    try { AssemblyAcceptance.restart(c.getSource().getServer()); Cycle13Acceptance.restart(c.getSource().getServer()); } catch (Exception e) { throw new IllegalStateException(e); }
                    var player = player(c.getSource().getServer().overworld());
                    denied(() -> callStatic("PlacementHistory", "undo", player));
                    c.getSource().sendSuccess(() -> Component.literal("WA_VALIDATION_RESTART_PASS"), false); return 1;
                })));
    }
    private static ServerPlayer player(net.minecraft.server.level.ServerLevel level) {
        return FakePlayerFactory.get(level, new GameProfile(UUID.fromString("22ddcdae-aeb9-4ca1-a868-8386377dd4db"), "ValidationAuthor"));
    }
    private static void structures(net.minecraft.server.level.ServerLevel level) throws Exception {
        var player = player(level); player.setPos(4000, 220, 4000);
        BlockPos origin = new BlockPos(4096, 200, 4096); level.getChunkAt(origin);
        for (var p : BlockPos.betweenClosed(origin, origin.offset(31, 31, 31))) level.setBlock(p, Blocks.AIR.defaultBlockState(), 18);
        var template = new NativeTemplate(new BlockPos(3, 4, 3), List.of(
                new NativeTemplate.Cell(new BlockPos(1,0,1), Blocks.STONE.defaultBlockState(), null),
                new NativeTemplate.Cell(new BlockPos(0,0,0), Blocks.OAK_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.EAST), null),
                new NativeTemplate.Cell(new BlockPos(2,0,0), Blocks.CHEST.defaultBlockState(), null)));
        var entry = callStatic("LibraryEntry", "read", template, new net.minecraft.nbt.CompoundTag(), "Acceptance", "Fixture");
        entry = call(entry, "withMarker", "spawn", construct("LibraryEntry$Marker", new BlockPos(1,1,1), Direction.NORTH));
        final var selectedEntry = entry;
        var place = construct("TemplatePlacement", origin, Rotation.CLOCKWISE_90, Mirror.FRONT_BACK);
        level.getChunkAt(origin.offset(3,0,3));
        require(((List<?>)call(place, "plan", level, selectedEntry, player)).size() == 3, "Dry run differs from placement");
        require(level.getBlockState(origin).isAir(), "Dry run changed blocks");
        require((int)call(place, "place", level, selectedEntry, player) == 3, "Missing placed cells");
        for (var cell : template.blocks()) require(level.getBlockState((BlockPos)call(place, "position", template.size(), cell.pos())) == call(place, "state", cell.state()), "Rotation/mirror lost a state");
        var chest = (BlockPos)call(place, "position", template.size(), new BlockPos(2,0,0));
        var container = (Container)level.getBlockEntity(chest); container.setItem(0, new ItemStack(Items.DIAMOND));
        denied(() -> callStatic("PlacementHistory", "undo", player)); require(container.getItem(0).is(Items.DIAMOND), "Undo changed an occupied chest");
        container.clearContent();
        require((int)callStatic("PlacementHistory", "undo", player) == 3, "Undo omitted cells");
        for (var cell : template.blocks()) require(level.getBlockState((BlockPos)call(place, "position", template.size(), cell.pos())).isAir(), "Undo left blocks");
        call(place, "place", level, selectedEntry, player);
        var changed = (BlockPos)call(place, "position", template.size(), new BlockPos(1,0,1)); level.setBlock(changed, Blocks.GOLD_BLOCK.defaultBlockState(), 18);
        denied(() -> callStatic("PlacementHistory", "undo", player)); require(level.getBlockState(changed).is(Blocks.GOLD_BLOCK), "Undo overwrote another edit");
        level.setBlock(changed, Blocks.STONE.defaultBlockState(), 18); callStatic("PlacementHistory", "undo", player);
        level.setBlock(origin, Blocks.GOLD_BLOCK.defaultBlockState(), 18);
        var collision = construct("TemplatePlacement", origin, Rotation.NONE, Mirror.NONE);
        denied(() -> call(collision, "place", level, selectedEntry, player));
        require(level.getBlockState(origin).is(Blocks.GOLD_BLOCK) && level.getBlockState(origin.offset(1,0,1)).isAir(), "Failed placement made partial changes");
        level.setBlock(origin, Blocks.AIR.defaultBlockState(), 18);
        var brokenTag=new net.minecraft.nbt.CompoundTag();brokenTag.putString("invalid", "fault injection");
        var faulty=callStatic("LibraryEntry", "read", new NativeTemplate(new BlockPos(2,3,1),List.of(
                new NativeTemplate.Cell(BlockPos.ZERO,Blocks.STONE.defaultBlockState(),null),
                new NativeTemplate.Cell(new BlockPos(1,0,0),Blocks.STONE.defaultBlockState(),brokenTag))),new net.minecraft.nbt.CompoundTag(),"Rollback","Fixture");
        denied(()->call(collision,"place",level,faulty,player));
        require(level.getBlockState(origin).isAir()&&level.getBlockState(origin.east()).isAir(),"Metadata application failure did not roll back every block");
        var single = callStatic("LibraryEntry", "read", new NativeTemplate(new BlockPos(1,3,1), List.of(new NativeTemplate.Cell(BlockPos.ZERO, Blocks.STONE.defaultBlockState(), null))), new net.minecraft.nbt.CompoundTag(), "Limit", "Fixture");
        for (int i=0;i<11;i++) call(construct("TemplatePlacement", origin.offset(i,10,0), Rotation.NONE, Mirror.NONE), "place", level, single, player);
        for (int i=0;i<10;i++) require((int)callStatic("PlacementHistory", "undo", player)==1, "History evicted too early");
        denied(() -> callStatic("PlacementHistory", "undo", player)); require(level.getBlockState(origin.offset(0,10,0)).is(Blocks.STONE), "History exceeded ten operations");
        var cube=new ArrayList<NativeTemplate.Cell>();for(var pos:BlockPos.betweenClosed(BlockPos.ZERO,new BlockPos(31,31,31)))cube.add(new NativeTemplate.Cell(pos,Blocks.STONE.defaultBlockState(),null));
        var large=callStatic("LibraryEntry","read",new NativeTemplate(new BlockPos(32,32,32),cube),new net.minecraft.nbt.CompoundTag(),"Budget","Fixture");
        ServerPlayer first=null;
        for(int i=0;i<5;i++) {
            var author=FakePlayerFactory.get(level,new GameProfile(new UUID(42,i+1),"Budget"+i));author.setPos(4000,220,4000);if(i==0)first=author;
            var corner=new BlockPos(8192+i*64,160,8192);
            for(int x=0;x<2;x++)for(int z=0;z<2;z++)level.getChunkAt(corner.offset(x*16,0,z*16));
            call(construct("TemplatePlacement",corner,Rotation.NONE,Mirror.NONE),"place",level,large,author);
        }
        var oldest=first;denied(()->callStatic("PlacementHistory","undo",oldest));
        var field=type("PlacementHistory").getDeclaredField("HISTORY");field.setAccessible(true);var history=(java.util.Deque<?>)((Map<?,?>)field.get(null)).get(level.getServer());
        long retained=0;for(var operation:history)retained+=(long)call(operation,"bytes");require(retained<=32L*1024*1024,"Shared history exceeded its budget");
        System.out.println("WA_VALIDATION_ROLLBACK_SHARED_BUDGET_PASS retained_bytes="+retained);
        System.out.println("WA_VALIDATION_STATES_METADATA_COLLISIONS_HISTORY_PASS");
    }
    @FunctionalInterface private interface Operation { void run() throws Exception; }
    private static void denied(Operation op) { try { op.run(); throw new AssertionError("Expected refusal"); } catch (IOException expected) { } catch (Exception error) { throw new IllegalStateException(error); } }
    private static Class<?> type(String name) throws Exception { return Class.forName("com.gwsheridan.worldarchitect.structure." + name); }
    private static Object construct(String name, Object... args) throws Exception {
        var ctor = java.util.Arrays.stream(type(name).getDeclaredConstructors()).filter(c -> c.getParameterCount() == args.length).findFirst().orElseThrow();
        ctor.setAccessible(true); return ctor.newInstance(args);
    }
    private static Object callStatic(String name, String method, Object... args) throws Exception { return invoke(type(name), null, method, args); }
    private static Object call(Object target, String method, Object... args) throws Exception { return invoke(target.getClass(), target, method, args); }
    private static Object invoke(Class<?> type, Object target, String method, Object[] args) throws Exception {
        var member = java.util.Arrays.stream(type.getDeclaredMethods()).filter(m -> m.getName().equals(method) && m.getParameterCount() == args.length).findFirst().orElseThrow();
        member.setAccessible(true);
        try { return member.invoke(target, args); } catch (java.lang.reflect.InvocationTargetException error) {
            if (error.getCause() instanceof Exception cause) throw cause; throw error;
        }
    }
    private static void require(boolean test, String message) { if (!test) throw new AssertionError(message); }
}
