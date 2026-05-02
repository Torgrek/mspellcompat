package com.misp.mspellcompat;

import com.misp.mspellcompat.config.MSpellCompatConfig;
import com.misp.mspellcompat.sable.SableFluidRuntimeConfig;
import com.misp.mspellcompat.sable.SableFluidTransport;
import com.misp.mspellcompat.util.MSpellCompatDebug;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
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
                ModConfig.Type.SERVER,
                SableFluidRuntimeConfig.SPEC,
                "mspellcompat-sable-fluids-server.toml"
        );
        modEventBus.addListener(MSpellCompat::onConfigLoading);
        modEventBus.addListener(MSpellCompat::onConfigReloading);
        NeoForge.EVENT_BUS.addListener(SableFluidTransport::onServerTickPre);
        NeoForge.EVENT_BUS.addListener(SableFluidTransport::onServerTick);
        MSpellCompatDebug.logEarly("startup", "constructed", "mod=MSpellCompat");
    }

    private static void onConfigLoading(ModConfigEvent.Loading event) {
        if (!event.getConfig().getModId().equals(MOD_ID)) return;
        if (event.getConfig().getSpec() == MSpellCompatConfig.SPEC) {
            MSpellCompatDebug.onConfigLoaded();
            MSpellCompatDebug.log("config", "common_snapshot", "debugLogging=" + MSpellCompatConfig.DEBUG_LOGGING.get()
                    + ",localWeather=" + MSpellCompatConfig.FLOWING_FLUIDS_USE_LOCAL_WEATHER.get()
                    + ",surfaceWater=" + MSpellCompatConfig.FLOWING_FLUIDS_CREATE_SURFACE_WATER.get()
                    + ",surfaceWaterDenominator=" + MSpellCompatConfig.FLOWING_FLUIDS_SURFACE_WATER_CHANCE_DENOMINATOR.get()
                    + ",surfaceWaterUnits=" + MSpellCompatConfig.FLOWING_FLUIDS_SURFACE_WATER_UNITS.get()
                    + ",lsoDrink=" + MSpellCompatConfig.LSO_CONSUME_FINITE_WATER_WHEN_DRINKING.get()
                    + ",lsoCanteen=" + MSpellCompatConfig.LSO_CANTEEN_CONSUME_FINITE_WATER.get()
                    + ",burntWater=" + MSpellCompatConfig.BURNT_COUNT_FLOWING_FLUIDS_WATER.get()
                    + ",burntLocalRain=" + MSpellCompatConfig.BURNT_REQUIRE_LOCAL_SIMPLE_CLOUDS_RAIN.get());
        } else if (event.getConfig().getSpec() == SableFluidRuntimeConfig.SPEC) {
            MSpellCompatDebug.log("config", "sable_snapshot", "enabled=" + SableFluidRuntimeConfig.ENABLED.get()
                    + ",scanInterval=" + SableFluidRuntimeConfig.SCAN_INTERVAL_TICKS.get()
                    + ",maxCellsPerSublevel=" + SableFluidRuntimeConfig.MAX_CELLS_PER_SUBLEVEL.get()
                    + ",maxCellsPerPass=" + SableFluidRuntimeConfig.MAX_CELLS_PER_PASS.get()
                    + ",maxTransfers=" + SableFluidRuntimeConfig.MAX_TRANSFERS_PER_SUBLEVEL.get()
                    + ",unitsPerTransfer=" + SableFluidRuntimeConfig.UNITS_PER_TRANSFER.get());
        }
        MSpellCompatDebug.log("config", "loaded", "file=" + event.getConfig().getFileName());
    }

    private static void onConfigReloading(ModConfigEvent.Reloading event) {
        if (!event.getConfig().getModId().equals(MOD_ID)) return;
        if (event.getConfig().getSpec() == MSpellCompatConfig.SPEC) {
            MSpellCompatDebug.onConfigLoaded();
            MSpellCompatDebug.log("config", "common_snapshot", "debugLogging=" + MSpellCompatConfig.DEBUG_LOGGING.get()
                    + ",localWeather=" + MSpellCompatConfig.FLOWING_FLUIDS_USE_LOCAL_WEATHER.get()
                    + ",surfaceWater=" + MSpellCompatConfig.FLOWING_FLUIDS_CREATE_SURFACE_WATER.get()
                    + ",lsoDrink=" + MSpellCompatConfig.LSO_CONSUME_FINITE_WATER_WHEN_DRINKING.get()
                    + ",lsoCanteen=" + MSpellCompatConfig.LSO_CANTEEN_CONSUME_FINITE_WATER.get()
                    + ",burntWater=" + MSpellCompatConfig.BURNT_COUNT_FLOWING_FLUIDS_WATER.get()
                    + ",burntLocalRain=" + MSpellCompatConfig.BURNT_REQUIRE_LOCAL_SIMPLE_CLOUDS_RAIN.get());
        } else if (event.getConfig().getSpec() == SableFluidRuntimeConfig.SPEC) {
            MSpellCompatDebug.log("config", "sable_snapshot", "enabled=" + SableFluidRuntimeConfig.ENABLED.get()
                    + ",scanInterval=" + SableFluidRuntimeConfig.SCAN_INTERVAL_TICKS.get()
                    + ",maxCellsPerSublevel=" + SableFluidRuntimeConfig.MAX_CELLS_PER_SUBLEVEL.get()
                    + ",maxCellsPerPass=" + SableFluidRuntimeConfig.MAX_CELLS_PER_PASS.get()
                    + ",maxTransfers=" + SableFluidRuntimeConfig.MAX_TRANSFERS_PER_SUBLEVEL.get()
                    + ",unitsPerTransfer=" + SableFluidRuntimeConfig.UNITS_PER_TRANSFER.get());
        }
        MSpellCompatDebug.log("config", "reloaded", "file=" + event.getConfig().getFileName());
    }
}
