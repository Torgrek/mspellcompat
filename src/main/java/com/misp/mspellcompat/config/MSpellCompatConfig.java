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
    public static final ModConfigSpec.IntValue LSO_WATER_UNITS_PER_CANTEEN_FILL;
    public static final ModConfigSpec.BooleanValue LSO_HEAT_SNOW_MELTING_ENABLED;
    public static final ModConfigSpec.DoubleValue LSO_SNOW_MELTING_MIN_TEMPERATURE_DELTA;
    public static final ModConfigSpec.DoubleValue LSO_SNOW_MELTING_MIN_AMBIENT_TEMPERATURE;
    public static final ModConfigSpec.IntValue LSO_SNOW_MELTING_REFERENCE_DISTANCE;
    public static final ModConfigSpec.IntValue LSO_SNOW_MELTING_CHANCE_DENOMINATOR;
    public static final ModConfigSpec.IntValue LSO_SNOW_MELTING_HEAT_CHANCE_DENOMINATOR;
    public static final ModConfigSpec.IntValue LSO_SNOW_MELTING_HEAT_SOURCE_RADIUS;
    public static final ModConfigSpec.IntValue LSO_SNOW_BLOCK_WATER_UNITS;
    public static final ModConfigSpec.IntValue LSO_SNOW_LAYER_WATER_UNITS;
    public static final ModConfigSpec.IntValue LSO_SNOW_MELTING_SCAN_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue LSO_SNOW_MELTING_SCAN_RADIUS;
    public static final ModConfigSpec.IntValue LSO_SNOW_MELTING_MAX_BLOCKS_PER_SCAN;
    public static final ModConfigSpec.BooleanValue MODULAR_BACKPACKS_FRACTIONAL_TRANSFER_ENABLED;
    public static final ModConfigSpec.BooleanValue BURNT_WATER_EXTINGUISH_ENABLED;
    public static final ModConfigSpec.BooleanValue BURNT_COUNT_FLOWING_FLUIDS_WATER;
    public static final ModConfigSpec.IntValue BURNT_MIN_WATER_AMOUNT;
    public static final ModConfigSpec.IntValue BURNT_WATER_LAYERS_CONSUMED_PER_EXTINGUISH;
    public static final ModConfigSpec.BooleanValue BURNT_REQUIRE_LOCAL_SIMPLE_CLOUDS_RAIN;
    public static final ModConfigSpec.BooleanValue TREE_PHYSICS_DAMAGE_ENABLED;
    public static final ModConfigSpec.BooleanValue TREE_PHYSICS_EXPLOSION_FELLING_ENABLED;
    public static final ModConfigSpec.BooleanValue TREE_PHYSICS_BLOCK_DAMAGE_FELLING_ENABLED;
    public static final ModConfigSpec.IntValue TREE_PHYSICS_SUPPRESSION_CLEANUP_INTERVAL_TICKS;
    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("diagnostics");
        DEBUG_LOGGING = BUILDER.comment("Enable diagnostic logs.").define("debug_logging", false);
        BUILDER.pop();

        BUILDER.push("flowing_fluids_simple_clouds");
        FLOWING_FLUIDS_USE_LOCAL_WEATHER = BUILDER.comment("Use local precipitation for fluid refill.")
                .define("use_local_precipitation_for_refill", true);
        FLOWING_FLUIDS_CREATE_SURFACE_WATER = BUILDER.comment("Enable surface precipitation accumulation: Flowing Fluids water during rain and snow layers during snowfall.")
                .define("create_surface_water_from_local_rain", true);
        FLOWING_FLUIDS_SURFACE_WATER_CHANCE_DENOMINATOR = BUILDER.comment("Shared precipitation-layer chance denominator for ordinary terrain and Sable sublevels; used for rainwater and snow layers.")
                .defineInRange("surface_water_chance_denominator", 1200, 1, 20000);
        FLOWING_FLUIDS_SURFACE_WATER_UNITS = BUILDER.comment("Flowing Fluids water units created per rain event; snowfall creates one snow layer per successful attempt.")
                .defineInRange("surface_water_units", 1, 1, 8);
        BUILDER.pop();

        BUILDER.push("lso_flowing_fluids_drinking");
        LSO_CONSUME_FINITE_WATER_WHEN_DRINKING = BUILDER.comment("Consume water layers when drinking.")
                .define("consume_finite_water_when_drinking", true);
        LSO_WATER_UNITS_PER_DRINK = BUILDER.comment("Water units consumed per drink.")
                .defineInRange("water_units_per_drink", 1, 1, 8);
        LSO_CANTEEN_CONSUME_FINITE_WATER = BUILDER.comment("Consume water layers when filling a canteen.")
                .define("consume_finite_water_when_filling_canteen", true);
        LSO_WATER_UNITS_PER_CANTEEN_FILL = BUILDER.comment("Water units consumed per fill.")
                .defineInRange("water_units_per_canteen_fill", 1, 1, 8);
        BUILDER.pop();

        BUILDER.push("lso_heat_snow_melting");
        LSO_HEAT_SNOW_MELTING_ENABLED = BUILDER.comment("Melt snow near blocks that raise LSO world temperature.")
                .define("enabled", true);
        LSO_SNOW_MELTING_MIN_TEMPERATURE_DELTA = BUILDER.comment("Legacy setting retained for compatibility; snow melting now uses the LSO temperature at the snow position.")
                .defineInRange("minimum_temperature_delta", 1.0, 0.1, 50.0);
        LSO_SNOW_MELTING_MIN_AMBIENT_TEMPERATURE = BUILDER.comment("Minimum LSO world temperature at the snow position required for melting.")
                .defineInRange("minimum_ambient_temperature", 24.0, 0.0, 40.0);
        LSO_SNOW_MELTING_REFERENCE_DISTANCE = BUILDER.comment("Legacy setting retained for compatibility; no reference positions are sampled.")
                .defineInRange("reference_distance", 4, 1, 16);
        LSO_SNOW_MELTING_CHANCE_DENOMINATOR = BUILDER.comment("Lower values make ambient-temperature melting more frequent.")
                .defineInRange("chance_denominator", 1, 1, 1000);
        LSO_SNOW_MELTING_HEAT_CHANCE_DENOMINATOR = BUILDER.comment("Lower values make melting from LSO-configured heat sources more frequent.")
                .defineInRange("heat_chance_denominator", 2, 1, 1000);
        LSO_SNOW_MELTING_HEAT_SOURCE_RADIUS = BUILDER.comment("Radius for finding blocks with positive temperature entries in LSO datapacks.")
                .defineInRange("heat_source_scan_radius", 2, 1, 8);
        LSO_SNOW_BLOCK_WATER_UNITS = BUILDER.comment("Water units produced by a full snow block.")
                .defineInRange("snow_block_water_units", 8, 1, 8);
        LSO_SNOW_LAYER_WATER_UNITS = BUILDER.comment("Water units produced by a snow layer block.")
                .defineInRange("snow_layer_water_units", 2, 1, 8);
        LSO_SNOW_MELTING_SCAN_INTERVAL_TICKS = BUILDER.comment("Ticks between snow scans.")
                .defineInRange("scan_interval_ticks", 20, 1, 200);
        LSO_SNOW_MELTING_SCAN_RADIUS = BUILDER.comment("Horizontal snow scan radius around players.")
                .defineInRange("scan_radius", 4, 1, 12);
        LSO_SNOW_MELTING_MAX_BLOCKS_PER_SCAN = BUILDER.comment("Maximum snow blocks melted per scan.")
                .defineInRange("max_blocks_per_scan", 1, 1, 64);
        BUILDER.pop();

        BUILDER.push("modular_backpacks_flowing_fluids");
        MODULAR_BACKPACKS_FRACTIONAL_TRANSFER_ENABLED = BUILDER.comment("Enable partial fluid transfers in tanks.")
                .define("fractional_tank_transfer_enabled", true);
        BUILDER.pop();

        BUILDER.push("burnt_basic_flowing_fluids");
        BURNT_WATER_EXTINGUISH_ENABLED = BUILDER.comment("Enable extinguishing Burnt blocks with water layers.")
                .define("water_layer_extinguishing_enabled", true);
        BURNT_COUNT_FLOWING_FLUIDS_WATER = BUILDER.comment("Count Flowing Fluids layers as adjacent water.")
                .define("count_flowing_fluids_water_as_adjacent_water", true);
        BURNT_MIN_WATER_AMOUNT = BUILDER.comment("Minimum water units required for extinguishing.")
                .defineInRange("minimum_adjacent_water_amount", 1, 1, 8);
        BURNT_WATER_LAYERS_CONSUMED_PER_EXTINGUISH = BUILDER
                .comment("Water units consumed per extinguish.")
                .defineInRange("water_layers_consumed_per_extinguish", 1, 0, 8);
        BUILDER.pop();

        BUILDER.push("burnt_basic_simple_clouds_rain");
        BURNT_REQUIRE_LOCAL_SIMPLE_CLOUDS_RAIN = BUILDER
                .comment("Use local Simple Clouds rain for Burnt rain checks.")
                .define("require_local_simple_clouds_rain_for_extinguishing", true);
        BUILDER.pop();

        BUILDER.push("tree_physics_damage");
        TREE_PHYSICS_DAMAGE_ENABLED = BUILDER.comment("Enable MSpellCompat integration with Tree Physics.")
                .define("enabled", true);
        TREE_PHYSICS_EXPLOSION_FELLING_ENABLED = BUILDER.comment("Allow explosions to trigger Tree Physics tree falling.")
                .define("explosions_trigger_felling", true);
        TREE_PHYSICS_BLOCK_DAMAGE_FELLING_ENABLED = BUILDER.comment("Allow non-player block destruction to trigger Tree Physics tree falling.")
                .define("block_damage_triggers_felling", true);
        TREE_PHYSICS_SUPPRESSION_CLEANUP_INTERVAL_TICKS = BUILDER.comment("Ticks between cleanup passes for temporary Tree Physics block-replacement guards.")
                .defineInRange("suppression_cleanup_interval_ticks", 20, 1, 1200);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private MSpellCompatConfig() {
    }
}
