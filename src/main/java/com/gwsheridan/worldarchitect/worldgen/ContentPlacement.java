package com.gwsheridan.worldarchitect.worldgen;

import com.gwsheridan.worldarchitect.config.ContentProfile.Decoration;
import com.gwsheridan.worldarchitect.config.ContentProfile.Ore;
import java.util.List;
import java.util.SplittableRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

final class ContentPlacement {
    private static final Direction[] STEPS = Direction.values();

    private ContentPlacement() {}

    static void placeOres(ChunkAccess chunk, long seed, List<Ore> rules, int[] surfaces, BaseMaterial layers) {
        placeOres(chunk, seed, rules, surfaces, layers, 0, ignored -> true);
    }

    static void placeOres(ChunkAccess chunk, long seed, List<Ore> rules, int[] surfaces, BaseMaterial layers, long salt, java.util.function.Predicate<BlockPos> allowed) {
        if (rules.isEmpty()) {
            return;
        }
        var chunkPos = chunk.getPos();
        int startX = chunkPos.getMinBlockX();
        int startZ = chunkPos.getMinBlockZ();
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (int index = 0; index < rules.size(); index++) {
            Ore rule = rules.get(index);
            SplittableRandom random = new SplittableRandom(ContentRandom.seed(seed, chunkPos.x(), chunkPos.z(), 100 + index + salt));
            int min = Math.max(rule.minY(), chunk.getMinY());
            int max = Math.min(rule.maxY(), chunk.getMaxY());
            if (max < min) {
                continue;
            }
            for (int attempt = 0; attempt < rule.attempts(); attempt++) {
                int x = random.nextInt(16);
                int z = random.nextInt(16);
                int y = random.nextInt(min, max + 1);
                for (int block = 0; block < rule.size(); block++) {
                    int column = z * 16 + x;
                    int depth = surfaces[column] - y;
                    if (depth >= 1 && y >= min && y <= max) {
                        BlockState base = layers.at(column, y);
                        position.set(startX + x, y, startZ + z);
                        if (rule.target().orElse(base) == base && chunk.getBlockState(position) == base
                                && matchesBiome(rule.biomes(), chunk, position) && allowed.test(position)) {
                            chunk.setBlockState(position, rule.material(), Block.UPDATE_ALL);
                        }
                    }
                    Direction step = STEPS[random.nextInt(STEPS.length)];
                    x = Math.clamp(x + step.getStepX(), 0, 15);
                    z = Math.clamp(z + step.getStepZ(), 0, 15);
                    y += step.getStepY();
                }
            }
        }
    }

    @FunctionalInterface
    interface BaseMaterial {
        BlockState at(int column, int y);
    }

    static void decorate(WorldGenLevel level, ChunkAccess chunk, SolidColumnGenerator generator, List<Decoration> rules) {
        decorate(level, chunk, generator, rules, 0, ignored -> true);
    }

    static void decorate(WorldGenLevel level, ChunkAccess chunk, SolidColumnGenerator generator, List<Decoration> rules, long salt, java.util.function.Predicate<BlockPos> allowed) {
        var chunkPos = chunk.getPos();
        long seed = level.getSeed();
        for (int index = 0; index < rules.size(); index++) {
            Decoration rule = rules.get(index);
            int min = rule.minY().orElse(chunk.getMinY());
            int max = rule.maxY().orElse(chunk.getMaxY());
            SplittableRandom random = new SplittableRandom(ContentRandom.seed(seed, chunkPos.x(), chunkPos.z(), 200 + index + salt));
            for (int attempt = 0; attempt < rule.attempts(); attempt++) {
                int x = chunkPos.getMinBlockX() + random.nextInt(16);
                int z = chunkPos.getMinBlockZ() + random.nextInt(16);
                if (random.nextInt(100) >= rule.chance()) {
                    continue;
                }
                BlockPos position = new BlockPos(x, generator.surfaceY(x, z, chunk, seed) + 1, z);
                BlockPos below = position.below();
                if (position.getY() >= min && position.getY() <= max && matchesBiome(rule.biomes(), chunk, position)
                        && level.isEmptyBlock(position) && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                        && rule.material().canSurvive(level, position) && allowed.test(position)) {
                    level.setBlock(position, rule.material(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private static boolean matchesBiome(List<Holder<Biome>> allowed, ChunkAccess chunk, BlockPos position) {
        return allowed.isEmpty() || allowed.contains(chunk.getNoiseBiome(QuartPos.fromBlock(position.getX()),
                QuartPos.fromBlock(position.getY()), QuartPos.fromBlock(position.getZ())));
    }
}
