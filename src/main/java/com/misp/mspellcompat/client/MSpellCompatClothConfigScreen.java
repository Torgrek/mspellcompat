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
                    MSpellCompatDebug.log("client_config", "settings_saved", "debugLogging="
                            + MSpellCompatConfig.DEBUG_LOGGING.get());
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

        ConfigCategory burnt = builder.getOrCreateCategory(
                Component.translatable("mspellcompat.gui.category.burnt")
        );
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

        return builder.build();
    }

    private static void addBoolean(
            ConfigCategory category, ConfigEntryBuilder entries,
            String tooltipKey, String labelKey, ModConfigSpec.BooleanValue value
    ) {
        category.addEntry(entries.startBooleanToggle(Component.translatable(labelKey), value.get())
                .setDefaultValue(value.getDefault())
                .setTooltip(Component.translatable(tooltipKey))
                .setSaveConsumer(value::set)
                .build());
    }

    private static void addInt(
            ConfigCategory category, ConfigEntryBuilder entries,
            String tooltipKey, String labelKey, ModConfigSpec.IntValue value, int min, int max
    ) {
        category.addEntry(entries.startIntField(Component.translatable(labelKey), value.get())
                .setMin(min).setMax(max).setDefaultValue(value.getDefault())
                .setTooltip(Component.translatable(tooltipKey))
                .setSaveConsumer(value::set)
                .build());
    }

    private static void addDouble(
            ConfigCategory category, ConfigEntryBuilder entries,
            String tooltipKey, String labelKey, ModConfigSpec.DoubleValue value, double min, double max
    ) {
        category.addEntry(entries.startDoubleField(Component.translatable(labelKey), value.get())
                .setMin(min).setMax(max).setDefaultValue(value.getDefault())
                .setTooltip(Component.translatable(tooltipKey))
                .setSaveConsumer(value::set)
                .build());
    }
}
