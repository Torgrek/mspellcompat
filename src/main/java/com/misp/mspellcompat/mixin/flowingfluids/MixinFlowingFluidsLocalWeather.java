package com.misp.mspellcompat.mixin.flowingfluids;

import com.misp.mspellcompat.config.MSpellCompatConfig;
import com.misp.mspellcompat.util.MSpellCompatDebug;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.WaterFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.Dynamic;
import traben.flowing_fluids.mixin.mixins.MixinWaterFluid;

@Mixin(value = WaterFluid.class, priority = 1100, remap = false)
public abstract class MixinFlowingFluidsLocalWeather {
    @Dynamic(mixin = MixinWaterFluid.class)
    @Redirect(
            method = "ff$tryRainFill(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;FZZ)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;isRaining()Z"),
            require = 1,
            remap = false
    )
    private boolean mspell$useLocalizedPrecipitationForRainFill(
            Level invokedLevel,
            Level level,
            BlockPos blockPos,
            float randomValue,
            boolean inInfiniteBiome,
            boolean withinInfiniteBiomeHeight
    ) {
        boolean result = mspell$shouldTreatAsRaining(invokedLevel, blockPos);
        MSpellCompatDebug.log("flowing_fluids_weather", "rain_fill_redirect", () ->
                MSpellCompatDebug.describeLevel(invokedLevel) + ",pos=" + blockPos
                        + ",randomValue=" + randomValue + ",inInfiniteBiome=" + inInfiniteBiome
                        + ",withinInfiniteBiomeHeight=" + withinInfiniteBiomeHeight + ",result=" + result);
        return result;
    }

    @Dynamic(mixin = MixinWaterFluid.class)
    @Redirect(
            method = "ff$tryBiomeFillOrDrain(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;IFZZZ)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;isRaining()Z"),
            require = 1,
            remap = false
    )
    private boolean mspell$useLocalizedPrecipitationForBiomeRefill(
            Level invokedLevel,
            Level level,
            BlockPos blockPos,
            int amount,
            float randomValue,
            boolean inInfiniteBiome,
            boolean atSeaLevel,
            boolean hasSkyLight
    ) {
        boolean result = mspell$shouldTreatAsRaining(invokedLevel, blockPos);
        MSpellCompatDebug.log("flowing_fluids_weather", "biome_refill_redirect", () ->
                MSpellCompatDebug.describeLevel(invokedLevel) + ",pos=" + blockPos
                        + ",amount=" + amount + ",randomValue=" + randomValue
                        + ",inInfiniteBiome=" + inInfiniteBiome + ",atSeaLevel=" + atSeaLevel
                        + ",hasSkyLight=" + hasSkyLight + ",result=" + result);
        return result;
    }

    @Unique
    private static boolean mspell$shouldTreatAsRaining(Level level, BlockPos fluidPos) {
        if (level.isClientSide) {
            boolean result = level.isRaining();
            MSpellCompatDebug.log("flowing_fluids_weather", "rain_decision", () ->
                    "mode=vanilla_client,enabled=evaluated=false,pos=" + fluidPos + ",result=" + result
                            + "," + MSpellCompatDebug.describeLevel(level));
            return result;
        }

        boolean enabled = MSpellCompatConfig.FLOWING_FLUIDS_USE_LOCAL_WEATHER.get();
        if (!enabled) {
            boolean result = level.isRaining();
            MSpellCompatDebug.log("flowing_fluids_weather", "rain_decision", () ->
                    "mode=vanilla,enabled=false,pos=" + fluidPos + ",result=" + result
                            + "," + MSpellCompatDebug.describeLevel(level));
            return result;
        }

        if (level instanceof ServerLevel serverLevel) {
            CloudManager<ServerLevel> clouds = CloudManager.get(serverLevel);
            if (!clouds.shouldUseVanillaWeather()) {
                var precipitation = clouds.getPrecipitationAt(fluidPos.above());
                boolean result = Boolean.TRUE.equals(precipitation.getLeft())
                        && precipitation.getRight() == Biome.Precipitation.RAIN;
                MSpellCompatDebug.log("flowing_fluids_weather", "rain_decision",
                        "mode=simpleclouds_local,pos=" + fluidPos + ",precipitation=" + precipitation
                                + ",result=" + result + "," + MSpellCompatDebug.describeLevel(level));
                return result;
            }
        }
        boolean result = level.isRaining();
        MSpellCompatDebug.log("flowing_fluids_weather", "rain_decision",
                "mode=vanilla_fallback,pos=" + fluidPos + ",result=" + result
                        + "," + MSpellCompatDebug.describeLevel(level));
        return result;
    }
}
