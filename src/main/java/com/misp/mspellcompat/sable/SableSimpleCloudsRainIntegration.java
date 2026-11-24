package com.misp.mspellcompat.sable;

import com.misp.mspellcompat.config.MSpellCompatConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import com.misp.mspellcompat.weather.SurfacePrecipitation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import traben.flowing_fluids.FFFluidUtils;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("resource")
public final class SableSimpleCloudsRainIntegration {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SABLE_CONTAINER_CLASS =
            "dev.ryanhcode.sable.api.sublevel.SubLevelContainer";
    private static final int MAX_ATTEMPTS_PER_SUBLEVEL_PER_SCAN = 4;
    private static final double WORLD_CHUNK_SAMPLE_AREA = 256.0;
    private static final long MAX_SUBLEVEL_DIMENSION = 4096L;

    private static final Map<Class<?>, Method> CONTAINER_METHODS = new ConcurrentHashMap<>();
    private static final Map<NoArgMethodKey, Method> NO_ARG_METHODS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Method> POSE_TRANSFORM_METHODS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Method> POSE_INVERSE_NORMAL_METHODS = new ConcurrentHashMap<>();

    private SableSimpleCloudsRainIntegration() {
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (!MSpellCompatConfig.FLOWING_FLUIDS_CREATE_SURFACE_WATER.get()
                || !ModList.get().isLoaded("sable")
                || !ModList.get().isLoaded("flowing_fluids")
                || !ModList.get().isLoaded("simpleclouds")) {
            return;
        }

        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();
        if (overworld == null) return;

        int interval = SableFluidRuntimeConfig.SABLE_RAIN_SCAN_INTERVAL_TICKS.get();
        if (Math.floorMod(overworld.getGameTime(), interval) != 0) return;

        ScanStats stats = new ScanStats();
        for (ServerLevel level : server.getAllLevels()) {
            processLevel(level, interval, stats);
        }

        if (SableFluidRuntimeConfig.SABLE_RAIN_LOG_SCAN_SUMMARY.get()) {
            LOGGER.info("[MSpellCompat][sable_simpleclouds_rain] scan_summary | {}", stats);
        }
    }

    private static void processLevel(ServerLevel level, int scanInterval, ScanStats stats) {
        stats.levels++;
        try {
            Class<?> containerClass = Class.forName(
                    SABLE_CONTAINER_CLASS, true, SableSimpleCloudsRainIntegration.class.getClassLoader());
            Object container = containerMethod(containerClass).invoke(null, level);
            if (container == null) return;

            Object result = invoke(container, "getAllSubLevels");
            if (!(result instanceof Collection<?> sublevels)) return;

            stats.sublevelsFound += sublevels.size();
            for (Object sublevel : sublevels) {
                try {
                    if (Boolean.TRUE.equals(invoke(sublevel, "isRemoved"))) {
                        stats.removedSublevels++;
                        continue;
                    }
                    if (!SableSublevelActivity.hasNearbyPlayer(level, sublevel)) {
                        stats.inactiveSublevels++;
                        continue;
                    }
                    stats.sublevelsProcessed++;
                    processSublevel(level, sublevel, scanInterval, stats);
                } catch (Throwable failure) {
                    stats.failures++;
                    stats.lastFailure = failure.getClass().getSimpleName();
                }
            }
        } catch (Throwable failure) {
            stats.failures++;
            stats.lastFailure = failure.getClass().getSimpleName();
        }
    }

    private static void processSublevel(ServerLevel level, Object sublevel, int scanInterval, ScanStats stats)
            throws ReflectiveOperationException {
        Object plot = invoke(sublevel, "getPlot");
        Object rawBounds = invoke(plot, "getBoundingBox");
        int minX = readInt(rawBounds, "minX");
        int minY = readInt(rawBounds, "minY");
        int minZ = readInt(rawBounds, "minZ");
        int maxX = readInt(rawBounds, "maxX");
        int maxY = readInt(rawBounds, "maxY");
        int maxZ = readInt(rawBounds, "maxZ");

        BlockPos center = (BlockPos) invoke(plot, "getCenterBlock");
        Object rawAccessor = invoke(plot, "getEmbeddedLevelAccessor");
        if (!(rawAccessor instanceof LevelAccessor accessor)) {
            stats.noAccessor++;
            return;
        }
        Object pose = invoke(sublevel, "logicalPose");

        LocalBounds bounds = new LocalBounds(
                minX - center.getX(), minY - center.getY(), minZ - center.getZ(),
                maxX - center.getX(), maxY - center.getY(), maxZ - center.getZ());
        long sizeX = bounds.sizeX();
        long sizeY = bounds.sizeY();
        long sizeZ = bounds.sizeZ();
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0
                || sizeX > MAX_SUBLEVEL_DIMENSION
                || sizeY > MAX_SUBLEVEL_DIMENSION
                || sizeZ > MAX_SUBLEVEL_DIMENSION) {
            stats.invalidBounds++;
            return;
        }

