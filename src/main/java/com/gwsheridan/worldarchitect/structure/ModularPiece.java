package com.gwsheridan.worldarchitect.structure;

import com.gwsheridan.worldarchitect.WorldArchitect;
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
    private final String name;
    private final LibraryEntry entry;

    ModularPiece(String name, BlockPos origin, LibraryEntry entry) {
        super(WorldArchitect.MODULAR_PIECE.get(), 0, BoundingBox.fromCorners(origin, origin.offset(entry.template().size()).offset(-1, -1, -1)));
        this.name = name;
        this.entry = entry;
    }

    public ModularPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(WorldArchitect.MODULAR_PIECE.get(), tag);
        name = tag.getString("module").orElseThrow();
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

    BlockPos origin() {
        return new BlockPos(boundingBox.minX(), boundingBox.minY(), boundingBox.minZ());
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putString("module", name);
        tag.put("template", entry.toTag());
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
            BoundingBox chunkBounds, ChunkPos chunk, BlockPos reference) {
        BlockPos origin = origin();
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
