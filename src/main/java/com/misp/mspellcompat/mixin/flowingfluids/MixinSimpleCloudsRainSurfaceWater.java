package com.misp.mspellcompat.mixin.flowingfluids;

import com.misp.mspellcompat.config.MSpellCompatConfig;
import com.misp.mspellcompat.util.MSpellCompatDebug;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import traben.flowing_fluids.FFFluidUtils;

@Mixin(value = ServerLevel.class, remap = false)
public abstract class MixinSimpleCloudsRainSurfaceWater {
    @Inject(
            method = "tickChunk(Lnet/minecraft/world/level/chunk/LevelChunk;I)V",
            at = @At("TAIL"),
            require = 1,
            remap = false
    )
    private void mspell$tryCreateRainSurfaceWater(
            LevelChunk chunk,
            int randomTickSpeed,
            CallbackInfo ci
    ) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (!MSpellCompatConfig.FLOWING_FLUIDS_CREATE_SURFACE_WATER.get()) {
            MSpellCompatDebug.log("surface_rain_water", "skip", () ->
                    "reason=feature_disabled,chunk=" + chunk.getPos());
            return;
        }

        int denominator = MSpellCompatConfig.FLOWING_FLUIDS_SURFACE_WATER_CHANCE_DENOMINATOR.get();
        int roll = level.getRandom().nextInt(denominator);
        if (roll != 0) {
            MSpellCompatDebug.log("surface_rain_water", "skip", () -> "reason=chance,chunk=" + chunk.getPos()
                    + ",roll=" + roll + ",denominator=" + denominator + ",randomTickSpeed=" + randomTickSpeed);
            return;
        }

        ChunkPos chunkPos = chunk.getPos();
        int x = chunkPos.getMinBlockX() + level.getRandom().nextInt(16);
        int z = chunkPos.getMinBlockZ() + level.getRandom().nextInt(16);
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);

        if (surfaceY <= level.getMinBuildHeight() || surfaceY >= level.getMaxBuildHeight()) {
            MSpellCompatDebug.log("surface_rain_water", "skip", () -> "reason=height_limit,chunk=" + chunkPos
                    + ",sample=" + x + "," + surfaceY + "," + z);
            return;
        }

        BlockPos waterPos = new BlockPos(x, surfaceY, z);
        BlockPos supportPos = waterPos.below();
        BlockState waterTargetState = chunk.getBlockState(waterPos);
        if (!waterTargetState.isAir() || !chunk.getFluidState(waterPos).isEmpty()) {
            MSpellCompatDebug.log("surface_rain_water", "skip", () -> "reason=target_not_empty,chunk=" + chunkPos
                    + ",target=" + MSpellCompatDebug.describeFluid(level, waterPos));
            return;
        }

        BlockState supportState = chunk.getBlockState(supportPos);
        if (!supportState.isFaceSturdy(level, supportPos, Direction.UP)) {
            MSpellCompatDebug.log("surface_rain_water", "skip", () -> "reason=unsupported_surface,chunk=" + chunkPos
                    + ",supportPos=" + supportPos + ",support=" + supportState);
            return;
        }
        if (!level.canSeeSky(waterPos)) {
            MSpellCompatDebug.log("surface_rain_water", "skip", () -> "reason=sky_blocked,pos=" + waterPos);
            return;
        }
        if (!mspell$isRainingAt(level, waterPos)) {
            MSpellCompatDebug.log("surface_rain_water", "skip", () -> "reason=no_local_rain,pos=" + waterPos);
            return;
        }

        int amount = MSpellCompatConfig.FLOWING_FLUIDS_SURFACE_WATER_UNITS.get();
        MSpellCompatDebug.log("surface_rain_water", "create_begin", () -> "pos=" + waterPos + ",amount=" + amount
                + ",support=" + supportPos + ",supportState=" + supportState);
        FFFluidUtils.setFluidStateAtPosToNewAmount(level, waterPos, Fluids.WATER, amount);
        MSpellCompatDebug.log("surface_rain_water", "create_end", () ->
                "pos=" + waterPos + ",requestedAmount=" + amount + ",after="
                        + MSpellCompatDebug.describeFluid(level, waterPos));
    }

    @Unique
    private static boolean mspell$isRainingAt(ServerLevel level, BlockPos pos) {
        CloudManager<ServerLevel> clouds = CloudManager.get(level);
        if (clouds.shouldUseVanillaWeather()) {
            boolean result = level.isRainingAt(pos);
            MSpellCompatDebug.log("surface_rain_water", "rain_check", () ->
                    "mode=vanilla,pos=" + pos + ",result=" + result);
            return result;
        }

        var precipitation = clouds.getPrecipitationAt(pos);
        boolean result = Boolean.TRUE.equals(precipitation.getLeft())
                && precipitation.getRight() == Biome.Precipitation.RAIN;
        MSpellCompatDebug.log("surface_rain_water", "rain_check", () ->
                "mode=simpleclouds,pos=" + pos + ",precipitation=" + precipitation + ",result=" + result);
        return result;
    }
}
