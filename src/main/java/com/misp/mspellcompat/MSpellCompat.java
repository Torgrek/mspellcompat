package com.misp.mspellcompat;

import com.misp.mspellcompat.config.MSpellCompatConfig;
import com.misp.mspellcompat.config.SableFluidPhysicsConfig;
import com.misp.mspellcompat.lso.LsoHeatSnowMelting;
import com.misp.mspellcompat.sable.SableFluidImpactDisplacement;
import com.misp.mspellcompat.sable.SableFluidRuntimeConfig;
import com.misp.mspellcompat.sable.SableSimpleCloudsRainIntegration;
import com.misp.mspellcompat.sable.SableFluidTransport;
import com.misp.mspellcompat.sable.TreePhysicsDamageBridge;
import com.misp.mspellcompat.util.MSpellCompatDebug;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(MSpellCompat.MOD_ID)
public final class MSpellCompat {
    public static final String MOD_ID = "mspellcompat";

    public MSpellCompat(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(
                ModConfig.Type.COMMON,
                MSpellCompatConfig.SPEC,
                "mspellcompat-server.toml"
        );
        modContainer.registerConfig(
                ModConfig.Type.COMMON,
                SableFluidPhysicsConfig.SPEC,
                "mspellcompat-sable-fluid.toml"
        );
        modContainer.registerConfig(
                ModConfig.Type.SERVER,
                SableFluidRuntimeConfig.SPEC,
                "mspellcompat-sable-fluids-server.toml"
        );
        modEventBus.addListener(MSpellCompat::onConfigLoading);
        modEventBus.addListener(MSpellCompat::onConfigReloading);
        if (ModList.get().isLoaded("sable") && ModList.get().isLoaded("flowing_fluids")) {
            NeoForge.EVENT_BUS.addListener(SableFluidImpactDisplacement::onServerTickPre);
            NeoForge.EVENT_BUS.addListener(SableFluidTransport::onServerTickPre);
            NeoForge.EVENT_BUS.addListener(SableFluidTransport::onServerTick);
        }
        if (ModList.get().isLoaded("sable")
                && ModList.get().isLoaded("flowing_fluids")
                && ModList.get().isLoaded("simpleclouds")) {
            NeoForge.EVENT_BUS.addListener(SableSimpleCloudsRainIntegration::onServerTick);
        }
        if (ModList.get().isLoaded("treephysics")) {
            NeoForge.EVENT_BUS.addListener(TreePhysicsDamageBridge::onExplosionDetonate);
            NeoForge.EVENT_BUS.addListener(TreePhysicsDamageBridge::onPlayerBreak);
        }
        if (ModList.get().isLoaded("legendarysurvivaloverhaul")
                && ModList.get().isLoaded("flowing_fluids")) {
            NeoForge.EVENT_BUS.addListener(LsoHeatSnowMelting::onServerTickPre);
        }
        MSpellCompatDebug.logEarly("startup", "constructed", "mod=MSpellCompat");
    }

