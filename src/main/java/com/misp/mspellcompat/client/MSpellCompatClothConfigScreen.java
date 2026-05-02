package com.misp.mspellcompat.client;

import com.misp.mspellcompat.config.MSpellCompatConfig;
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
        weather.addEntry(entries.startTextDescription(
                Component.translatable("mspellcompat.gui.notice.server_config")
        ).build());
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
}
