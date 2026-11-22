package com.gwsheridan.worldsunwritten.structure;

import com.gwsheridan.worldsunwritten.WorldsUnwritten;
import java.io.IOException;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.TagValueInput;

/** Each piece carries its template and markers for later chunk generation and operator travel. */
public final class ModularPiece extends StructurePiece {
    final boolean legacyEncoding;
    private final String name;
    private final LibraryEntry entry;
    private final String operation;
    private final boolean preparationWater;

    ModularPiece(String name, BlockPos origin, LibraryEntry entry) {
        this(name,origin,entry,"",false);
    }
    ModularPiece(String name,BlockPos origin,LibraryEntry entry,String operation,boolean preparationWater){
        this(name,origin,entry,operation,preparationWater,com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyCodecs.constructing());
    }
    ModularPiece(String name,BlockPos origin,LibraryEntry entry,String operation,boolean preparationWater,boolean legacyEncoding){
        super(legacyEncoding ? com.gwsheridan.worldsunwritten.compatibility.legacy.LegacySupport.MODULE.get() : WorldsUnwritten.MODULAR_PIECE.get(), 0, BoundingBox.fromCorners(origin, origin.offset(entry.template().size()).offset(-1, -1, -1)));
        this.legacyEncoding = legacyEncoding;
        this.name = name;
        this.entry = entry;
        this.operation=operation;this.preparationWater=preparationWater;
    }

    public ModularPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyCodecs.constructing() ? com.gwsheridan.worldsunwritten.compatibility.legacy.LegacySupport.MODULE.get() : WorldsUnwritten.MODULAR_PIECE.get(), tag);
        legacyEncoding = com.gwsheridan.worldsunwritten.compatibility.legacy.LegacyCodecs.constructing();
        name = tag.getString("module").orElseThrow();
        operation=tag.getStringOr("preparation","");preparationWater=tag.getBooleanOr("preparation_water",false);
        if(!java.util.List.of("","prepared","support","clear").contains(operation))throw new IllegalStateException("Unknown piece preparation operation.");
        entry = LibraryEntry.CODEC.parse(NbtOps.INSTANCE, tag.getCompoundOrEmpty("template")).getOrThrow();
        BlockPos size = entry.template().size();
        if (size.getX() != boundingBox.getXSpan() || size.getY() != boundingBox.getYSpan() || size.getZ() != boundingBox.getZSpan()) {
            throw new IllegalStateException("A saved modular piece has inconsistent bounds.");
        }
    }

    String name() {
        return name;
    }

    LibraryEntry entry() {
        return entry;
    }
    String operation(){return operation;}

    BlockPos origin() {
        return new BlockPos(boundingBox.minX(), boundingBox.minY(), boundingBox.minZ());
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putString("module", name);
        tag.put("template", entry.toTag());
        if(!operation.isEmpty()){tag.putString("preparation",operation);if(preparationWater)tag.putBoolean("preparation_water",true);}
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
            BoundingBox chunkBounds, ChunkPos chunk, BlockPos reference) {
        BlockPos origin = origin();
        // Later features may differ from base-terrain planning. Preserve protected live blocks rather than overwriting them.
        if(!operation.isEmpty())for(var cell:entry.template().blocks()){
            BlockPos pos=origin.offset(cell.pos());if(!chunkBounds.isInside(pos))continue;var existing=level.getBlockState(pos);
            if(existing.hasBlockEntity()||TemplateMetadata.restricted(existing)||existing.is(net.minecraft.world.level.block.Blocks.LAVA)||!existing.getFluidState().isEmpty()&&!existing.is(net.minecraft.world.level.block.Blocks.WATER))return;
            if(operation.equals("support")&&!StructureFoundation.replaceable(existing,preparationWater)||operation.equals("clear")&&!StructureFoundation.clearable(existing,preparationWater)||operation.equals("prepared")&&!entry.rules().collision().allows(existing,cell.state()))return;
        }
        for (var cell : entry.template().blocks()) {
            BlockPos pos = origin.offset(cell.pos());
            if (!chunkBounds.isInside(pos)) {
                continue;
            }
            level.setBlock(pos, cell.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            try {
                CompoundTag metadata = TemplateMetadata.sanitize(cell.state(), cell.metadata(), level.registryAccess());
                if (metadata != null) {
                    var entity = level.getBlockEntity(pos);
                    if (entity == null) {
                        throw new IllegalStateException("Missing generated block entity at " + pos);
                    }
                    entity.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), metadata));
                    entity.setChanged();
                }
            } catch (IOException e) {
                throw new IllegalStateException("Invalid generation template metadata in module " + name, e);
            }
        }
    }
}
