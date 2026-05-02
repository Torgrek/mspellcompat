package com.misp.mspellcompat.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class MSpellCompatConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue DEBUG_LOGGING;
    public static final ModConfigSpec.BooleanValue FLOWING_FLUIDS_USE_LOCAL_WEATHER;
    public static final ModConfigSpec.BooleanValue FLOWING_FLUIDS_CREATE_SURFACE_WATER;
    public static final ModConfigSpec.IntValue FLOWING_FLUIDS_SURFACE_WATER_CHANCE_DENOMINATOR;
    public static final ModConfigSpec.IntValue FLOWING_FLUIDS_SURFACE_WATER_UNITS;
    public static final ModConfigSpec.BooleanValue LSO_CONSUME_FINITE_WATER_WHEN_DRINKING;
    public static final ModConfigSpec.IntValue LSO_WATER_UNITS_PER_DRINK;
    public static final ModConfigSpec.BooleanValue LSO_CANTEEN_CONSUME_FINITE_WATER;
    public static final ModConfigSpec.BooleanValue BURNT_COUNT_FLOWING_FLUIDS_WATER;
    public static final ModConfigSpec.IntValue BURNT_MIN_WATER_AMOUNT;
    public static final ModConfigSpec.IntValue BURNT_WATER_LAYERS_CONSUMED_PER_EXTINGUISH;
    public static final ModConfigSpec.BooleanValue BURNT_REQUIRE_LOCAL_SIMPLE_CLOUDS_RAIN;
    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("diagnostics");
        DEBUG_LOGGING = BUILDER.define("debug_logging", false);
        BUILDER.pop();

        BUILDER.push("flowing_fluids_simple_clouds");
        FLOWING_FLUIDS_USE_LOCAL_WEATHER = BUILDER.define("use_local_precipitation_for_refill", true);
        FLOWING_FLUIDS_CREATE_SURFACE_WATER = BUILDER.define("create_surface_water_from_local_rain", true);
        FLOWING_FLUIDS_SURFACE_WATER_CHANCE_DENOMINATOR = BUILDER
                .defineInRange("surface_water_chance_denominator", 1200, 1, 20000);
        FLOWING_FLUIDS_SURFACE_WATER_UNITS = BUILDER.defineInRange("surface_water_units", 1, 1, 8);
        BUILDER.pop();

        BUILDER.push("lso_flowing_fluids_drinking");
        LSO_CONSUME_FINITE_WATER_WHEN_DRINKING = BUILDER.define("consume_finite_water_when_drinking", true);
        LSO_WATER_UNITS_PER_DRINK = BUILDER.defineInRange("water_units_per_drink", 1, 1, 8);
        LSO_CANTEEN_CONSUME_FINITE_WATER = BUILDER.define("consume_finite_water_when_filling_canteen", true);
        BUILDER.pop();

        BUILDER.push("burnt_basic_flowing_fluids");
        BURNT_COUNT_FLOWING_FLUIDS_WATER = BUILDER.define("count_flowing_fluids_water_as_adjacent_water", true);
        BURNT_MIN_WATER_AMOUNT = BUILDER.defineInRange("minimum_adjacent_water_amount", 1, 1, 8);
        BURNT_WATER_LAYERS_CONSUMED_PER_EXTINGUISH = BUILDER
                .defineInRange("water_layers_consumed_per_extinguish", 1, 0, 8);
        BUILDER.pop();

        BUILDER.push("burnt_basic_simple_clouds_rain");
        BURNT_REQUIRE_LOCAL_SIMPLE_CLOUDS_RAIN = BUILDER
                .define("require_local_simple_clouds_rain_for_extinguishing", true);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private MSpellCompatConfig() {
    }
}
