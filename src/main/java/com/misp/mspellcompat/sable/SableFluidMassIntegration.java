package com.misp.mspellcompat.sable;

import com.misp.mspellcompat.util.MSpellCompatDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

@EventBusSubscriber(modid = "mspellcompat")
public final class SableFluidMassIntegration {

    private static final String SABLE_CONTAINER_CLASS =
            "dev.ryanhcode.sable.api.sublevel.SubLevelContainer";

    private static final Map<MinecraftServer, Map<String, TrackerState>> STATES = new WeakHashMap<>();
    private static final Map<Class<?>, Method> ADD_BLOCK_MASS_METHODS = new HashMap<>();
    private static final Map<NoArgMethodKey, Method> NO_ARG_METHODS = new ConcurrentHashMap<>();
    private static volatile Method GET_CONTAINER_METHOD;

    private SableFluidMassIntegration() {
    }

    @SubscribeEvent
    public static void onServerTickPre(ServerTickEvent.Pre event) {
        if (!SableFluidRuntimeConfig.MASS_INTEGRATION_ENABLED.get() || !ModList.get().isLoaded("sable")) return;

        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();
        long gameTime = overworld.getGameTime();
        int scanInterval = SableFluidRuntimeConfig.MASS_SCAN_INTERVAL_TICKS.get();
        int scanPhase = scanInterval / 2;
        if (Math.floorMod(gameTime, scanInterval) != scanPhase) return;
        if (MSpellCompatDebug.isDebugLoggingEnabled()) {
            MSpellCompatDebug.log("sable_mass", "scan_begin", "gameTime=" + gameTime
                    + ",interval=" + scanInterval);
        }

        Set<String> activeKeys = new HashSet<>();
        boolean enumerationComplete = true;
        Map<String, TrackerState> serverStates;
        synchronized (STATES) {
            serverStates = STATES.computeIfAbsent(server, ignored -> new HashMap<>());
        }

        try {
            Method getContainer = getContainerMethod();

            for (ServerLevel level : server.getAllLevels()) {
                Object container = getContainer.invoke(null, level);
                if (container == null) continue;
                Object rawSublevels = invoke(container, "getAllSubLevels");
                if (!(rawSublevels instanceof Iterable<?> sublevels)) continue;

                for (Object sublevel : sublevels) {
                    if (Boolean.TRUE.equals(invoke(sublevel, "isRemoved"))) {
                        if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                            MSpellCompatDebug.log("sable_mass", "sublevel_skip", "reason=removed,dimension=" + level.dimension().location());
                        }
                        continue;
                    }
                    String id = String.valueOf(invoke(sublevel, "getUniqueId"));
                    String key = level.dimension().location() + "|" + id;
                    activeKeys.add(key);
                    if (!SableSublevelActivity.hasNearbyPlayer(level, sublevel)) continue;
                    if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                        MSpellCompatDebug.log("sable_mass", "sublevel_found", "key=" + key);
                    }
                    processSublevel(level, sublevel, key, gameTime, serverStates);
                }
            }
        } catch (Throwable failure) {
            enumerationComplete = false;
            if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                MSpellCompatDebug.error("sable_mass", "enumeration_failed", failure,
                        "gameTime=" + gameTime + ",activeKeys=" + activeKeys.size());
            }
        }
        if (MSpellCompatDebug.isDebugLoggingEnabled()) {
            MSpellCompatDebug.log("sable_mass", "scan_end", "gameTime=" + gameTime
                    + ",enumerationComplete=" + enumerationComplete + ",activeSublevels=" + activeKeys.size()
                    + ",trackedSublevels=" + serverStates.size());
        }

        if (enumerationComplete) {
            serverStates.keySet().removeIf(key -> !activeKeys.contains(key));
        }
    }

    private static void processSublevel(ServerLevel world, Object sublevel, String key, long gameTime,
                                        Map<String, TrackerState> states) {
        try {
            Object plot = invoke(sublevel, "getPlot");
            Object rawBounds = invoke(plot, "getBoundingBox");
            int minX = readInt(rawBounds, "minX");
            int minY = readInt(rawBounds, "minY");
            int minZ = readInt(rawBounds, "minZ");
            int maxX = readInt(rawBounds, "maxX");
            int maxY = readInt(rawBounds, "maxY");
            int maxZ = readInt(rawBounds, "maxZ");

            long width = (long) maxX - minX + 1L;
            long height = (long) maxY - minY + 1L;
            long depth = (long) maxZ - minZ + 1L;
            long maxScanCells = SableFluidRuntimeConfig.MASS_MAX_SCAN_CELLS.get();
            if (width <= 0 || height <= 0 || depth <= 0
                    || width > maxScanCells || height > maxScanCells || depth > maxScanCells
                    || width * height > maxScanCells
                    || width * height * depth > maxScanCells) {
                if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                    MSpellCompatDebug.log("sable_mass", "sublevel_skip", "key=" + key + ",reason=scan_bounds_limit"
                            + ",bounds=" + minX + "," + minY + "," + minZ + ".." + maxX + "," + maxY + "," + maxZ
                            + ",volume=" + (width * height * depth));
                }
                return;
            }

            BlockPos center = (BlockPos) invoke(plot, "getCenterBlock");
            Object rawAccessor = invoke(plot, "getEmbeddedLevelAccessor");
            if (!(rawAccessor instanceof LevelAccessor accessor)) {
                if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                    MSpellCompatDebug.log("sable_mass", "sublevel_skip", "key=" + key + ",reason=no_level_accessor,type="
                            + (rawAccessor == null ? "null" : rawAccessor.getClass().getName()));
                }
                return;
            }

            StorageBounds storage = new StorageBounds(minX, minY, minZ, maxX, maxY, maxZ);
            if (!storageChunksAlreadyLoaded(world, storage)) {
                if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                    MSpellCompatDebug.log("sable_mass", "sublevel_skip", "key=" + key + ",reason=storage_chunks_not_loaded"
                            + ",bounds=" + storage);
                }
                return;
            }

            LocalBounds localBounds = new LocalBounds(
                    minX - center.getX(), minY - center.getY(), minZ - center.getZ(),
                    maxX - center.getX(), maxY - center.getY(), maxZ - center.getZ());

            Object selfMassTracker = invoke(sublevel, "getSelfMassTracker");
            if (selfMassTracker == null) {
                if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                    MSpellCompatDebug.log("sable_mass", "sublevel_skip", "key=" + key + ",reason=no_mass_tracker");
                }
                return;
            }

            TrackerState state = states.get(key);
            boolean trackerChanged = state == null || state.massTracker != selfMassTracker;
            if (trackerChanged) {
                state = new TrackerState(selfMassTracker);
                states.put(key, state);
            }

            if (trackerChanged || state.hullBounds == null
                    || gameTime - state.lastHullScanTick >= SableFluidRuntimeConfig.MASS_HULL_REFRESH_TICKS.get()) {
                state.hullBounds = deriveHullBounds(accessor, localBounds);
                state.lastHullScanTick = gameTime;
            }

            if (state.hullBounds == null) {
                if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                    MSpellCompatDebug.log("sable_mass", "hull_missing", "key=" + key + ",storage=" + storage);
                }
                removePreviousWaterMassIfSafe(key, state, accessor, center, selfMassTracker);
                return;
            }

            Map<CellPos, Integer> currentWaterUnits = readWaterUnits(accessor, state.hullBounds);
            int currentTotalUnits = currentWaterUnits.values().stream().mapToInt(Integer::intValue).sum();
            int previousTotalUnits = state.totalWaterUnits;
            double massBefore = readMass(selfMassTracker);
            double previousWaterMass = previousTotalUnits / 8.0 * SableFluidRuntimeConfig.MASS_WATER_MASS_PER_FULL_BLOCK.get();
            double structuralMass = massBefore - previousWaterMass;
            if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                MSpellCompatDebug.log("sable_mass", "snapshot", "key=" + key + ",gameTime=" + gameTime
                        + ",waterCells=" + currentWaterUnits.size() + ",currentWaterUnits=" + currentTotalUnits
                        + ",previousWaterUnits=" + previousTotalUnits + ",massBefore=" + massBefore
                        + ",previousWaterMass=" + previousWaterMass + ",structuralMass=" + structuralMass
                        + ",hull=" + state.hullBounds);
            }
            if (!Double.isFinite(structuralMass) || structuralMass <= 1.0e-6) {
                if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                    MSpellCompatDebug.log("sable_mass", "sublevel_skip", "key=" + key + ",reason=invalid_structural_mass"
                            + ",structuralMass=" + structuralMass);
                }
                return;
            }
            List<AppliedDelta> applied = new ArrayList<>();
            Method addBlockMass = addBlockMassMethod(selfMassTracker.getClass());
            BlockState waterBlock = Blocks.WATER.defaultBlockState();

            Set<CellPos> changedPositions = new HashSet<>(state.waterUnits.keySet());
            changedPositions.addAll(currentWaterUnits.keySet());
            try {
                for (CellPos localCell : changedPositions) {
                    int beforeUnits = state.waterUnits.getOrDefault(localCell, 0);
                    int afterUnits = currentWaterUnits.getOrDefault(localCell, 0);
                    int deltaUnits = afterUnits - beforeUnits;
                    if (deltaUnits == 0) continue;

                    double deltaMass = (deltaUnits / 8.0) * SableFluidRuntimeConfig.MASS_WATER_MASS_PER_FULL_BLOCK.get();

                    BlockPos plotPos = center.offset(localCell.x(), localCell.y(), localCell.z());
                    addBlockMass.invoke(selfMassTracker, accessor, waterBlock, plotPos, deltaMass, null);
                    applied.add(new AppliedDelta(plotPos, deltaMass));
                    if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                        MSpellCompatDebug.log("sable_mass", "cell_mass_delta", "key=" + key + ",cell=" + localCell
                                + ",plotPos=" + plotPos + ",beforeUnits=" + beforeUnits + ",afterUnits=" + afterUnits
                                + ",deltaUnits=" + deltaUnits + ",deltaMass=" + deltaMass);
                    }
                }
            } catch (Throwable applyFailure) {

                for (int i = applied.size() - 1; i >= 0; i--) {
                    AppliedDelta delta = applied.get(i);
                    try {
                        addBlockMass.invoke(selfMassTracker, accessor, waterBlock,
                                delta.plotPos(), -delta.massDelta(), null);
                    } catch (Throwable rollbackFailure) {
                        applyFailure.addSuppressed(rollbackFailure);
                        if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                            MSpellCompatDebug.error("sable_mass", "mass_delta_rollback_failed", rollbackFailure,
                                    "key=" + key + ",plotPos=" + delta.plotPos() + ",massDelta=" + delta.massDelta());
                        }
                    }
                }
                if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                    MSpellCompatDebug.error("sable_mass", "mass_delta_apply_failed", applyFailure,
                            "key=" + key + ",appliedDeltas=" + applied.size() + ",currentWaterUnits=" + currentTotalUnits);
                }
                return;
            }

            state.waterUnits.clear();
            state.waterUnits.putAll(currentWaterUnits);
            state.totalWaterUnits = currentTotalUnits;
            MSpellCompatDebug.log("sable_mass", "sublevel_end", () -> "key=" + key + ",totalWaterUnits="
                    + currentTotalUnits + ",waterCells=" + currentWaterUnits.size()
                    + ",massAfter=" + readMassForLog(selfMassTracker));

        } catch (Throwable failure) {
            if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                MSpellCompatDebug.error("sable_mass", "sublevel_failed", failure, "key=" + key + ",gameTime=" + gameTime);
            }
        }
    }

    private static void removePreviousWaterMassIfSafe(String key, TrackerState state, LevelAccessor accessor,
                                                       BlockPos center, Object tracker) {
        if (state.waterUnits.isEmpty()) return;
        try {
            double massBefore = readMass(tracker);
            double trackedWaterMass = state.totalWaterUnits / 8.0 * SableFluidRuntimeConfig.MASS_WATER_MASS_PER_FULL_BLOCK.get();
            if (!Double.isFinite(massBefore) || massBefore - trackedWaterMass <= 1.0e-6) {
                return;
            }

            Method addBlockMass = addBlockMassMethod(tracker.getClass());
            BlockState waterBlock = Blocks.WATER.defaultBlockState();
            List<AppliedDelta> applied = new ArrayList<>();
            try {
                for (Map.Entry<CellPos, Integer> entry : state.waterUnits.entrySet()) {
                    double mass = entry.getValue() / 8.0 * SableFluidRuntimeConfig.MASS_WATER_MASS_PER_FULL_BLOCK.get();
                    if (mass <= 0.0) continue;
                    CellPos cell = entry.getKey();
                    BlockPos plotPos = center.offset(cell.x(), cell.y(), cell.z());
                    addBlockMass.invoke(tracker, accessor, waterBlock, plotPos, -mass, null);
                    applied.add(new AppliedDelta(plotPos, -mass));
                }
            } catch (Throwable applyFailure) {
                for (int i = applied.size() - 1; i >= 0; i--) {
                    AppliedDelta delta = applied.get(i);
                    try {
                        addBlockMass.invoke(tracker, accessor, waterBlock,
                                delta.plotPos(), -delta.massDelta(), null);
                    } catch (Throwable rollbackFailure) {
                        applyFailure.addSuppressed(rollbackFailure);
                        if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                            MSpellCompatDebug.error("sable_mass", "remove_mass_rollback_failed", rollbackFailure,
                                    "key=" + key + ",plotPos=" + delta.plotPos() + ",massDelta=" + delta.massDelta());
                        }
                    }
                }
                throw applyFailure;
            }
            state.waterUnits.clear();
            state.totalWaterUnits = 0;
            if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                MSpellCompatDebug.log("sable_mass", "previous_mass_removed", "key=" + key
                        + ",removedUnits=" + (int) Math.round(trackedWaterMass * 8.0)
                        + ",removedMass=" + trackedWaterMass);
            }
        } catch (Throwable failure) {
            if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                MSpellCompatDebug.error("sable_mass", "remove_previous_mass_failed", failure, "key=" + key);
            }
        }
    }

    private static LocalBounds deriveHullBounds(LevelAccessor accessor, LocalBounds storage) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int y = storage.minY(); y <= storage.maxY(); y++) {
            for (int z = storage.minZ(); z <= storage.maxZ(); z++) {
                for (int x = storage.minX(); x <= storage.maxX(); x++) {
                    pos.set(x, y, z);
                    BlockState block = accessor.getBlockState(pos);
                    FluidState fluid = accessor.getFluidState(pos);
                    if (isPassable(block, fluid)) continue;
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    minZ = Math.min(minZ, z);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                    maxZ = Math.max(maxZ, z);
                }
            }
        }
        return minX == Integer.MAX_VALUE ? null
                : new LocalBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static Map<CellPos, Integer> readWaterUnits(LevelAccessor accessor, LocalBounds hull) {
        Map<CellPos, Integer> result = new HashMap<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = hull.minY(); y <= hull.maxY(); y++) {
            for (int z = hull.minZ(); z <= hull.maxZ(); z++) {
                for (int x = hull.minX(); x <= hull.maxX(); x++) {
                    pos.set(x, y, z);
                    BlockState block = accessor.getBlockState(pos);
                    if (!block.is(Blocks.WATER)) continue;
                    FluidState fluid = accessor.getFluidState(pos);
                    if (fluid.isEmpty() || !isVanillaWater(fluid.getType())) continue;
                    int amount = Math.clamp(fluid.getAmount(), 0, 8);
                    if (amount > 0) result.put(new CellPos(x, y, z), amount);
                }
            }
        }
        return result;
    }

    private static boolean isPassable(BlockState block, FluidState fluid) {
        return (block.isAir() && fluid.isEmpty())
                || (block.is(Blocks.WATER) && !fluid.isEmpty() && isVanillaWater(fluid.getType()));
    }

    private static boolean isVanillaWater(net.minecraft.world.level.material.Fluid fluid) {
        return fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER;
    }

    private static boolean storageChunksAlreadyLoaded(ServerLevel world, StorageBounds bounds) {
        for (int chunkX = bounds.minX() >> 4; chunkX <= (bounds.maxX() >> 4); chunkX++) {
            for (int chunkZ = bounds.minZ() >> 4; chunkZ <= (bounds.maxZ() >> 4); chunkZ++) {
                if (!world.hasChunk(chunkX, chunkZ)) return false;
            }
        }
        return true;
    }

    private static Method addBlockMassMethod(Class<?> trackerClass) throws NoSuchMethodException {
        synchronized (ADD_BLOCK_MASS_METHODS) {
            Method cached = ADD_BLOCK_MASS_METHODS.get(trackerClass);
            if (cached != null) return cached;
            Method method = trackerClass.getMethod("addBlockMass", BlockGetter.class, BlockState.class,
                    BlockPos.class, double.class, Vec3.class);
            ADD_BLOCK_MASS_METHODS.put(trackerClass, method);
            return method;
        }
    }

    private static Method getContainerMethod() throws ReflectiveOperationException {
        Method cached = GET_CONTAINER_METHOD;
        if (cached != null) return cached;
        Class<?> containerClass = Class.forName(SABLE_CONTAINER_CLASS, true,
                SableFluidMassIntegration.class.getClassLoader());
        Method resolved = containerClass.getMethod("getContainer", ServerLevel.class);
        GET_CONTAINER_METHOD = resolved;
        return resolved;
    }

    private static Object invoke(Object target, String methodName) throws ReflectiveOperationException {
        if (target == null) throw new ReflectiveOperationException("null target for " + methodName + "()");
        Class<?> owner = target.getClass();
        NoArgMethodKey key = new NoArgMethodKey(owner, methodName);
        Method method = NO_ARG_METHODS.get(key);
        if (method == null) {
            Method resolved = owner.getMethod(methodName);
            Method previous = NO_ARG_METHODS.putIfAbsent(key, resolved);
            method = previous == null ? resolved : previous;
        }
        return method.invoke(target);
    }

    private static int readInt(Object target, String methodName) throws ReflectiveOperationException {
        Object value = invoke(target, methodName);
        if (value instanceof Number number) return number.intValue();
        throw new ReflectiveOperationException(methodName + "() did not return a number");
    }

    private static double readMass(Object target) throws ReflectiveOperationException {
        Object value = invoke(target, "getMass");
        if (value instanceof Number number) return number.doubleValue();
        throw new ReflectiveOperationException("getMass() did not return a number");
    }

    private static String readMassForLog(Object target) {
        try {
            return Double.toString(readMass(target));
        } catch (ReflectiveOperationException failure) {
            if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                MSpellCompatDebug.error("sable_mass", "diagnostic_read_failed", failure, "method=getMass");
            }
            return "unavailable";
        }
    }

    private record NoArgMethodKey(Class<?> owner, String name) {
    }

    private record CellPos(int x, int y, int z) {
    }

    private record AppliedDelta(BlockPos plotPos, double massDelta) {
    }

    private record StorageBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    }

    private record LocalBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        @Override
        public @NotNull String toString() {
            return "LocalBounds[min=" + minX + "," + minY + "," + minZ
                    + "; max=" + maxX + "," + maxY + "," + maxZ + "]";
        }
    }

    private static final class TrackerState {
        private final Object massTracker;
        private LocalBounds hullBounds;
        private long lastHullScanTick = Long.MIN_VALUE / 2;
        private final Map<CellPos, Integer> waterUnits = new HashMap<>();
        private int totalWaterUnits;

        private TrackerState(Object massTracker) {
            this.massTracker = massTracker;
        }
    }
}
