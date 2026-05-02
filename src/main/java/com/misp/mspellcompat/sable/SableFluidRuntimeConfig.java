package com.misp.mspellcompat.sable;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class SableFluidRuntimeConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.IntValue SCAN_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue MAX_CELLS_PER_SUBLEVEL;
    public static final ModConfigSpec.IntValue MAX_CELLS_PER_PASS;
    public static final ModConfigSpec.IntValue MAX_TRANSFERS_PER_SUBLEVEL;
    public static final ModConfigSpec.IntValue UNITS_PER_TRANSFER;
    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("sable_flowing_fluids_block_transport");
        ENABLED = BUILDER.define("enabled", true);
        SCAN_INTERVAL_TICKS = BUILDER.defineInRange("scan_interval_ticks", 5, 1, 40);
        MAX_CELLS_PER_SUBLEVEL = BUILDER.defineInRange("max_cells_per_sublevel", 8192, 27, 65536);
        MAX_CELLS_PER_PASS = BUILDER.defineInRange("max_cells_per_pass", 16384, 64, 262144);
        MAX_TRANSFERS_PER_SUBLEVEL = BUILDER.defineInRange("max_transfers_per_sublevel", 8, 1, 64);
        UNITS_PER_TRANSFER = BUILDER.defineInRange("units_per_transfer", 1, 1, 4);
        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private SableFluidRuntimeConfig() {
    }
}
