package com.gwsheridan.worldarchitect.structure;
import net.minecraft.world.level.block.Blocks;import java.io.IOException;import java.util.List;import net.minecraft.core.BlockPos;import net.minecraft.core.HolderLookup;import net.minecraft.nbt.CompoundTag;import net.minecraft.world.level.block.state.BlockState;import org.jspecify.annotations.Nullable;
import com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyValues;
public record NativeTemplate(BlockPos size,List<Cell> blocks) {
 public static final int MAX_SIDE=32;
 public NativeTemplate { size=size.immutable();blocks=List.copyOf(blocks); }
 private com.gwsheridan.worldsunwritten.structure.NativeTemplate current(){return LegacyValues.current(this);}
 public NativeTemplate sanitized(HolderLookup.Provider registries)throws IOException{return LegacyValues.legacy(current().sanitized(registries));}
 public static NativeTemplate read(CompoundTag root)throws IOException{return LegacyValues.legacy(com.gwsheridan.worldsunwritten.structure.NativeTemplate.read(root));}
 public static NativeTemplate readIsland(CompoundTag root)throws IOException{return LegacyValues.legacy(com.gwsheridan.worldsunwritten.structure.NativeTemplate.readIsland(root));}
 public CompoundTag toTag(){return current().toTag();}
 public static void checkSize(BlockPos size)throws IOException{com.gwsheridan.worldsunwritten.structure.NativeTemplate.checkSize(size);}
 public static boolean inside(BlockPos size,BlockPos pos){return com.gwsheridan.worldsunwritten.structure.NativeTemplate.inside(size,pos);}
 public record Cell(BlockPos pos, BlockState state, @Nullable CompoundTag metadata) {
        public Cell {
            pos = pos.immutable();
            metadata = metadata == null ? null : metadata.copy();
        }

        @Override
        public @Nullable CompoundTag metadata() {
            return metadata == null ? null : metadata.copy();
        }

        public boolean isSolidContent() {
            return !state.isAir() && !state.is(Blocks.STRUCTURE_VOID);
        }
    }
}
