package com.misp.mspellcompat.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class SableFluidPhysicsConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue IMPACT_DISPLACEMENT_ENABLED;
    public static final ModConfigSpec.DoubleValue IMPACT_MINIMUM_MASS;
    public static final ModConfigSpec.DoubleValue IMPACT_MINIMUM_SPEED;
    public static final ModConfigSpec.IntValue IMPACT_SPLASH_RADIUS;
    public static final ModConfigSpec.IntValue IMPACT_MAX_SOURCE_BLOCKS_PER_EVENT;
    public static final ModConfigSpec.IntValue IMPACT_SPLASH_UNITS_PER_BLOCK;
    public static final ModConfigSpec.IntValue IMPACT_MAX_SPLASH_UNITS_PER_TICK;
    public static final ModConfigSpec.IntValue IMPACT_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue IMPACT_MAX_EVENTS_PER_TICK;
    public static final ModConfigSpec.IntValue IMPACT_WATER_LOSS_PERCENT;
    public static final ModConfigSpec.BooleanValue IMPACT_DISPLACE_SOURCE_WATER;
    public static final ModConfigSpec.BooleanValue IMPACT_USE_SABLE_BEYOND_MASS;
    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("impact_displacement");
        IMPACT_DISPLACEMENT_ENABLED = BUILDER.define("enabled", true);
        IMPACT_MINIMUM_MASS = BUILDER.defineInRange("minimum_mass", 64.0, 0.0, 1.0e9);
        IMPACT_MINIMUM_SPEED = BUILDER.defineInRange("minimum_speed", 1.5, 0.0, 1000.0);
        IMPACT_SPLASH_RADIUS = BUILDER.defineInRange("splash_radius", 3, 1, 8);
        IMPACT_MAX_SOURCE_BLOCKS_PER_EVENT = BUILDER.defineInRange("max_source_blocks_per_event", 32, 1, 128);
        IMPACT_SPLASH_UNITS_PER_BLOCK = BUILDER.defineInRange("splash_units_per_block", 4, 1, 8);
        IMPACT_MAX_SPLASH_UNITS_PER_TICK = BUILDER.defineInRange("max_splash_units_per_tick", 128, 1, 4096);
        IMPACT_COOLDOWN_TICKS = BUILDER.defineInRange("cooldown_ticks", 20, 1, 1200);
        IMPACT_MAX_EVENTS_PER_TICK = BUILDER.defineInRange("max_events_per_tick", 4, 1, 64);
        IMPACT_WATER_LOSS_PERCENT = BUILDER.defineInRange("water_loss_percent", 15, 0, 100);
        IMPACT_DISPLACE_SOURCE_WATER = BUILDER.define("displace_source_water", true);
        IMPACT_USE_SABLE_BEYOND_MASS = BUILDER.define("use_sable_beyond_mass", true);
        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private SableFluidPhysicsConfig() {
    }
}
