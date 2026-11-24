package com.misp.mspellcompat.mixin.flowingfluids;

import com.misp.mspellcompat.config.MSpellCompatConfig;
import com.misp.mspellcompat.util.MSpellCompatDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import com.misp.mspellcompat.weather.SurfacePrecipitation;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import traben.flowing_fluids.FFFluidUtils;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

@Mixin(value = ServerLevel.class, remap = false)
public abstract class MixinSimpleCloudsRainSurfaceWater {
    @Unique
    private static volatile Method mspell$getSableContainerMethod;
    @Unique
    private static volatile Method mspell$isSablePlotyardMethod;
    @Unique
    private static final Map<ServerLevel, Map<Long, Boolean>> mspell$sableStorageChunkCache = new WeakHashMap<>();

    @Inject(
            method = "tickChunk(Lnet/minecraft/world/level/chunk/LevelChunk;I)V",
            at = @At("TAIL"),
            require = 1,
            remap = false
    )
    private void mspell$tryCreateSurfacePrecipitationLayer(
            LevelChunk chunk,
            int randomTickSpeed,
            CallbackInfo ci
    ) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (!MSpellCompatConfig.FLOWING_FLUIDS_CREATE_SURFACE_WATER.get()) {
            if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                MSpellCompatDebug.log("surface_precipitation_layer", "skip", () ->
                        "reason=feature_disabled,chunk=" + chunk.getPos());
            }
            return;
        }

        ChunkPos chunkPos = chunk.getPos();
        if (mspell$isSableStorageChunk(level, chunkPos)) {
            return;
        }

        int denominator = MSpellCompatConfig.FLOWING_FLUIDS_SURFACE_WATER_CHANCE_DENOMINATOR.get();
        int roll = level.getRandom().nextInt(denominator);
        if (roll != 0) return;

        int x = chunkPos.getMinBlockX() + level.getRandom().nextInt(16);
        int z = chunkPos.getMinBlockZ() + level.getRandom().nextInt(16);
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (surfaceY <= level.getMinBuildHeight() || surfaceY >= level.getMaxBuildHeight()) return;

        BlockPos targetPos = new BlockPos(x, surfaceY, z);
        BlockPos supportPos = targetPos.below();
        if (!level.canSeeSky(targetPos)) return;

        Biome.Precipitation precipitation = SurfacePrecipitation.getAt(level, targetPos);
        if (precipitation == Biome.Precipitation.RAIN) {
            BlockState targetState = chunk.getBlockState(targetPos);
            if (!targetState.isAir() || !chunk.getFluidState(targetPos).isEmpty()) return;

            BlockState supportState = chunk.getBlockState(supportPos);
            if (!supportState.isFaceSturdy(level, supportPos, Direction.UP)) return;

            int amount = MSpellCompatConfig.FLOWING_FLUIDS_SURFACE_WATER_UNITS.get();
            FFFluidUtils.setFluidStateAtPosToNewAmount(level, targetPos, Fluids.WATER, amount);
            if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                MSpellCompatDebug.log("surface_precipitation_layer", "water_created", () ->
                        "pos=" + targetPos + ",amount=" + amount + ",chunk=" + chunkPos);
            }
            return;
        }

        if (precipitation == Biome.Precipitation.SNOW) {
            if (SurfacePrecipitation.addSnowLayer(level, targetPos, supportPos)
                    && MSpellCompatDebug.isDebugLoggingEnabled()) {
                MSpellCompatDebug.log("surface_precipitation_layer", "snow_layer_added", () ->
                        "pos=" + targetPos + ",chunk=" + chunkPos);
            }
        }
    }

    @Unique
    private static boolean mspell$isSableStorageChunk(ServerLevel level, ChunkPos chunkPos) {
        long minBlockX = (long) chunkPos.x * 16L;
        long minBlockZ = (long) chunkPos.z * 16L;
        if (Math.abs(minBlockX) < 10_000_000L || Math.abs(minBlockZ) < 10_000_000L) {
            return false;
        }
        if (!ModList.get().isLoaded("sable")) return false;

        long chunkKey = ((long) chunkPos.x << 32) ^ (chunkPos.z & 0xFFFFFFFFL);
        synchronized (mspell$sableStorageChunkCache) {
            Map<Long, Boolean> levelCache = mspell$sableStorageChunkCache.get(level);
            if (levelCache != null) {
                Boolean cached = levelCache.get(chunkKey);
                if (cached != null) return cached;
            }
        }

        boolean inPlotyard = false;
        try {
            Method getContainer = mspell$getSableContainerMethod;
            if (getContainer == null) {
                Class<?> containerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
                Method discovered = containerClass.getMethod("getContainer", ServerLevel.class);
                mspell$getSableContainerMethod = discovered;
                getContainer = discovered;
            }
            Object container = getContainer.invoke(null, level);
            if (container != null) {
                Method inBounds = mspell$isSablePlotyardMethod;
                if (inBounds == null || !inBounds.getDeclaringClass().isAssignableFrom(container.getClass())) {
                    Method discovered = container.getClass().getMethod("inBounds", ChunkPos.class);
                    mspell$isSablePlotyardMethod = discovered;
                    inBounds = discovered;
                }
                inPlotyard = Boolean.TRUE.equals(inBounds.invoke(container, chunkPos));
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            inPlotyard = false;
        }

        synchronized (mspell$sableStorageChunkCache) {
            Map<Long, Boolean> levelCache = mspell$sableStorageChunkCache.computeIfAbsent(
                    level, ignored -> new HashMap<>());
            if (levelCache.size() >= 32768) levelCache.clear();
            levelCache.put(chunkKey, inPlotyard);
        }
        return inPlotyard;
    }

}
