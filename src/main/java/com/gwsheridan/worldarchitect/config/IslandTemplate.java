package com.gwsheridan.worldarchitect.config;

import com.gwsheridan.worldarchitect.structure.NativeTemplate;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Sparse, self-contained snapshot of a starter structure. Coordinates are local to its origin. */
public record IslandTemplate(BlockPos size, BlockPos spawn, List<Cell> blocks) {
    public static final int MAX_SIDE = NativeTemplate.MAX_SIDE;
    private static final int MAX_BLOCKS = MAX_SIDE * MAX_SIDE * MAX_SIDE;
    public static final Codec<IslandTemplate> CODEC = RecordCodecBuilder.<IslandTemplate>create(builder -> builder.group(
            BlockPos.CODEC.fieldOf("size").forGetter(IslandTemplate::size),
            BlockPos.CODEC.fieldOf("spawn").forGetter(IslandTemplate::spawn),
            Cell.CODEC.listOf(1, MAX_BLOCKS).fieldOf("blocks").forGetter(IslandTemplate::blocks)
    ).apply(builder, IslandTemplate::new)).validate(IslandTemplate::validate);

    public IslandTemplate {
        size = size.immutable();
        spawn = spawn.immutable();
        blocks = List.copyOf(blocks);
    }

    public IslandTemplate withSpawn(BlockPos marker) {
        return new IslandTemplate(size, marker, blocks).validate().getOrThrow();
    }

    public DataResult<IslandTemplate> validate() {
        if (size.getX() < 1 || size.getY() < 3 || size.getZ() < 1 || size.getX() > MAX_SIDE
                || size.getY() > MAX_SIDE || size.getZ() > MAX_SIDE || blocks.isEmpty() || blocks.size() > MAX_BLOCKS) {
            return DataResult.error(() -> "Island size must fit within 32 × 32 × 32 with at least three blocks of height.");
        }
        Map<BlockPos, BlockState> states = new HashMap<>();
        for (Cell cell : blocks) {
            if (!inside(cell.pos()) || !canPlace(cell.state()) || states.putIfAbsent(cell.pos(), cell.state()) != null) {
                return DataResult.error(() -> "Invalid or duplicate island block at " + cell.pos().toShortString());
            }
        }
        BlockState floor = states.get(spawn.below());
        if (floor == null || !inside(spawn.above()) || states.containsKey(spawn) || states.containsKey(spawn.above())
                || !safeFloor(floor, EmptyBlockGetter.INSTANCE, spawn.below())) {
            return DataResult.error(() -> "The spawn marker must have two clear air blocks above a safe full block inside the template.");
        }
        return DataResult.success(this);
    }

    private boolean inside(BlockPos pos) {
        return NativeTemplate.inside(size, pos);
    }

    public static boolean safeFloor(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getFluidState().isEmpty() && !(state.getBlock() instanceof FallingBlock)
                && !state.is(Blocks.MAGMA_BLOCK) && !state.is(Blocks.POWDER_SNOW)
                && !state.is(Blocks.CACTUS) && !state.is(Blocks.CACTUS_FLOWER)
                && !state.is(Blocks.CAMPFIRE) && !state.is(Blocks.SOUL_CAMPFIRE)
                && state.isCollisionShapeFullBlock(level, pos);
    }

    private static boolean canPlace(BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty() && !state.hasBlockEntity()
                && !(state.getBlock() instanceof FallingBlock) && !state.is(Blocks.STRUCTURE_VOID)
                && !state.is(Blocks.NETHER_PORTAL) && !state.is(Blocks.END_PORTAL);
    }

    public static IslandTemplate classic() {
        List<Cell> cells = new ArrayList<>();
        for (int z = 0; z < 9; z++) {
            for (int x = 0; x < 9; x++) {
                int distance = Math.abs(x - 4) + Math.abs(z - 4);
                if (distance <= 5) {
                    cells.add(new Cell(new BlockPos(x, 3, z), Blocks.GRASS_BLOCK.defaultBlockState()));
                    cells.add(new Cell(new BlockPos(x, 2, z), Blocks.DIRT.defaultBlockState()));
                }
                if (distance <= 3) {
                    cells.add(new Cell(new BlockPos(x, 1, z), Blocks.STONE.defaultBlockState()));
                }
                if (distance <= 1) {
                    cells.add(new Cell(new BlockPos(x, 0, z), Blocks.STONE.defaultBlockState()));
                }
            }
        }
        cells.add(new Cell(new BlockPos(6, 4, 4), Blocks.OAK_SAPLING.defaultBlockState()));
        return new IslandTemplate(new BlockPos(9, 6, 9), new BlockPos(4, 4, 4), cells);
    }

    public static IslandTemplate importStructure(Path file, BlockPos spawn) throws IOException {
        return importStructure(NbtIo.readCompressed(file, NbtAccounter.create(8L * 1024 * 1024)), spawn);
    }

    public static IslandTemplate importStructure(CompoundTag root, BlockPos spawn) throws IOException {
        if (root.contains("palettes") || !root.getListOrEmpty("entities").isEmpty()) {
            throw new IOException("Use a structure with one palette and no entities.");
        }
        NativeTemplate structure = NativeTemplate.read(root);
        List<Cell> blocks = new ArrayList<>();
        for (var cell : structure.blocks()) {
            if (cell.metadata() != null) {
                throw new IOException("Starting islands support block states without block-entity data.");
            }
            if (cell.isSolidContent()) {
                blocks.add(new Cell(cell.pos(), cell.state()));
            }
        }
        return new IslandTemplate(structure.size(), spawn, blocks).validate().getOrThrow(IOException::new);
    }

    private static DataResult<BlockState> parseState(String value) {
        try {
            return DataResult.success(BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, value, false).blockState());
        } catch (CommandSyntaxException e) {
            return DataResult.error(() -> e.getRawMessage().getString());
        }
    }

    public record Cell(BlockPos pos, BlockState state) {
        private static final Codec<BlockState> STATE = Codec.STRING.comapFlatMap(IslandTemplate::parseState, BlockStateParser::serialize);
        private static final Codec<Cell> CODEC = RecordCodecBuilder.create(builder -> builder.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Cell::pos),
                STATE.fieldOf("state").forGetter(Cell::state)
        ).apply(builder, Cell::new));

        public Cell {
            pos = pos.immutable();
        }
    }
}