    private static void onConfigLoading(ModConfigEvent.Loading event) {
        if (!event.getConfig().getModId().equals(MOD_ID)) return;
        if (event.getConfig().getSpec() == MSpellCompatConfig.SPEC) {
            MSpellCompatDebug.onConfigLoaded();
            if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                MSpellCompatDebug.log("config", "common_snapshot", "debugLogging=" + MSpellCompatConfig.DEBUG_LOGGING.get()
                        + ",localWeather=" + MSpellCompatConfig.FLOWING_FLUIDS_USE_LOCAL_WEATHER.get()
                        + ",surfaceWater=" + MSpellCompatConfig.FLOWING_FLUIDS_CREATE_SURFACE_WATER.get()
                        + ",surfaceWaterDenominator=" + MSpellCompatConfig.FLOWING_FLUIDS_SURFACE_WATER_CHANCE_DENOMINATOR.get()
                        + ",surfaceWaterUnits=" + MSpellCompatConfig.FLOWING_FLUIDS_SURFACE_WATER_UNITS.get()
                        + ",lsoDrink=" + MSpellCompatConfig.LSO_CONSUME_FINITE_WATER_WHEN_DRINKING.get()
                        + ",lsoCanteen=" + MSpellCompatConfig.LSO_CANTEEN_CONSUME_FINITE_WATER.get()
                        + ",lsoSnowMelt=" + MSpellCompatConfig.LSO_HEAT_SNOW_MELTING_ENABLED.get()
                        + ",modularBackpacks=" + MSpellCompatConfig.MODULAR_BACKPACKS_FRACTIONAL_TRANSFER_ENABLED.get()
                        + ",burntWaterEnabled=" + MSpellCompatConfig.BURNT_WATER_EXTINGUISH_ENABLED.get()
                        + ",burntWater=" + MSpellCompatConfig.BURNT_COUNT_FLOWING_FLUIDS_WATER.get()
                        + ",burntLocalRain=" + MSpellCompatConfig.BURNT_REQUIRE_LOCAL_SIMPLE_CLOUDS_RAIN.get()
                        + ",treePhysicsEnabled=" + MSpellCompatConfig.TREE_PHYSICS_DAMAGE_ENABLED.get()
                        + ",treePhysicsExplosions=" + MSpellCompatConfig.TREE_PHYSICS_EXPLOSION_FELLING_ENABLED.get()
                        + ",treePhysicsBlockDamage=" + MSpellCompatConfig.TREE_PHYSICS_BLOCK_DAMAGE_FELLING_ENABLED.get());
            }
        } else if (event.getConfig().getSpec() == SableFluidPhysicsConfig.SPEC) {
            logSableImpactConfig();
        } else if (event.getConfig().getSpec() == SableFluidRuntimeConfig.SPEC) {
            if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                MSpellCompatDebug.log("config", "sable_snapshot", "enabled=" + SableFluidRuntimeConfig.ENABLED.get()
                        + ",scanInterval=" + SableFluidRuntimeConfig.SCAN_INTERVAL_TICKS.get()
                        + ",maxCellsPerSublevel=" + SableFluidRuntimeConfig.MAX_CELLS_PER_SUBLEVEL.get()
                        + ",maxCellsPerPass=" + SableFluidRuntimeConfig.MAX_CELLS_PER_PASS.get()
                        + ",maxTransfers=" + SableFluidRuntimeConfig.MAX_TRANSFERS_PER_SUBLEVEL.get()
                        + ",unitsPerTransfer=" + SableFluidRuntimeConfig.UNITS_PER_TRANSFER.get()
                        + ",massEnabled=" + SableFluidRuntimeConfig.MASS_INTEGRATION_ENABLED.get()
                        + ",massScanInterval=" + SableFluidRuntimeConfig.MASS_SCAN_INTERVAL_TICKS.get()
                        + ",sableRainScanInterval=" + SableFluidRuntimeConfig.SABLE_RAIN_SCAN_INTERVAL_TICKS.get()
                        + ",playerActivationRadius=" + SableFluidRuntimeConfig.PLAYER_ACTIVATION_RADIUS.get()
                        + ",maxMassScanCells=" + SableFluidRuntimeConfig.MASS_MAX_SCAN_CELLS.get()
                        + ",hullRefresh=" + SableFluidRuntimeConfig.MASS_HULL_REFRESH_TICKS.get()
                        + ",waterMass=" + SableFluidRuntimeConfig.MASS_WATER_MASS_PER_FULL_BLOCK.get());
            }
        }
        if (MSpellCompatDebug.isDebugLoggingEnabled()) {
            MSpellCompatDebug.log("config", "loaded", "file=" + event.getConfig().getFileName());
        }
    }

    private static void onConfigReloading(ModConfigEvent.Reloading event) {
        if (!event.getConfig().getModId().equals(MOD_ID)) return;
        if (event.getConfig().getSpec() == MSpellCompatConfig.SPEC) {
            MSpellCompatDebug.onConfigLoaded();
            if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                MSpellCompatDebug.log("config", "common_snapshot", "debugLogging=" + MSpellCompatConfig.DEBUG_LOGGING.get()
                        + ",localWeather=" + MSpellCompatConfig.FLOWING_FLUIDS_USE_LOCAL_WEATHER.get()
                        + ",surfaceWater=" + MSpellCompatConfig.FLOWING_FLUIDS_CREATE_SURFACE_WATER.get()
                        + ",lsoDrink=" + MSpellCompatConfig.LSO_CONSUME_FINITE_WATER_WHEN_DRINKING.get()
                        + ",lsoCanteen=" + MSpellCompatConfig.LSO_CANTEEN_CONSUME_FINITE_WATER.get()
                        + ",lsoSnowMelt=" + MSpellCompatConfig.LSO_HEAT_SNOW_MELTING_ENABLED.get()
                        + ",modularBackpacks=" + MSpellCompatConfig.MODULAR_BACKPACKS_FRACTIONAL_TRANSFER_ENABLED.get()
                        + ",burntWaterEnabled=" + MSpellCompatConfig.BURNT_WATER_EXTINGUISH_ENABLED.get()
                        + ",burntWater=" + MSpellCompatConfig.BURNT_COUNT_FLOWING_FLUIDS_WATER.get()
                        + ",burntLocalRain=" + MSpellCompatConfig.BURNT_REQUIRE_LOCAL_SIMPLE_CLOUDS_RAIN.get()
                        + ",treePhysicsEnabled=" + MSpellCompatConfig.TREE_PHYSICS_DAMAGE_ENABLED.get()
                        + ",treePhysicsExplosions=" + MSpellCompatConfig.TREE_PHYSICS_EXPLOSION_FELLING_ENABLED.get()
                        + ",treePhysicsBlockDamage=" + MSpellCompatConfig.TREE_PHYSICS_BLOCK_DAMAGE_FELLING_ENABLED.get());
            }
        } else if (event.getConfig().getSpec() == SableFluidPhysicsConfig.SPEC) {
            logSableImpactConfig();
        } else if (event.getConfig().getSpec() == SableFluidRuntimeConfig.SPEC) {
            if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                MSpellCompatDebug.log("config", "sable_snapshot", "enabled=" + SableFluidRuntimeConfig.ENABLED.get()
                        + ",scanInterval=" + SableFluidRuntimeConfig.SCAN_INTERVAL_TICKS.get()
                        + ",maxCellsPerSublevel=" + SableFluidRuntimeConfig.MAX_CELLS_PER_SUBLEVEL.get()
                        + ",maxCellsPerPass=" + SableFluidRuntimeConfig.MAX_CELLS_PER_PASS.get()
                        + ",maxTransfers=" + SableFluidRuntimeConfig.MAX_TRANSFERS_PER_SUBLEVEL.get()
                        + ",unitsPerTransfer=" + SableFluidRuntimeConfig.UNITS_PER_TRANSFER.get()
                        + ",massEnabled=" + SableFluidRuntimeConfig.MASS_INTEGRATION_ENABLED.get()
                        + ",massScanInterval=" + SableFluidRuntimeConfig.MASS_SCAN_INTERVAL_TICKS.get()
                        + ",sableRainScanInterval=" + SableFluidRuntimeConfig.SABLE_RAIN_SCAN_INTERVAL_TICKS.get()
                        + ",playerActivationRadius=" + SableFluidRuntimeConfig.PLAYER_ACTIVATION_RADIUS.get()
                        + ",maxMassScanCells=" + SableFluidRuntimeConfig.MASS_MAX_SCAN_CELLS.get()
                        + ",hullRefresh=" + SableFluidRuntimeConfig.MASS_HULL_REFRESH_TICKS.get()
                        + ",waterMass=" + SableFluidRuntimeConfig.MASS_WATER_MASS_PER_FULL_BLOCK.get());
            }
        }
        if (MSpellCompatDebug.isDebugLoggingEnabled()) {
            MSpellCompatDebug.log("config", "reloaded", "file=" + event.getConfig().getFileName());
        }
    }

    private static void logSableImpactConfig() {
        if (MSpellCompatDebug.isDebugLoggingEnabled()) {
            MSpellCompatDebug.log("config", "sable_impact_snapshot", "enabled="
                    + SableFluidPhysicsConfig.IMPACT_DISPLACEMENT_ENABLED.get()
                    + ",minimumMass=" + SableFluidPhysicsConfig.IMPACT_MINIMUM_MASS.get()
                    + ",minimumSpeed=" + SableFluidPhysicsConfig.IMPACT_MINIMUM_SPEED.get()
                    + ",splashRadius=" + SableFluidPhysicsConfig.IMPACT_SPLASH_RADIUS.get()
                    + ",maxSourceBlocks=" + SableFluidPhysicsConfig.IMPACT_MAX_SOURCE_BLOCKS_PER_EVENT.get()
                    + ",splashUnitsPerBlock=" + SableFluidPhysicsConfig.IMPACT_SPLASH_UNITS_PER_BLOCK.get()
                    + ",maxSplashUnitsPerTick=" + SableFluidPhysicsConfig.IMPACT_MAX_SPLASH_UNITS_PER_TICK.get()
                    + ",cooldownTicks=" + SableFluidPhysicsConfig.IMPACT_COOLDOWN_TICKS.get()
                    + ",maxEventsPerTick=" + SableFluidPhysicsConfig.IMPACT_MAX_EVENTS_PER_TICK.get()
                    + ",waterLossPercent=" + SableFluidPhysicsConfig.IMPACT_WATER_LOSS_PERCENT.get()
                    + ",displaceSourceWater=" + SableFluidPhysicsConfig.IMPACT_DISPLACE_SOURCE_WATER.get()
                    + ",useSableBeyondMass=" + SableFluidPhysicsConfig.IMPACT_USE_SABLE_BEYOND_MASS.get());
        }
    }
}
