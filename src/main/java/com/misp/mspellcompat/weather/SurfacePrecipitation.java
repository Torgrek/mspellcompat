package com.misp.mspellcompat.weather;

import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biome;

/** Shared local-precipitation checks and snow-layer placement for terrain and Sable sublevels. */
public final class SurfacePrecipitation {
    private SurfacePrecipitation() {
    }

    public static Biome.Precipitation getAt(ServerLevel level, BlockPos pos) {
        CloudManager<ServerLevel> clouds = CloudManager.get(level);
        if (clouds.shouldUseVanillaWeather()) {
            if (!level.isRaining() || !level.canSeeSky(pos)) {
                return Biome.Precipitation.NONE;
            }
            return level.getBiome(pos).value().getPrecipitationAt(pos);
        }

        var precipitation = clouds.getPrecipitationAt(pos);
        if (!Boolean.TRUE.equals(precipitation.getLeft()) || precipitation.getRight() == null) {
            return Biome.Precipitation.NONE;
        }
        return precipitation.getRight();
    }

    public static boolean addSnowLayer(LevelAccessor level, BlockPos targetPos, BlockPos supportPos) {
        BlockState targetState = level.getBlockState(targetPos);
        if (targetState.hasProperty(SnowLayerBlock.LAYERS)) {
            int layers = targetState.getValue(SnowLayerBlock.LAYERS);
            if (layers < 8) {
                return level.setBlock(targetPos, targetState.setValue(SnowLayerBlock.LAYERS, layers + 1), Block.UPDATE_ALL);
            }
            BlockPos abovePos = targetPos.above();
            BlockState aboveState = level.getBlockState(abovePos);
            if (aboveState.isAir() && level.getFluidState(abovePos).isEmpty()
                    && targetState.isFaceSturdy(level, targetPos, Direction.UP)) {
                BlockState firstLayer = Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1);
                if (firstLayer.canSurvive(level, abovePos)) {
                    return level.setBlock(abovePos, firstLayer, Block.UPDATE_ALL);
                }
            }
            return false;
        }

        if (!targetState.isAir() || !level.getFluidState(targetPos).isEmpty()) {
            return false;
        }

        BlockState supportState = level.getBlockState(supportPos);
        if (supportState.hasProperty(SnowLayerBlock.LAYERS)) {
            int layers = supportState.getValue(SnowLayerBlock.LAYERS);
            if (layers < 8) {
                return level.setBlock(supportPos, supportState.setValue(SnowLayerBlock.LAYERS, layers + 1), Block.UPDATE_ALL);
            }
        }

        if (!supportState.isFaceSturdy(level, supportPos, Direction.UP)) {
            return false;
        }

        BlockState firstLayer = Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1);
        if (!firstLayer.canSurvive(level, targetPos)) {
            return false;
        }
        return level.setBlock(targetPos, firstLayer, Block.UPDATE_ALL);
    }
}
