package com.misp.mspellcompat.mixin;

import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class CompatMixinPlugin implements IMixinConfigPlugin {
    private static final String LOCAL_WEATHER_MIXIN =
            "com.misp.mspellcompat.mixin.flowingfluids.MixinFlowingFluidsLocalWeather";
    private static final String RAIN_SURFACE_WATER_MIXIN =
            "com.misp.mspellcompat.mixin.flowingfluids.MixinSimpleCloudsRainSurfaceWater";
    private static final String LSO_FINITE_WATER_MIXIN =
            "com.misp.mspellcompat.mixin.lso.MixinLsoFiniteWaterDrinking";
    private static final String LSO_BLOCK_FINITE_WATER_MIXIN =
            "com.misp.mspellcompat.mixin.lso.MixinLsoFiniteWaterBlockDrinking";
    private static final String LSO_CANTEEN_MIXIN =
            "com.misp.mspellcompat.mixin.lso.MixinLsoCanteenFiniteWater";
    private static final String BURNT_FLUID_STATE_MIXIN =
            "com.misp.mspellcompat.mixin.burnt.MixinBurntAdjacentFluidWater";
    private static final String BURNT_SIMPLE_CLOUDS_RAIN_MIXIN =
            "com.misp.mspellcompat.mixin.burnt.MixinBurntSimpleCloudsRain";
    private static final String SABLE_FLUID_TICK_MIXIN =
            "com.misp.mspellcompat.mixin.sable.MixinSablePlotFluidTick";
    private static final String MB_FF_TANK_SLOT_EVENTS_MIXIN =
            "com.misp.mspellcompat.mixin.modularbackpacks.MixinModularBackpacksTankSlotEvents";
    private static final String MB_FF_NATIVE_TICK_GUARD_MIXIN =
            "com.misp.mspellcompat.mixin.modularbackpacks.MixinModularBackpacksNativeTickGuard";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (MB_FF_TANK_SLOT_EVENTS_MIXIN.equals(mixinClassName)
                || MB_FF_NATIVE_TICK_GUARD_MIXIN.equals(mixinClassName)) {
            return isModLoaded("modular_backpacks") && isModLoaded("flowing_fluids");
        }
        if (LOCAL_WEATHER_MIXIN.equals(mixinClassName)
                || RAIN_SURFACE_WATER_MIXIN.equals(mixinClassName)) {
            return isModLoaded("flowing_fluids") && isModLoaded("simpleclouds");
        }
        if (LSO_FINITE_WATER_MIXIN.equals(mixinClassName)
                || LSO_BLOCK_FINITE_WATER_MIXIN.equals(mixinClassName)
                || LSO_CANTEEN_MIXIN.equals(mixinClassName)) {
            return isModLoaded("legendarysurvivaloverhaul")
                    && isModLoaded("flowing_fluids");
        }
        if (BURNT_FLUID_STATE_MIXIN.equals(mixinClassName)) {
            return isModLoaded("burnt") && isModLoaded("flowing_fluids");
        }
        if (BURNT_SIMPLE_CLOUDS_RAIN_MIXIN.equals(mixinClassName)) {
            return isModLoaded("burnt") && isModLoaded("simpleclouds");
        }
        if (SABLE_FLUID_TICK_MIXIN.equals(mixinClassName)) {
            return isModLoaded("sable") && isModLoaded("flowing_fluids");
        }
        return true;
    }

    private static boolean isModLoaded(String modId) {
        return LoadingModList.get().getModFileById(modId) != null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName,
                         IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName,
                          IMixinInfo mixinInfo) {
    }
}
