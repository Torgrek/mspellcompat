package com.misp.mspellcompat.lso;

import com.misp.mspellcompat.config.MSpellCompatConfig;
import com.misp.mspellcompat.util.MSpellCompatDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import traben.flowing_fluids.FFFluidUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

@EventBusSubscriber(modid = "mspellcompat")
public final class LsoHeatSnowMelting {
    private static volatile Method worldTemperatureMethod;
    private static volatile boolean worldTemperatureMethodResolved;
    private static volatile Method lsoBlockTemperatureDataMethod;
    private static volatile Method lsoBlockTemperatureMatchesStateMethod;
    private static volatile Field lsoBlockTemperatureValueField;
    private static volatile boolean lsoBlockTemperatureDataResolved;

    private LsoHeatSnowMelting() {
    }

    @SubscribeEvent
    public static void onServerTickPre(ServerTickEvent.Pre event) {
        if (!MSpellCompatConfig.LSO_HEAT_SNOW_MELTING_ENABLED.get()
                || !ModList.get().isLoaded("legendarysurvivaloverhaul")
                || !ModList.get().isLoaded("flowing_fluids")) return;

        MinecraftServer server = event.getServer();
        int scanInterval = MSpellCompatConfig.LSO_SNOW_MELTING_SCAN_INTERVAL_TICKS.get();
        if (server.overworld().getGameTime() % scanInterval != 0L) return;

        int radius = MSpellCompatConfig.LSO_SNOW_MELTING_SCAN_RADIUS.get();
        int maxMelted = MSpellCompatConfig.LSO_SNOW_MELTING_MAX_BLOCKS_PER_SCAN.get();
        int melted = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Player player : level.players()) {
                BlockPos base = player.blockPosition();
                for (int x = -radius; x <= radius && melted < maxMelted; x++) {
                    for (int z = -radius; z <= radius && melted < maxMelted; z++) {
                        for (int y = -2; y <= 2 && melted < maxMelted; y++) {
                            BlockPos pos = base.offset(x, y, z);
                            if (!level.hasChunkAt(pos)) continue;
                            BlockState state = level.getBlockState(pos);
                            if (!isSnow(state)) continue;
                            if (tryMelt(level, pos, state, level.getRandom())) melted++;
                        }
                    }
                    if (melted >= maxMelted) break;
                }
                if (melted >= maxMelted) return;
            }
        }
    }

    public static boolean tryMelt(ServerLevel level, BlockPos pos, BlockState state, net.minecraft.util.RandomSource random) {
        if (!MSpellCompatConfig.LSO_HEAT_SNOW_MELTING_ENABLED.get() || !isSnow(state)) return false;

        float temperature;
        try {
            temperature = worldTemperature(level, pos);
        } catch (Throwable ignored) {
            return false;
        }

        if (!Float.isFinite(temperature)) return false;

        boolean ambientHot = temperature >= MSpellCompatConfig.LSO_SNOW_MELTING_MIN_AMBIENT_TEMPERATURE.get();
        boolean heatDriven = !ambientHot && hasConfiguredHeatSource(level, pos);
        if (!ambientHot && !heatDriven) return false;

        int chanceDenominator = heatDriven
                ? MSpellCompatConfig.LSO_SNOW_MELTING_HEAT_CHANCE_DENOMINATOR.get()
                : MSpellCompatConfig.LSO_SNOW_MELTING_CHANCE_DENOMINATOR.get();
        if (chanceDenominator > 1 && random.nextInt(chanceDenominator) != 0) return false;

        int units = state.is(Blocks.SNOW_BLOCK)
                ? MSpellCompatConfig.LSO_SNOW_BLOCK_WATER_UNITS.get()
                : MSpellCompatConfig.LSO_SNOW_LAYER_WATER_UNITS.get();

        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        boolean placed = FFFluidUtils.setFluidStateAtPosToNewAmount(level, pos, Fluids.WATER, units);
        FluidState result = level.getFluidState(pos);
        if (!placed || result.isEmpty()
                || (result.getType() != Fluids.WATER && result.getType() != Fluids.FLOWING_WATER)) {
            level.setBlock(pos, state, Block.UPDATE_ALL);
            return false;
        }

        MSpellCompatDebug.log("lso_snow_melting", "snow_melted", "pos=" + pos + ",temperature="
                + temperature + ",source=" + (heatDriven ? "heat" : "ambient") + ",units=" + units);
        return true;
    }

    private static boolean hasConfiguredHeatSource(ServerLevel level, BlockPos snowPos) {
        int radius = MSpellCompatConfig.LSO_SNOW_MELTING_HEAT_SOURCE_RADIUS.get();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radius * radius) continue;
                    BlockPos sourcePos = snowPos.offset(dx, dy, dz);
                    if (!level.hasChunkAt(sourcePos)) continue;

                    BlockState sourceState = level.getBlockState(sourcePos);
                    ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(sourceState.getBlock());
                    if (blockId == null) continue;

                    try {
                        List<?> temperatureEntries = getLsoBlockTemperatureDataMethod().invoke(null, blockId) instanceof List<?> list
                                ? list : List.of();
                        for (Object entry : temperatureEntries) {
                            float sourceTemperature = lsoBlockTemperatureValueField.getFloat(entry);
                            if (sourceTemperature <= 0.0F) continue;
                            if (Boolean.TRUE.equals(lsoBlockTemperatureMatchesStateMethod.invoke(entry, sourceState))) {
                                return true;
                            }
                        }
                    } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
                        return false;
                    }
                }
            }
        }
        return false;
    }

    private static Method getLsoBlockTemperatureDataMethod() throws ReflectiveOperationException {
        if (lsoBlockTemperatureDataResolved) {
            if (lsoBlockTemperatureDataMethod == null) throw new ReflectiveOperationException("LSO block temperature data API unavailable");
            return lsoBlockTemperatureDataMethod;
        }
        synchronized (LsoHeatSnowMelting.class) {
            if (!lsoBlockTemperatureDataResolved) {
                try {
                    ClassLoader loader = LsoHeatSnowMelting.class.getClassLoader();
                    Class<?> manager = Class.forName(
                            "sfiomn.legendarysurvivaloverhaul.api.data.manager.TemperatureDataManager", true, loader);
                    Class<?> entryType = Class.forName(
                            "sfiomn.legendarysurvivaloverhaul.api.data.json.JsonTemperatureBlock", true, loader);
                    lsoBlockTemperatureDataMethod = manager.getMethod("getBlock", ResourceLocation.class);
                    lsoBlockTemperatureMatchesStateMethod = entryType.getMethod("matchesState", BlockState.class);
                    lsoBlockTemperatureValueField = entryType.getField("temperature");
                } catch (ReflectiveOperationException failure) {
                    lsoBlockTemperatureDataMethod = null;
                    lsoBlockTemperatureMatchesStateMethod = null;
                    lsoBlockTemperatureValueField = null;
                    throw failure;
                } finally {
                    lsoBlockTemperatureDataResolved = true;
                }
            }
        }
        if (lsoBlockTemperatureDataMethod == null
                || lsoBlockTemperatureMatchesStateMethod == null
                || lsoBlockTemperatureValueField == null) {
            throw new ReflectiveOperationException("LSO block temperature data API unavailable");
        }
        return lsoBlockTemperatureDataMethod;
    }

    private static boolean isSnow(BlockState state) {
        return state.is(Blocks.SNOW_BLOCK) || state.hasProperty(SnowLayerBlock.LAYERS);
    }

    private static float worldTemperature(Level level, BlockPos pos) throws ReflectiveOperationException {
        Method method = getWorldTemperatureMethod();
        Object value = method.invoke(null, level, pos);
        if (!(value instanceof Number number)) throw new ReflectiveOperationException("Invalid temperature result");
        return number.floatValue();
    }

    private static Method getWorldTemperatureMethod() throws ReflectiveOperationException {
        if (worldTemperatureMethodResolved) {
            if (worldTemperatureMethod == null) throw new ReflectiveOperationException("LSO temperature API unavailable");
            return worldTemperatureMethod;
        }
        synchronized (LsoHeatSnowMelting.class) {
            if (!worldTemperatureMethodResolved) {
                try {
                    Class<?> type = Class.forName("sfiomn.legendarysurvivaloverhaul.api.temperature.TemperatureUtil", true,
                            LsoHeatSnowMelting.class.getClassLoader());
                    worldTemperatureMethod = type.getMethod("getWorldTemperature", Level.class, BlockPos.class);
                } catch (ReflectiveOperationException failure) {
                    worldTemperatureMethod = null;
                    throw failure;
                } finally {
                    worldTemperatureMethodResolved = true;
                }
            }
        }
        if (worldTemperatureMethod == null) throw new ReflectiveOperationException("LSO temperature API unavailable");
        return worldTemperatureMethod;
    }
}