        Vec3 localUpVector = invokePoseVector(
                pose, new Vec3(0.0, 1.0, 0.0), "transformNormalInverse", POSE_INVERSE_NORMAL_METHODS);
        Direction localUp = nearestDirection(localUpVector);
        if (localUp == null) {
            stats.invalidPose++;
            return;
        }

        long surfaceArea = switch (localUp.getAxis()) {
            case X -> sizeY * sizeZ;
            case Y -> sizeX * sizeZ;
            case Z -> sizeX * sizeY;
        };
        int denominator = MSpellCompatConfig.FLOWING_FLUIDS_SURFACE_WATER_CHANCE_DENOMINATOR.get();
        int amount = MSpellCompatConfig.FLOWING_FLUIDS_SURFACE_WATER_UNITS.get();

        // Match vanilla-surface rain density: one randomized candidate per 16x16 chunk per tick,
        // scaled to this sublevel's exposed surface area and the slower Sable scan interval.
        double expectedAttempts = surfaceArea * (double) scanInterval
                / (WORLD_CHUNK_SAMPLE_AREA * denominator);
        int attempts;
        if (expectedAttempts >= MAX_ATTEMPTS_PER_SUBLEVEL_PER_SCAN) {
            attempts = MAX_ATTEMPTS_PER_SUBLEVEL_PER_SCAN;
        } else {
            attempts = (int) Math.floor(expectedAttempts);
            if (level.getRandom().nextDouble() < expectedAttempts - attempts) {
                attempts++;
            }
        }
        if (attempts <= 0) {
            stats.rateSkipped++;
            return;
        }
        stats.plannedAttempts += attempts;

