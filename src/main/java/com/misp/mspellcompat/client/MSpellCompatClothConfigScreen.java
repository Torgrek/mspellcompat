package com.misp.mspellcompat.client;

import com.misp.mspellcompat.config.MSpellCompatConfig;
import com.misp.mspellcompat.config.SableFluidPhysicsConfig;
import com.misp.mspellcompat.util.MSpellCompatDebug;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;


public final class MSpellCompatClothConfigScreen {
    private MSpellCompatClothConfigScreen() {
    }

    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("mspellcompat.gui.title"))
                .setSavingRunnable(() -> {
                    MSpellCompatConfig.SPEC.save();
                    SableFluidPhysicsConfig.SPEC.save();
                    MSpellCompatDebug.refreshFromConfig();
                    if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                        MSpellCompatDebug.log("client_config", "settings_saved", "debugLogging="
                                + MSpellCompatConfig.DEBUG_LOGGING.get());
                    }
                });

        ConfigEntryBuilder entries = builder.entryBuilder();

        ConfigCategory diagnostics = builder.getOrCreateCategory(
                Component.translatable("mspellcompat.gui.category.diagnostics")
        );
        addBoolean(diagnostics, entries, "mspellcompat.gui.diagnostics.debug_logging.tooltip",
                "mspellcompat.gui.diagnostics.debug_logging", MSpellCompatConfig.DEBUG_LOGGING);

        ConfigCategory weather = builder.getOrCreateCategory(
                Component.translatable("mspellcompat.gui.category.weather")
        );
        addBoolean(weather, entries, "mspellcompat.gui.weather.local_refill.tooltip",
                "mspellcompat.gui.weather.local_refill", MSpellCompatConfig.FLOWING_FLUIDS_USE_LOCAL_WEATHER);
        addBoolean(weather, entries, "mspellcompat.gui.weather.create_surface_water.tooltip",
                "mspellcompat.gui.weather.create_surface_water", MSpellCompatConfig.FLOWING_FLUIDS_CREATE_SURFACE_WATER);
        addInt(weather, entries, "mspellcompat.gui.weather.surface_chance.tooltip",
                "mspellcompat.gui.weather.surface_chance", MSpellCompatConfig.FLOWING_FLUIDS_SURFACE_WATER_CHANCE_DENOMINATOR,
                1, 20000);
        addInt(weather, entries, "mspellcompat.gui.weather.surface_units.tooltip",
                "mspellcompat.gui.weather.surface_units", MSpellCompatConfig.FLOWING_FLUIDS_SURFACE_WATER_UNITS,
                1, 8);
        ConfigCategory lso = builder.getOrCreateCategory(
                Component.translatable("mspellcompat.gui.category.lso")
        );
        addBoolean(lso, entries, "mspellcompat.gui.lso.drinking.tooltip",
                "mspellcompat.gui.lso.drinking", MSpellCompatConfig.LSO_CONSUME_FINITE_WATER_WHEN_DRINKING);
        addInt(lso, entries, "mspellcompat.gui.lso.units_per_drink.tooltip",
                "mspellcompat.gui.lso.units_per_drink", MSpellCompatConfig.LSO_WATER_UNITS_PER_DRINK,
                1, 8);
        addBoolean(lso, entries, "mspellcompat.gui.lso.canteen.tooltip",
                "mspellcompat.gui.lso.canteen", MSpellCompatConfig.LSO_CANTEEN_CONSUME_FINITE_WATER);
        addInt(lso, entries, null, "mspellcompat.gui.lso.units_per_canteen_fill",
                MSpellCompatConfig.LSO_WATER_UNITS_PER_CANTEEN_FILL, 1, 8);
        ConfigCategory snow = builder.getOrCreateCategory(
                Component.translatable("mspellcompat.gui.category.snow_melting")
        );
        addBoolean(snow, entries, null, "mspellcompat.gui.lso.snow_melting",
                MSpellCompatConfig.LSO_HEAT_SNOW_MELTING_ENABLED);
        addDouble(snow, entries, null, "mspellcompat.gui.lso.snow_melting_ambient_temperature",
                MSpellCompatConfig.LSO_SNOW_MELTING_MIN_AMBIENT_TEMPERATURE, 0.0, 40.0);
        addInt(snow, entries, null, "mspellcompat.gui.lso.snow_melting_chance",
                MSpellCompatConfig.LSO_SNOW_MELTING_CHANCE_DENOMINATOR, 1, 1000);
        addInt(snow, entries, null, "mspellcompat.gui.lso.snow_melting_heat_chance",
                MSpellCompatConfig.LSO_SNOW_MELTING_HEAT_CHANCE_DENOMINATOR, 1, 1000);
        addInt(snow, entries, null, "mspellcompat.gui.lso.snow_melting_heat_radius",
                MSpellCompatConfig.LSO_SNOW_MELTING_HEAT_SOURCE_RADIUS, 1, 8);
        addInt(snow, entries, null, "mspellcompat.gui.lso.snow_block_water_units",
                MSpellCompatConfig.LSO_SNOW_BLOCK_WATER_UNITS, 1, 8);
        addInt(snow, entries, null, "mspellcompat.gui.lso.snow_layer_water_units",
                MSpellCompatConfig.LSO_SNOW_LAYER_WATER_UNITS, 1, 8);
        addInt(snow, entries, null, "mspellcompat.gui.lso.snow_melting_scan_interval",
                MSpellCompatConfig.LSO_SNOW_MELTING_SCAN_INTERVAL_TICKS, 1, 200);
        addInt(snow, entries, null, "mspellcompat.gui.lso.snow_melting_scan_radius",
                MSpellCompatConfig.LSO_SNOW_MELTING_SCAN_RADIUS, 1, 12);
        addInt(snow, entries, null, "mspellcompat.gui.lso.snow_melting_max_blocks",
                MSpellCompatConfig.LSO_SNOW_MELTING_MAX_BLOCKS_PER_SCAN, 1, 64);

        ConfigCategory backpacks = builder.getOrCreateCategory(
                Component.translatable("mspellcompat.gui.category.modular_backpacks")
        );
        addBoolean(backpacks, entries, null, "mspellcompat.gui.modular_backpacks.fractional_transfer",
                MSpellCompatConfig.MODULAR_BACKPACKS_FRACTIONAL_TRANSFER_ENABLED);

        ConfigCategory burnt = builder.getOrCreateCategory(
                Component.translatable("mspellcompat.gui.category.burnt")
        );
        addBoolean(burnt, entries, null, "mspellcompat.gui.burnt.enabled",
                MSpellCompatConfig.BURNT_WATER_EXTINGUISH_ENABLED);
        addBoolean(burnt, entries, "mspellcompat.gui.burnt.count_partial_water.tooltip",
                "mspellcompat.gui.burnt.count_partial_water", MSpellCompatConfig.BURNT_COUNT_FLOWING_FLUIDS_WATER);
        addInt(burnt, entries, "mspellcompat.gui.burnt.minimum_amount.tooltip",
                "mspellcompat.gui.burnt.minimum_amount", MSpellCompatConfig.BURNT_MIN_WATER_AMOUNT,
                1, 8);
        addInt(burnt, entries, "mspellcompat.gui.burnt.layers_consumed.tooltip",
                "mspellcompat.gui.burnt.layers_consumed",
                MSpellCompatConfig.BURNT_WATER_LAYERS_CONSUMED_PER_EXTINGUISH, 0, 8);
        addBoolean(burnt, entries, "mspellcompat.gui.burnt.local_rain.tooltip",
                "mspellcompat.gui.burnt.local_rain", MSpellCompatConfig.BURNT_REQUIRE_LOCAL_SIMPLE_CLOUDS_RAIN);

        ConfigCategory sable = builder.getOrCreateCategory(
                Component.translatable("mspellcompat.gui.category.sable_impact")
        );
        addBoolean(sable, entries, "mspellcompat.gui.sable_impact.enabled.tooltip",
                "mspellcompat.gui.sable_impact.enabled", SableFluidPhysicsConfig.IMPACT_DISPLACEMENT_ENABLED);
        addInt(sable, entries, "mspellcompat.gui.sable_impact.scan_interval.tooltip",
                "mspellcompat.gui.sable_impact.scan_interval", SableFluidPhysicsConfig.IMPACT_PROCESS_INTERVAL_TICKS,
                1, 40);
        addInt(sable, entries, "mspellcompat.gui.sable_impact.cleanup_interval.tooltip",
                "mspellcompat.gui.sable_impact.cleanup_interval", SableFluidPhysicsConfig.IMPACT_CLEANUP_INTERVAL_TICKS,
                1, 1200);
        addInt(sable, entries, "mspellcompat.gui.sable_impact.history_retention.tooltip",
                "mspellcompat.gui.sable_impact.history_retention", SableFluidPhysicsConfig.IMPACT_HISTORY_RETENTION_TICKS,
                100, 72000);
        addInt(sable, entries, "mspellcompat.gui.sable_impact.max_world_scan_cells.tooltip",
                "mspellcompat.gui.sable_impact.max_world_scan_cells", SableFluidPhysicsConfig.IMPACT_MAX_WORLD_SCAN_CELLS,
                512, 1048576);
        addDouble(sable, entries, "mspellcompat.gui.sable_impact.minimum_mass.tooltip",
                "mspellcompat.gui.sable_impact.minimum_mass", SableFluidPhysicsConfig.IMPACT_MINIMUM_MASS,
                0.0, 1.0e9);
        addDouble(sable, entries, "mspellcompat.gui.sable_impact.minimum_speed.tooltip",
                "mspellcompat.gui.sable_impact.minimum_speed", SableFluidPhysicsConfig.IMPACT_MINIMUM_SPEED,
                0.0, 1000.0);
        addInt(sable, entries, "mspellcompat.gui.sable_impact.splash_radius.tooltip",
                "mspellcompat.gui.sable_impact.splash_radius", SableFluidPhysicsConfig.IMPACT_SPLASH_RADIUS,
                1, 8);
        addInt(sable, entries, "mspellcompat.gui.sable_impact.max_source_blocks.tooltip",
                "mspellcompat.gui.sable_impact.max_source_blocks",
                SableFluidPhysicsConfig.IMPACT_MAX_SOURCE_BLOCKS_PER_EVENT, 1, 128);
        addInt(sable, entries, "mspellcompat.gui.sable_impact.units_per_splash.tooltip",
                "mspellcompat.gui.sable_impact.units_per_splash",
                SableFluidPhysicsConfig.IMPACT_SPLASH_UNITS_PER_BLOCK, 1, 8);
        addInt(sable, entries, "mspellcompat.gui.sable_impact.max_units_per_tick.tooltip",
                "mspellcompat.gui.sable_impact.max_units_per_tick",
                SableFluidPhysicsConfig.IMPACT_MAX_SPLASH_UNITS_PER_TICK, 1, 4096);
        addInt(sable, entries, "mspellcompat.gui.sable_impact.cooldown.tooltip",
                "mspellcompat.gui.sable_impact.cooldown", SableFluidPhysicsConfig.IMPACT_COOLDOWN_TICKS,
                1, 1200);
        addInt(sable, entries, "mspellcompat.gui.sable_impact.max_events_per_tick.tooltip",
                "mspellcompat.gui.sable_impact.max_events_per_tick",
                SableFluidPhysicsConfig.IMPACT_MAX_EVENTS_PER_TICK, 1, 64);
        addInt(sable, entries, "mspellcompat.gui.sable_impact.water_loss_percent.tooltip",
                "mspellcompat.gui.sable_impact.water_loss_percent",
                SableFluidPhysicsConfig.IMPACT_WATER_LOSS_PERCENT, 0, 100);
        addBoolean(sable, entries, "mspellcompat.gui.sable_impact.displace_source_water.tooltip",
                "mspellcompat.gui.sable_impact.displace_source_water",
                SableFluidPhysicsConfig.IMPACT_DISPLACE_SOURCE_WATER);
        addBoolean(sable, entries, "mspellcompat.gui.sable_impact.use_sable_beyond_mass.tooltip",
                "mspellcompat.gui.sable_impact.use_sable_beyond_mass",
                SableFluidPhysicsConfig.IMPACT_USE_SABLE_BEYOND_MASS);

        ConfigCategory treePhysics = builder.getOrCreateCategory(
                Component.translatable("mspellcompat.gui.category.tree_physics")
        );
        addBoolean(treePhysics, entries, "mspellcompat.gui.tree_physics.enabled.tooltip",
                "mspellcompat.gui.tree_physics.enabled", MSpellCompatConfig.TREE_PHYSICS_DAMAGE_ENABLED);
        addBoolean(treePhysics, entries, "mspellcompat.gui.tree_physics.explosions.tooltip",
                "mspellcompat.gui.tree_physics.explosions", MSpellCompatConfig.TREE_PHYSICS_EXPLOSION_FELLING_ENABLED);
        addBoolean(treePhysics, entries, "mspellcompat.gui.tree_physics.block_damage.tooltip",
                "mspellcompat.gui.tree_physics.block_damage", MSpellCompatConfig.TREE_PHYSICS_BLOCK_DAMAGE_FELLING_ENABLED);
        addInt(treePhysics, entries, "mspellcompat.gui.tree_physics.suppression_cleanup_interval.tooltip",
                "mspellcompat.gui.tree_physics.suppression_cleanup_interval",
                MSpellCompatConfig.TREE_PHYSICS_SUPPRESSION_CLEANUP_INTERVAL_TICKS, 1, 1200);

        return builder.build();
    }

    private static void addBoolean(
            ConfigCategory category, ConfigEntryBuilder entries,
            String tooltipKey, String labelKey, ModConfigSpec.BooleanValue value
    ) {
        var entry = entries.startBooleanToggle(Component.translatable(labelKey), value.get())
                .setDefaultValue(value.getDefault());
        if (tooltipKey != null) entry.setTooltip(Component.translatable(tooltipKey));
        category.addEntry(entry.setSaveConsumer(value::set).build());
    }

    private static void addInt(
            ConfigCategory category, ConfigEntryBuilder entries,
            String tooltipKey, String labelKey, ModConfigSpec.IntValue value, int min, int max
    ) {
        var entry = entries.startIntField(Component.translatable(labelKey), value.get())
                .setMin(min).setMax(max).setDefaultValue(value.getDefault());
        if (tooltipKey != null) entry.setTooltip(Component.translatable(tooltipKey));
        category.addEntry(entry.setSaveConsumer(value::set).build());
    }

    private static void addDouble(
            ConfigCategory category, ConfigEntryBuilder entries,
            String tooltipKey, String labelKey, ModConfigSpec.DoubleValue value, double min, double max
    ) {
        var entry = entries.startDoubleField(Component.translatable(labelKey), value.get())
                .setMin(min).setMax(max).setDefaultValue(value.getDefault());
        if (tooltipKey != null) entry.setTooltip(Component.translatable(tooltipKey));
        category.addEntry(entry.setSaveConsumer(value::set).build());
    }
}
