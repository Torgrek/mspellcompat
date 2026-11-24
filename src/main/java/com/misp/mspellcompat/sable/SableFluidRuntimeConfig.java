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
    public static final ModConfigSpec.BooleanValue MASS_INTEGRATION_ENABLED;
    public static final ModConfigSpec.IntValue MASS_SCAN_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue MASS_MAX_SCAN_CELLS;
    public static final ModConfigSpec.IntValue MASS_HULL_REFRESH_TICKS;
    public static final ModConfigSpec.DoubleValue MASS_WATER_MASS_PER_FULL_BLOCK;
    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("sable_flowing_fluids_block_transport");
        ENABLED = BUILDER.comment("Enable water transfer between Sable and the world.").define("enabled", true);
        SCAN_INTERVAL_TICKS = BUILDER.comment("Ticks between fluid transfer passes.")
                .defineInRange("scan_interval_ticks", 5, 1, 40);
        MAX_CELLS_PER_SUBLEVEL = BUILDER.comment("Maximum cells scanned per sublevel.")
                .defineInRange("max_cells_per_sublevel", 8192, 27, 65536);
        MAX_CELLS_PER_PASS = BUILDER.comment("Maximum cells scanned per pass.")
                .defineInRange("max_cells_per_pass", 16384, 64, 262144);
        MAX_TRANSFERS_PER_SUBLEVEL = BUILDER.comment("Maximum fluid transfers per sublevel pass.")
                .defineInRange("max_transfers_per_sublevel", 8, 1, 64);
        UNITS_PER_TRANSFER = BUILDER.comment("Maximum water units transferred per step.")
                .defineInRange("units_per_transfer", 1, 1, 4);
        BUILDER.pop();

        BUILDER.push("sable_fluid_mass");
        MASS_INTEGRATION_ENABLED = BUILDER.comment("Include water mass in Sable structure mass.").define("enabled", true);
        MASS_SCAN_INTERVAL_TICKS = BUILDER.comment("Ticks between mass scans.")
                .defineInRange("scan_interval_ticks", 5, 1, 40);
        MASS_MAX_SCAN_CELLS = BUILDER.comment("Maximum cells read in one structure bounds scan.")
                .defineInRange("max_scan_cells", 262144, 27, 1048576);
        MASS_HULL_REFRESH_TICKS = BUILDER.comment("Ticks between hull recalculations.")
                .defineInRange("hull_refresh_ticks", 100, 1, 1200);
        MASS_WATER_MASS_PER_FULL_BLOCK = BUILDER.comment("Mass assigned to a full water block.")
                .defineInRange("water_mass_per_full_block", 1.0, 0.0, 1000.0);
        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private SableFluidRuntimeConfig() {
    }
}