        Set<Long> sampledColumns = new HashSet<>();
        for (int attempt = 0; attempt < attempts; attempt++) {
            stats.placementAttempts++;
            if (tryCreatePrecipitationLayer(level, accessor, pose, center, bounds, localUp, amount,
                    sampledColumns, stats)) {
                stats.placedThisScan++;
            }
        }
    }

    private static boolean tryCreatePrecipitationLayer(ServerLevel level, LevelAccessor accessor, Object pose,
                                               BlockPos center, LocalBounds bounds,
                                               Direction localUp, int amount, Set<Long> sampledColumns,
                                               ScanStats stats) {
        int minA;
        int maxA;
        int minB;
        int maxB;
        switch (localUp.getAxis()) {
            case X -> {
                minA = bounds.minY(); maxA = bounds.maxY();
                minB = bounds.minZ(); maxB = bounds.maxZ();
            }
            case Y -> {
                minA = bounds.minX(); maxA = bounds.maxX();
                minB = bounds.minZ(); maxB = bounds.maxZ();
            }
            case Z -> {
                minA = bounds.minX(); maxA = bounds.maxX();
                minB = bounds.minY(); maxB = bounds.maxY();
            }
            default -> {
                stats.invalidPose++;
                return false;
            }
        }

        int a = 0;
        int b = 0;
        boolean selectedColumn = false;
        for (int selection = 0; selection < 16; selection++) {
            a = randomBetween(level, minA, maxA);
            b = randomBetween(level, minB, maxB);
            long columnKey = ((long) a << 32) ^ (b & 0xFFFFFFFFL);
            if (sampledColumns.add(columnKey)) {
                selectedColumn = true;
                break;
            }
        }
        if (!selectedColumn) return false;
        stats.surfaceSamples++;

        int minAlong = minAlong(bounds, localUp.getAxis());
        int maxAlong = maxAlong(bounds, localUp.getAxis());
        boolean positive = localUp.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        int lineLength = maxAlong - minAlong + 1;

        for (int step = 0; step < lineLength; step++) {
            int along = positive ? maxAlong - step : minAlong + step;
            BlockPos supportPos = composePos(localUp.getAxis(), along, a, b);
            BlockPos storageSupport = center.offset(supportPos.getX(), supportPos.getY(), supportPos.getZ());
            if (!level.hasChunk(storageSupport.getX() >> 4, storageSupport.getZ() >> 4)) {
                stats.storageChunkMissing++;
                continue;
            }

            BlockState supportState = accessor.getBlockState(supportPos);
            if (!supportState.isFaceSturdy(accessor, supportPos, localUp)) {
                stats.unsupportedCells++;
                continue;
            }

            BlockPos waterPos = supportPos.relative(localUp);
            BlockPos storageWater = center.offset(waterPos.getX(), waterPos.getY(), waterPos.getZ());
            if (!level.hasChunk(storageWater.getX() >> 4, storageWater.getZ() >> 4)) {
                stats.storageChunkMissing++;
                continue;
            }

            BlockState targetState = accessor.getBlockState(waterPos);
            Vec3 transformed = invokePoseVector(
                    pose,
                    new Vec3(center.getX() + waterPos.getX() + 0.5,
                            center.getY() + waterPos.getY() + 0.5,
                            center.getZ() + waterPos.getZ() + 0.5),
                    "transformPosition", POSE_TRANSFORM_METHODS);
            if (transformed == null) {
                stats.transformFailures++;
                return false;
            }

            BlockPos worldPos = BlockPos.containing(transformed.x, transformed.y, transformed.z);
            if (worldPos.getY() < level.getMinBuildHeight() || worldPos.getY() >= level.getMaxBuildHeight()) {
                stats.worldHeightRejected++;
                continue;
            }
            if (!level.hasChunk(worldPos.getX() >> 4, worldPos.getZ() >> 4)) {
                stats.worldChunkMissing++;
                continue;
            }
            if (!level.canSeeSky(worldPos)) {
                stats.skyBlocked++;
                continue;
            }
            stats.weatherChecks++;
            Biome.Precipitation precipitation = SurfacePrecipitation.getAt(level, worldPos);
            if (precipitation == Biome.Precipitation.RAIN) {
                if (!targetState.isAir() || !accessor.getFluidState(waterPos).isEmpty()) {
                    stats.targetBlocked++;
                    continue;
                }

                FFFluidUtils.setFluidStateAtPosToNewAmount(accessor, waterPos, Fluids.WATER, amount);
                if (accessor.getFluidState(waterPos).isEmpty()) {
                    stats.writeFailures++;
                    continue;
                }
                stats.placed++;
                return true;
            }

            if (precipitation == Biome.Precipitation.SNOW) {
                // Vanilla snow layers are gravity-aligned; do not place them on rotated side/underside faces.
                if (localUp != Direction.UP) {
                    stats.unsupportedCells++;
                    continue;
                }
                if (SurfacePrecipitation.addSnowLayer(accessor, waterPos, supportPos)) {
                    stats.placed++;
                    return true;
                }
                stats.targetBlocked++;
                continue;
            }

            stats.noLocalPrecipitation++;
        }
        return false;
    }

    private static int randomBetween(ServerLevel level, int min, int max) {
        long length = (long) max - min + 1L;
        if (length <= 1L) return min;
        return min + level.getRandom().nextInt((int) length);
    }

    private static int minAlong(LocalBounds bounds, Direction.Axis axis) {
        return switch (axis) {
            case X -> bounds.minX();
            case Y -> bounds.minY();
            case Z -> bounds.minZ();
        };
    }

    private static int maxAlong(LocalBounds bounds, Direction.Axis axis) {
        return switch (axis) {
            case X -> bounds.maxX();
            case Y -> bounds.maxY();
            case Z -> bounds.maxZ();
        };
    }

    private static BlockPos composePos(Direction.Axis axis, int along, int a, int b) {
        return switch (axis) {
            case X -> new BlockPos(along, a, b);
            case Y -> new BlockPos(a, along, b);
            case Z -> new BlockPos(a, b, along);
        };
    }

    private static Direction nearestDirection(Vec3 vector) {
        if (vector == null || vector.lengthSqr() < 1.0e-9) return null;
        double x = Math.abs(vector.x);
        double y = Math.abs(vector.y);
        double z = Math.abs(vector.z);
        if (x >= y && x >= z) return vector.x >= 0.0 ? Direction.EAST : Direction.WEST;
        if (y >= x && y >= z) return vector.y >= 0.0 ? Direction.UP : Direction.DOWN;
        return vector.z >= 0.0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static Method containerMethod(Class<?> owner) throws NoSuchMethodException {
        Method cached = CONTAINER_METHODS.get(owner);
        if (cached != null) return cached;
        Method found = owner.getMethod("getContainer", ServerLevel.class);
        Method prior = CONTAINER_METHODS.putIfAbsent(owner, found);
        return prior == null ? found : prior;
    }

    private static Object invoke(Object target, String methodName) throws ReflectiveOperationException {
        if (target == null) throw new ReflectiveOperationException("Null target for " + methodName + "()");
        NoArgMethodKey key = new NoArgMethodKey(target.getClass(), methodName);
        Method method = NO_ARG_METHODS.get(key);
        if (method == null) {
            Method found = target.getClass().getMethod(methodName);
            Method prior = NO_ARG_METHODS.putIfAbsent(key, found);
            method = prior == null ? found : prior;
        }
        return method.invoke(target);
    }

    private static int readInt(Object target, String methodName) throws ReflectiveOperationException {
        Object value = invoke(target, methodName);
        if (value instanceof Number number) return number.intValue();
        throw new ReflectiveOperationException(methodName + "() did not return a number");
    }

    private static Vec3 invokePoseVector(Object pose, Vec3 vector, String methodName,
                                         Map<Class<?>, Method> cache) {
        if (pose == null) return null;
        try {
            Class<?> type = pose.getClass();
            Method method = cache.get(type);
            if (method == null) {
                Method found = type.getMethod(methodName, Vec3.class);
                Method prior = cache.putIfAbsent(type, found);
                method = prior == null ? found : prior;
            }
            Object value = method.invoke(pose, vector);
            return value instanceof Vec3 result ? result : null;
        } catch (Throwable failure) {
            return null;
        }
    }

    private record NoArgMethodKey(Class<?> owner, String name) {
    }

    private record LocalBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        private long sizeX() { return (long) maxX - minX + 1L; }
        private long sizeY() { return (long) maxY - minY + 1L; }
        private long sizeZ() { return (long) maxZ - minZ + 1L; }
    }

    private static final class ScanStats {
        private int levels;
        private int sublevelsFound;
        private int sublevelsProcessed;
        private int removedSublevels;
        private int inactiveSublevels;
        private int plannedAttempts;
        private int placementAttempts;
        private int rateSkipped;
        private int surfaceSamples;
        private int placedThisScan;
        private int noAccessor;
        private int invalidBounds;
        private int invalidPose;
        private int storageChunkMissing;
        private int unsupportedCells;
        private int targetBlocked;
        private int transformFailures;
        private int worldHeightRejected;
        private int worldChunkMissing;
        private int skyBlocked;
        private int weatherChecks;
        private int noLocalPrecipitation;
        private int writeFailures;
        private int placed;
        private int failures;
        private String lastFailure = "none";

        @Override
        public String toString() {
            return "levels=" + levels
                    + ",sublevelsFound=" + sublevelsFound
                    + ",sublevelsProcessed=" + sublevelsProcessed
                    + ",removed=" + removedSublevels
                    + ",inactiveNoNearbyPlayer=" + inactiveSublevels
                    + ",plannedAttempts=" + plannedAttempts
                    + ",placementAttempts=" + placementAttempts
                    + ",rateSkipped=" + rateSkipped
                    + ",surfaceSamples=" + surfaceSamples
                    + ",placedThisScan=" + placedThisScan
                    + ",noAccessor=" + noAccessor
                    + ",invalidBounds=" + invalidBounds
                    + ",invalidPose=" + invalidPose
                    + ",storageChunkMissing=" + storageChunkMissing
                    + ",unsupportedCells=" + unsupportedCells
                    + ",targetBlocked=" + targetBlocked
                    + ",transformFailures=" + transformFailures
                    + ",worldHeightRejected=" + worldHeightRejected
                    + ",worldChunkMissing=" + worldChunkMissing
                    + ",skyBlocked=" + skyBlocked
                    + ",weatherChecks=" + weatherChecks
                    + ",noLocalPrecipitation=" + noLocalPrecipitation
                    + ",writeFailures=" + writeFailures
                    + ",placed=" + placed
                    + ",failures=" + failures
                    + ",lastFailure=" + lastFailure;
        }
    }
}
