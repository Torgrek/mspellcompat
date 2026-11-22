package com.misp.mspellcompat.mixin.burnt;

import net.minecraft.core.BlockPos;

public final class BurntFluidTickContext {
    private static final ThreadLocal<BlockPos> PREFERRED_WATER_POSITION = new ThreadLocal<>();

    private BurntFluidTickContext() {
    }

    public static BlockPos getPreferredWaterPosition() {
        return PREFERRED_WATER_POSITION.get();
    }

    public static Scope preferWaterPosition(BlockPos pos) {
        BlockPos previous = PREFERRED_WATER_POSITION.get();
        PREFERRED_WATER_POSITION.set(pos);
        return () -> {
            if (previous == null) {
                PREFERRED_WATER_POSITION.remove();
            } else {
                PREFERRED_WATER_POSITION.set(previous);
            }
        };
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
