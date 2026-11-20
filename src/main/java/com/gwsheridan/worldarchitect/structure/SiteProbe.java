package com.gwsheridan.worldarchitect.structure;

import java.util.List;
import net.minecraft.core.BlockPos;

/** Request-local instrumentation of the production planner; no world or player references. */
final class SiteProbe {
    static final ThreadLocal<SiteProbe> CURRENT=new ThreadLocal<>();
    final long deadline;
    SiteProbe(){this(System.nanoTime()+500_000_000L);}
    SiteProbe(long deadline){this.deadline=Math.min(deadline,System.nanoTime()+500_000_000L);}
    String reason="Native biome or placement checks rejected this candidate.";
    List<ModularPiece> pieces=List.of();BlockPos origin=BlockPos.ZERO;
    List<ProceduralPool.Connection> connections=List.of();BlockPos layoutSize=new BlockPos(1,1,1);TemplatePlacement transform=new TemplatePlacement(BlockPos.ZERO,net.minecraft.world.level.block.Rotation.NONE,net.minecraft.world.level.block.Mirror.NONE);
    static final class Incomplete extends RuntimeException {}
    static void check(){var p=CURRENT.get();if(p!=null&&(Thread.currentThread().isInterrupted()||System.nanoTime()>p.deadline))throw new Incomplete();}
    static boolean reject(String reason){var p=CURRENT.get();if(p!=null)p.reason=reason;return false;}
    static void connections(List<ProceduralPool.Connection> value){var p=CURRENT.get();if(p!=null)p.connections=value;check();}
    static void geometry(BlockPos origin,List<ModularPiece> pieces,BlockPos size,TemplatePlacement transform){var p=CURRENT.get();if(p!=null){p.origin=origin;p.pieces=List.copyOf(pieces);p.layoutSize=size;p.transform=transform;}check();}
}
