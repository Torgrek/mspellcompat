package com.misp.mspellcompat.util;

import com.misp.mspellcompat.config.MSpellCompatConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class MSpellCompatDebug {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_EARLY_EVENTS = 1024;
    private static final List<String> EARLY_EVENTS = new ArrayList<>();
    private static volatile boolean configResolved;
    private static volatile boolean debugEnabled;

    private MSpellCompatDebug() {
    }

    public static boolean isDebugLoggingEnabled() {
        return debugEnabled;
    }

    public static boolean isDebugLoggingDisabled() {
        return !debugEnabled;
    }

    public static void refreshFromConfig() {
        try {
            debugEnabled = MSpellCompatConfig.DEBUG_LOGGING.get();
        } catch (Throwable ignored) {
            debugEnabled = false;
        }
    }

    public static void log(String area, String event, String details) {
        if (isDebugLoggingDisabled()) return;
        LOGGER.debug("[MSpellCompatDebug][{}] {} | {}", area, event, details);
    }

    public static void logEarly(String area, String event, String details) {
        if (configResolved) {
            log(area, event, details);
            return;
        }
        synchronized (EARLY_EVENTS) {
            if (EARLY_EVENTS.size() < MAX_EARLY_EVENTS) {
                EARLY_EVENTS.add("[MSpellCompatDebug][" + area + "] " + event + " | " + details);
            }
        }
    }

    public static void onConfigLoaded() {
        configResolved = true;
        refreshFromConfig();
        if (isDebugLoggingDisabled()) {
            synchronized (EARLY_EVENTS) {
                EARLY_EVENTS.clear();
            }
            return;
        }
        LOGGER.debug("[MSpellCompatDebug][diagnostics] debug_logging enabled");
        synchronized (EARLY_EVENTS) {
            for (String event : EARLY_EVENTS) {
                LOGGER.debug(event);
            }
            EARLY_EVENTS.clear();
        }
    }

    public static void log(String area, String event, Supplier<String> details) {
        if (isDebugLoggingDisabled()) return;
        try {
            LOGGER.debug("[MSpellCompatDebug][{}] {} | {}", area, event, details.get());
        } catch (Throwable failure) {
            LOGGER.debug("[MSpellCompatDebug][{}] {} | diagnostic details failed", area, event, failure);
        }
    }

    public static void debug(String message, Object... arguments) {
        if (isDebugLoggingDisabled()) return;
        LOGGER.debug(message, arguments);
    }

    public static void debugException(String message, Throwable failure) {
        if (isDebugLoggingDisabled()) return;
        LOGGER.debug(message, failure);
    }

    public static void error(String area, String event, Throwable failure, String details) {
        if (isDebugLoggingDisabled()) return;
        LOGGER.debug("[MSpellCompatDebug][{}] {} | {}", area, event, details, failure);
    }

    public static String describeLevel(LevelAccessor level) {
        if (level == null) return "level=null";
        if (level instanceof Level mcLevel) {
            return "level=" + mcLevel.getClass().getSimpleName()
                    + ",dimension=" + mcLevel.dimension().location()
                    + ",client=" + mcLevel.isClientSide
                    + ",gameTime=" + mcLevel.getGameTime();
        }
        return "level=" + level.getClass().getName();
    }

    public static String describeFluid(LevelAccessor level, BlockPos pos) {
        if (level == null || pos == null) return "pos=" + pos + ",state=unavailable";
        BlockState block = level.getBlockState(pos);
        FluidState fluid = level.getFluidState(pos);
        String blockId;
        String fluidId;
        try {
            blockId = BuiltInRegistries.BLOCK.getKey(block.getBlock()).toString();
        } catch (Throwable ignored) {
            blockId = String.valueOf(block.getBlock());
        }
        try {
            Fluid type = fluid.getType();
            fluidId = fluid.isEmpty() ? "none" : BuiltInRegistries.FLUID.getKey(type).toString();
        } catch (Throwable ignored) {
            fluidId = String.valueOf(fluid.getType());
        }
        return "pos=" + pos + ",block=" + blockId + ",blockState=" + block
                + ",fluid=" + fluidId + ",amount=" + fluid.getAmount()
                + ",source=" + fluid.isSource() + ",fluidState=" + fluid;
    }

}
