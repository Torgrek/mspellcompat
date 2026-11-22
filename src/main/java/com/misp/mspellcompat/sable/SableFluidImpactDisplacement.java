package com.misp.mspellcompat.sable;

import com.misp.mspellcompat.config.SableFluidPhysicsConfig;
import com.misp.mspellcompat.util.MSpellCompatDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class SableFluidImpactDisplacement {
    private static final String SABLE_CONTAINER_CLASS =
            "dev.ryanhcode.sable.api.sublevel.SubLevelContainer";
    private static final String FF_API_CLASS = "traben.flowing_fluids.api.FlowingFluidsAPI";
    private static final String CLIENT_ID = "mspellcompat_sable_impact_displacement";

    private static final long MAX_WORLD_SCAN_CELLS = 32_768L;
    private static final int MAX_WATERLINE_SEARCH_STEPS = 48;
    private static final double[] CELL_SAMPLE_OFFSETS = {0.2, 0.5, 0.8};

    private static final Map<MinecraftServer, RuntimeState> RUNTIME = new WeakHashMap<>();
    private static final Set<String> WARNED = new HashSet<>();
    private static volatile FluidAccess cachedFluidAccess;
    private static volatile boolean fluidLookupAttempted;
    private static volatile boolean activationLogged;

    private SableFluidImpactDisplacement() {
    }

    public static void onServerTickPre(ServerTickEvent.Pre event) {
        if (!SableFluidPhysicsConfig.IMPACT_DISPLACEMENT_ENABLED.get()
                || !ModList.get().isLoaded("sable")
                || !ModList.get().isLoaded("flowing_fluids")) {
            return;
        }

        MinecraftServer server = event.getServer();
        if (server.overworld() == null) return;
        long gameTime = server.overworld().getGameTime();
        RuntimeState runtime = runtimeFor(server, gameTime);
        FluidAccess fluids = fluidAccess();
        if (fluids == null || !fluids.isEnabled() || !fluids.modifies(Fluids.WATER)
                && !fluids.modifies(Fluids.FLOWING_WATER)) {
            warnOnce("flowing-fluids-unavailable",
                    "Impact displacement is enabled, but Flowing Fluids does not currently manage vanilla water; impact processing is paused.");
            return;
        }

        if (!activationLogged) {
            MSpellCompatDebug.debug("[SableFluidImpactDisplacement][STAGE2.11.3-IMPACT-DISPLACEMENT-WATERLINE-SPLASH] active=true minMass={} minSpeed={} radius={} maxSourceBlocks={} splashUnitsPerBlock={} splashUnitsPerTick={} cooldownTicks={} maxEventsPerTick={}",
                    SableFluidPhysicsConfig.IMPACT_MINIMUM_MASS.get(),
                    SableFluidPhysicsConfig.IMPACT_MINIMUM_SPEED.get(),
                    SableFluidPhysicsConfig.IMPACT_SPLASH_RADIUS.get(),
                    SableFluidPhysicsConfig.IMPACT_MAX_SOURCE_BLOCKS_PER_EVENT.get(),
                    SableFluidPhysicsConfig.IMPACT_SPLASH_UNITS_PER_BLOCK.get(),
                    SableFluidPhysicsConfig.IMPACT_MAX_SPLASH_UNITS_PER_TICK.get(),
                    SableFluidPhysicsConfig.IMPACT_COOLDOWN_TICKS.get(),
                    SableFluidPhysicsConfig.IMPACT_MAX_EVENTS_PER_TICK.get());
            activationLogged = true;
        }

        int maxEvents = SableFluidPhysicsConfig.IMPACT_MAX_EVENTS_PER_TICK.get();
        int maxSplashUnits = SableFluidPhysicsConfig.IMPACT_MAX_SPLASH_UNITS_PER_TICK.get();
        if (runtime.eventsProcessed >= maxEvents
                || (runtime.splashUnitsPlaced >= maxSplashUnits
                && SableFluidPhysicsConfig.IMPACT_WATER_LOSS_PERCENT.get() < 100)) {
            return;
        }

        for (ServerLevel level : server.getAllLevels()) {
            if (runtime.eventsProcessed >= maxEvents
                    || (runtime.splashUnitsPlaced >= maxSplashUnits
                    && SableFluidPhysicsConfig.IMPACT_WATER_LOSS_PERCENT.get() < 100)) {
                break;
            }
            Object container = getContainer(level);
            if (container == null) continue;

            Object rawSublevels;
            try {
                rawSublevels = invoke(container, "getAllSubLevels");
            } catch (Throwable throwable) {
                warnOnce("sublevel-enumeration", "Could not enumerate Sable sublevels for impact displacement.");
                continue;
            }
            if (!(rawSublevels instanceof Iterable<?> sublevels)) continue;

            for (Object sublevel : sublevels) {
                if (runtime.eventsProcessed >= maxEvents
                        || (runtime.splashUnitsPlaced >= maxSplashUnits
                        && SableFluidPhysicsConfig.IMPACT_WATER_LOSS_PERCENT.get() < 100)) {
                    break;
                }
                ImpactSublevel impact = prepare(level, sublevel);
                if (impact == null || impact.mass() < SableFluidPhysicsConfig.IMPACT_MINIMUM_MASS.get()
                        || impact.speed() < SableFluidPhysicsConfig.IMPACT_MINIMUM_SPEED.get()) {
                    continue;
                }

                long lastEvent = runtime.lastImpactAt.getOrDefault(impact.key(), Long.MIN_VALUE / 2);
                if (gameTime - lastEvent < SableFluidPhysicsConfig.IMPACT_COOLDOWN_TICKS.get()) continue;

                long scanCells = impact.worldBounds().scanVolume();
                if (scanCells <= 0 || scanCells > MAX_WORLD_SCAN_CELLS) {
                    warnOnce("scan-too-large:" + impact.key(),
                            "Skipping impact displacement for sublevel " + impact.id()
                                    + ": transformed world bounds exceed the safe scan budget (" + scanCells + " cells).");
                    continue;
                }

                List<WaterCandidate> candidates = findOverlappingWater(impact, fluids);
                if (candidates.isEmpty()) continue;

                runtime.eventsProcessed++;
                runtime.lastImpactAt.put(impact.key(), gameTime);
                ImpactResult result = displaceImpact(impact, candidates, fluids, runtime);
                if (result.sourceBlocks() <= 0) {
                    MSpellCompatDebug.debug("[SableFluidImpactDisplacement][STAGE2.11.3-IMPACT-DISPLACEMENT-WATERLINE-SPLASH] overlap-without-displacement sublevel={} mass={} speed={} candidates={} splashRadius={} waterlineSearch={} reason=no-safe-waterline-splash-plan-or-fluid-write-failed",
                            impact.id(), format(impact.mass()), format(impact.speed()), candidates.size(),
                            result.splashRadius(), MAX_WATERLINE_SEARCH_STEPS);
                    continue;
                }

                String massSource = SableFluidPhysicsConfig.IMPACT_USE_SABLE_BEYOND_MASS.get()
                        && ModList.get().isLoaded("sable_beyond")
                        ? "sable_aggregate_with_sable_beyond_integration" : "sable_aggregate_fallback";
                MSpellCompatDebug.debug("[SableFluidImpactDisplacement][STAGE2.11.3-IMPACT-DISPLACEMENT-WATERLINE-SPLASH] event sublevel={} mass={} massSource={} speed={} splashRadius={} sourceBlocks={} sourceUnits={} splashUnits={} lossUnits={} firstSource={} firstSplash={}",
                        impact.id(), format(impact.mass()), massSource, format(impact.speed()), result.splashRadius(),
                        result.sourceBlocks(), result.sourceUnits(), result.splashUnits(), result.lossUnits(),
                        result.firstSource(), result.firstSplash());
            }
        }

        runtime.lastImpactAt.entrySet().removeIf(entry -> gameTime - entry.getValue() > 1_200L);
    }

    private static RuntimeState runtimeFor(MinecraftServer server, long gameTime) {
        synchronized (RUNTIME) {
            RuntimeState state = RUNTIME.computeIfAbsent(server, ignored -> new RuntimeState());
            if (state.gameTime != gameTime) {
                state.gameTime = gameTime;
                state.eventsProcessed = 0;
                state.splashUnitsPlaced = 0;
            }
            return state;
        }
    }

    private static Object getContainer(ServerLevel level) {
        try {
            Class<?> type = Class.forName(SABLE_CONTAINER_CLASS, true,
                    SableFluidImpactDisplacement.class.getClassLoader());
            Method method = type.getMethod("getContainer", Level.class);
            return method.invoke(null, level);
        } catch (Throwable throwable) {
            warnOnce("container-api", "Could not access Sable's sublevel container API.");
            return null;
        }
    }

    private static ImpactSublevel prepare(ServerLevel level, Object sublevel) {
        try {
            if (Boolean.TRUE.equals(invoke(sublevel, "isRemoved"))) return null;

            Object plot = invoke(sublevel, "getPlot");
            BlockPos center = (BlockPos) invoke(plot, "getCenterBlock");
            Object rawStorage = invoke(plot, "getBoundingBox");
            LocalBounds storage = new LocalBounds(
                    readInt(rawStorage, "minX") - center.getX(),
                    readInt(rawStorage, "minY") - center.getY(),
                    readInt(rawStorage, "minZ") - center.getZ(),
                    readInt(rawStorage, "maxX") - center.getX(),
                    readInt(rawStorage, "maxY") - center.getY(),
                    readInt(rawStorage, "maxZ") - center.getZ());
            if (storage.width() <= 0 || storage.height() <= 0 || storage.depth() <= 0) return null;

            Object rawAccessor = invoke(plot, "getEmbeddedLevelAccessor");
            if (!(rawAccessor instanceof LevelAccessor accessor)) return null;
            Object pose = invoke(sublevel, "logicalPose");
            Object rawWorldBounds = invoke(sublevel, "boundingBox");
            WorldBounds worldBounds = new WorldBounds(
                    readDouble(rawWorldBounds, "minX"), readDouble(rawWorldBounds, "minY"),
                    readDouble(rawWorldBounds, "minZ"), readDouble(rawWorldBounds, "maxX"),
                    readDouble(rawWorldBounds, "maxY"), readDouble(rawWorldBounds, "maxZ"));

            Object rawVelocity = sublevel.getClass().getField("latestLinearVelocity").get(sublevel);
            Vec3 velocity = new Vec3(readDouble(rawVelocity, "x"),
                    readDouble(rawVelocity, "y"), readDouble(rawVelocity, "z"));
            double speed = velocity.length();
            if (!Double.isFinite(speed)) return null;

            Object tracker = invoke(sublevel, "getMassTracker");
            double mass = readDouble(tracker, "getMass");
            if (!Double.isFinite(mass) || mass < 0.0) return null;

            String id = String.valueOf(invoke(sublevel, "getUniqueId"));
            String dimension = String.valueOf(level.dimension().location());
            return new ImpactSublevel(level, accessor, center, pose, storage, worldBounds,
                    velocity, speed, mass, id, dimension + "|" + id);
        } catch (Throwable throwable) {
            warnOnce("prepare:" + System.identityHashCode(sublevel),
                    "Could not prepare a Sable sublevel for impact displacement.");
            return null;
        }
    }

    private static List<WaterCandidate> findOverlappingWater(ImpactSublevel impact, FluidAccess fluids) {
        WorldBounds bounds = impact.worldBounds();
        int minX = floor(bounds.minX());
        int minY = Math.max(impact.world().getMinBuildHeight(), floor(bounds.minY()));
        int minZ = floor(bounds.minZ());
        int maxX = floor(bounds.maxX() - 1.0e-7);
        int maxY = Math.min(impact.world().getMaxBuildHeight() - 1, floor(bounds.maxY() - 1.0e-7));
        int maxZ = floor(bounds.maxZ() - 1.0e-7);
        if (minX > maxX || minY > maxY || minZ > maxZ) return List.of();

        Vec3 direction = impact.speed() > 1.0e-9
                ? impact.velocity().scale(1.0 / impact.speed()) : Vec3.ZERO;
        double centerX = (bounds.minX() + bounds.maxX()) * 0.5;
        double centerY = (bounds.minY() + bounds.maxY()) * 0.5;
        double centerZ = (bounds.minZ() + bounds.maxZ()) * 0.5;
        List<WaterCandidate> result = new ArrayList<>();
        boolean displaceSources = SableFluidPhysicsConfig.IMPACT_DISPLACE_SOURCE_WATER.get();

        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!impact.world().hasChunkAt(pos)) continue;
                    FluidState fluidState = impact.world().getFluidState(pos);
                    Fluid type = fluidState.getType();
                    if (fluidState.isEmpty() || !isWater(type) || !fluids.modifies(type)) continue;
                    if (fluidState.isSource() && !displaceSources) continue;
                    int amount = Math.max(1, Math.min(8, fluidState.getAmount()));

                    Vec3 cellCenter = new Vec3(x + 0.5, y + 0.5, z + 0.5);
                    double forward = (cellCenter.x - centerX) * direction.x
                            + (cellCenter.y - centerY) * direction.y
                            + (cellCenter.z - centerZ) * direction.z;

                    if (impact.speed() > 1.0e-9 && forward < -0.05) continue;
                    if (!overlapsSolidLocalCell(impact, pos)) continue;
                    result.add(new WaterCandidate(pos, type, amount, fluidState.isSource(), forward));
                }
            }
        }

        result.sort(Comparator.comparingDouble(WaterCandidate::forwardProjection).reversed()
                .thenComparingInt(candidate -> candidate.source() ? 1 : 0)
                .thenComparing(candidate -> candidate.pos().asLong()));
        return result;
    }

    private static boolean overlapsSolidLocalCell(ImpactSublevel impact, BlockPos worldPos) {
        for (double sx : CELL_SAMPLE_OFFSETS) {
            for (double sy : CELL_SAMPLE_OFFSETS) {
                for (double sz : CELL_SAMPLE_OFFSETS) {
                    Vec3 worldSample = new Vec3(worldPos.getX() + sx,
                            worldPos.getY() + sy, worldPos.getZ() + sz);
                    Vec3 localAbsolute = inverseTransformPosition(impact.pose(), worldSample);
                    if (localAbsolute == null) return false;

                    double localX = localAbsolute.x - impact.center().getX();
                    double localY = localAbsolute.y - impact.center().getY();
                    double localZ = localAbsolute.z - impact.center().getZ();
                    int x = floor(localX);
                    int y = floor(localY);
                    int z = floor(localZ);
                    if (!impact.storageBounds().contains(x, y, z)) continue;

                    BlockPos localPos = new BlockPos(x, y, z);
                    BlockState localState = impact.accessor().getBlockState(localPos);
                    if (localState.isAir() || !localState.getFluidState().isEmpty()) continue;
                    var shape = localState.getCollisionShape(impact.accessor(), localPos);
                    if (shape.isEmpty()) continue;
                    double fracX = localX - x;
                    double fracY = localY - y;
                    double fracZ = localZ - z;

                    for (var box : shape.toAabbs()) {
                        if (box.contains(fracX, fracY, fracZ)) return true;
                    }
                }
            }
        }
        return false;
    }

    private static ImpactResult displaceImpact(ImpactSublevel impact, List<WaterCandidate> candidates,
                                                FluidAccess fluids, RuntimeState runtime) {
        int maxBlocks = SableFluidPhysicsConfig.IMPACT_MAX_SOURCE_BLOCKS_PER_EVENT.get();
        int configuredRadius = Math.max(1, SableFluidPhysicsConfig.IMPACT_SPLASH_RADIUS.get());

        int massBonus = (int) Math.floor(Math.log10(Math.max(1.0, impact.mass()))) - 2;
        int speedBonus = Math.min(3, (int) Math.floor(impact.speed() / 12.0));
        int radius = Math.min(8, configuredRadius
                + Math.max(0, Math.min(4, massBonus)) + speedBonus);
        int maxPerSplash = SableFluidPhysicsConfig.IMPACT_SPLASH_UNITS_PER_BLOCK.get();
        int lossPercent = SableFluidPhysicsConfig.IMPACT_WATER_LOSS_PERCENT.get();
        int maxSplashUnits = SableFluidPhysicsConfig.IMPACT_MAX_SPLASH_UNITS_PER_TICK.get();

        int sourceBlocks = 0;
        int candidatesExamined = 0;
        int sourceUnits = 0;
        int splashUnits = 0;
        int lossUnits = 0;
        BlockPos firstSource = null;
        BlockPos firstSplash = null;
        Set<BlockPos> reservedSplashPositions = new HashSet<>();

        for (WaterCandidate candidate : candidates) {
            if (sourceBlocks >= maxBlocks || candidatesExamined >= maxBlocks * 4) break;
            candidatesExamined++;
            FluidState currentState = impact.world().getFluidState(candidate.pos());
            if (currentState.isEmpty() || !isWater(currentState.getType())
                    || !fluids.modifies(currentState.getType())) continue;
            if (currentState.isSource() && !SableFluidPhysicsConfig.IMPACT_DISPLACE_SOURCE_WATER.get()) continue;

            Fluid sourceType = currentState.getType();
            int amount = Math.max(1, Math.min(8, currentState.getAmount()));
            int recoverable = Math.max(0, Math.min(amount,
                    (int) Math.round(amount * (100.0 - lossPercent) / 100.0)));
            if (runtime.splashUnitsPlaced + splashUnits + recoverable > maxSplashUnits) continue;

            List<SplashPlacement> plan = buildSplashPlan(impact, candidate.pos(), sourceType,
                    recoverable, maxPerSplash, radius, reservedSplashPositions);
            int plannedUnits = plan.stream().mapToInt(SplashPlacement::units).sum();
            if (plannedUnits != recoverable) continue;

            List<SplashPlacement> placed = new ArrayList<>(plan.size());
            boolean placementFailed = false;
            for (SplashPlacement placement : plan) {
                if (!isEmptySplashTarget(impact.world(), placement.pos())) {
                    placementFailed = true;
                    break;
                }
                boolean written = fluids.setAmount(impact.world(), placement.pos(), sourceType, placement.units());
                FluidState afterWrite = impact.world().getFluidState(placement.pos());
                if (written || (!afterWrite.isEmpty() && isWater(afterWrite.getType()))) {

                    placed.add(placement);
                }
                if (!written || !matchesAmount(impact.world(), placement.pos(), placement.units())) {
                    placementFailed = true;
                    break;
                }
            }
            if (placementFailed) {
                rollbackSplashes(impact.world(), placed, sourceType, fluids);
                continue;
            }

            fluids.setAmount(impact.world(), candidate.pos(), sourceType, 0);
            boolean sourceNowEmpty = impact.world().getFluidState(candidate.pos()).isEmpty();
            if (!sourceNowEmpty) {
                rollbackSplashes(impact.world(), placed, sourceType, fluids);

                FluidState remaining = impact.world().getFluidState(candidate.pos());
                if (remaining.isEmpty() || !isWater(remaining.getType()) || remaining.getAmount() != amount) {
                    fluids.setAmount(impact.world(), candidate.pos(), sourceType, amount);
                }
                continue;
            }

            sourceBlocks++;
            sourceUnits += amount;
            splashUnits += recoverable;
            lossUnits += amount - recoverable;
            reservedSplashPositions.addAll(placed.stream().map(SplashPlacement::pos).toList());
            if (firstSource == null) firstSource = candidate.pos();
            if (firstSplash == null && !placed.isEmpty()) firstSplash = placed.get(0).pos();
        }

        runtime.splashUnitsPlaced += splashUnits;
        return new ImpactResult(sourceBlocks, sourceUnits, splashUnits, lossUnits, firstSource, firstSplash, radius);
    }

    private static List<SplashPlacement> buildSplashPlan(ImpactSublevel impact, BlockPos source,
                                                          Fluid fluid, int requestedUnits, int maxPerSplash,
                                                          int radius, Set<BlockPos> reserved) {
        if (requestedUnits <= 0) return List.of();

        BlockPos splashOrigin = findWaterlineAir(impact.world(), source, MAX_WATERLINE_SEARCH_STEPS);
        if (splashOrigin == null) return List.of();

        List<List<TargetCandidate>> targetsByDirection = new ArrayList<>(6);
        for (int i = 0; i < 6; i++) targetsByDirection.add(new ArrayList<>());

        int radiusSquared = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    int distanceSquared = dx * dx + dy * dy + dz * dz;
                    if (distanceSquared > radiusSquared) continue;

                    BlockPos target = splashOrigin.offset(dx, dy, dz);
                    if (reserved.contains(target) || !isEmptySplashTarget(impact.world(), target)) continue;
                    if (mapsInsideStorage(impact, target)) continue;

                    int ax = Math.abs(dx);
                    int ay = Math.abs(dy);
                    int az = Math.abs(dz);
                    int direction;
                    if (ax >= ay && ax >= az) {
                        direction = dx >= 0 ? 0 : 1;
                    } else if (ay >= az) {
                        direction = dy >= 0 ? 2 : 3;
                    } else {
                        direction = dz >= 0 ? 4 : 5;
                    }
                    targetsByDirection.get(direction).add(new TargetCandidate(target, distanceSquared));
                }
            }
        }

        Comparator<TargetCandidate> outermostFirst = Comparator
                .comparingInt(TargetCandidate::distanceSquared).reversed()
                .thenComparing(candidate -> candidate.pos().asLong());
        int maxRounds = 0;
        for (List<TargetCandidate> directionTargets : targetsByDirection) {
            directionTargets.sort(outermostFirst);
            maxRounds = Math.max(maxRounds, directionTargets.size());
        }

        int firstDirection = Math.floorMod(source.hashCode(), 6);
        int remaining = requestedUnits;
        List<SplashPlacement> plan = new ArrayList<>();
        for (int round = 0; round < maxRounds && remaining > 0; round++) {
            for (int offset = 0; offset < 6 && remaining > 0; offset++) {
                List<TargetCandidate> directionTargets =
                        targetsByDirection.get((firstDirection + offset) % 6);
                if (round >= directionTargets.size()) continue;

                TargetCandidate target = directionTargets.get(round);
                int units = Math.min(remaining, maxPerSplash);
                if (units <= 0) break;
                plan.add(new SplashPlacement(target.pos(), units));
                remaining -= units;
            }
        }
        return remaining == 0 ? plan : List.of();
    }

    private static BlockPos findWaterlineAir(ServerLevel world, BlockPos source, int maxSteps) {
        for (int dy = 1; dy <= maxSteps; dy++) {
            BlockPos pos = source.offset(0, dy, 0);
            if (pos.getY() < world.getMinBuildHeight() || pos.getY() >= world.getMaxBuildHeight()
                    || !world.hasChunkAt(pos)) {
                return null;
            }

            FluidState fluid = world.getFluidState(pos);
            if (!fluid.isEmpty()) {
                if (isWater(fluid.getType())) continue;
                return null;
            }

            return world.getBlockState(pos).isAir() ? pos.immutable() : null;
        }
        return null;
    }

    private static boolean mapsInsideStorage(ImpactSublevel impact, BlockPos worldPos) {
        Vec3 localAbsolute = inverseTransformPosition(impact.pose(),
                new Vec3(worldPos.getX() + 0.5, worldPos.getY() + 0.5, worldPos.getZ() + 0.5));
        if (localAbsolute == null) return true;
        int x = floor(localAbsolute.x - impact.center().getX());
        int y = floor(localAbsolute.y - impact.center().getY());
        int z = floor(localAbsolute.z - impact.center().getZ());
        return impact.storageBounds().contains(x, y, z);
    }

    private static boolean isEmptySplashTarget(ServerLevel world, BlockPos pos) {
        if (!world.hasChunkAt(pos) || pos.getY() < world.getMinBuildHeight()
                || pos.getY() >= world.getMaxBuildHeight()) return false;
        BlockState state = world.getBlockState(pos);
        return state.isAir() && world.getFluidState(pos).isEmpty();
    }

    private static boolean matchesAmount(ServerLevel world, BlockPos pos, int amount) {
        FluidState state = world.getFluidState(pos);
        return amount <= 0 ? state.isEmpty() : isWater(state.getType()) && state.getAmount() == amount;
    }

    private static void rollbackSplashes(ServerLevel world, List<SplashPlacement> placed,
                                         Fluid sourceType, FluidAccess fluids) {
        for (SplashPlacement placement : placed) {
            FluidState current = world.getFluidState(placement.pos());
            if (!current.isEmpty() && isWater(current.getType())) {
                fluids.setAmount(world, placement.pos(), sourceType, 0);
            }
        }
    }

    private static Vec3 inverseTransformPosition(Object pose, Vec3 worldPosition) {
        try {
            Object result = pose.getClass().getMethod("transformPositionInverse", Vec3.class)
                    .invoke(pose, worldPosition);
            if (result instanceof Vec3 vector && Double.isFinite(vector.x)
                    && Double.isFinite(vector.y) && Double.isFinite(vector.z)) return vector;
        } catch (Throwable ignored) {

        }
        return null;
    }

    private static FluidAccess fluidAccess() {
        if (fluidLookupAttempted) return cachedFluidAccess;
        synchronized (SableFluidImpactDisplacement.class) {
            if (fluidLookupAttempted) return cachedFluidAccess;
            if (!ModList.get().isLoaded("flowing_fluids")) return null;
            fluidLookupAttempted = true;
            try {
                Class<?> apiType = Class.forName(FF_API_CLASS, true,
                        SableFluidImpactDisplacement.class.getClassLoader());
                Object api = apiType.getMethod("getInstance", String.class).invoke(null, CLIENT_ID);
                cachedFluidAccess = new FluidAccess(api,
                        apiType.getMethod("isModEnabled"),
                        apiType.getMethod("doesModifyThisFluid", Fluid.class),
                        apiType.getMethod("modifyFluidAmountAtPos",
                                LevelAccessor.class, BlockPos.class, Fluid.class, int.class));
            } catch (Throwable throwable) {
                warnOnce("flowing-fluids-api", "Could not initialize the Flowing Fluids API for impact displacement.");
                cachedFluidAccess = null;
            }
            return cachedFluidAccess;
        }
    }

    private static boolean isWater(Fluid type) {
        return type == Fluids.WATER || type == Fluids.FLOWING_WATER;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private static Object invoke(Object target, String methodName) throws ReflectiveOperationException {
        if (target == null) throw new ReflectiveOperationException("null target for " + methodName + "()");
        return target.getClass().getMethod(methodName).invoke(target);
    }

    private static int readInt(Object target, String methodName) throws ReflectiveOperationException {
        Object value = invoke(target, methodName);
        if (value instanceof Number number) return number.intValue();
        throw new ReflectiveOperationException(methodName + "() did not return a number");
    }

    private static double readDouble(Object target, String methodName) throws ReflectiveOperationException {
        Object value = invoke(target, methodName);
        if (value instanceof Number number) return number.doubleValue();
        throw new ReflectiveOperationException(methodName + "() did not return a number");
    }

    private static void warnOnce(String key, String message) {
        synchronized (WARNED) {
            if (!WARNED.add(key)) return;
        }
        MSpellCompatDebug.debug("[SableFluidImpactDisplacement] {}", message);
    }

    private record LocalBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        private boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }
        private int width() { return maxX - minX + 1; }
        private int height() { return maxY - minY + 1; }
        private int depth() { return maxZ - minZ + 1; }
    }

    private record WorldBounds(double minX, double minY, double minZ,
                               double maxX, double maxY, double maxZ) {
        private long scanVolume() {
            long width = (long) floor(maxX - 1.0e-7) - floor(minX) + 1L;
            long height = (long) floor(maxY - 1.0e-7) - floor(minY) + 1L;
            long depth = (long) floor(maxZ - 1.0e-7) - floor(minZ) + 1L;
            if (width <= 0 || height <= 0 || depth <= 0) return 0;
            if (width > MAX_WORLD_SCAN_CELLS || height > MAX_WORLD_SCAN_CELLS
                    || depth > MAX_WORLD_SCAN_CELLS) return MAX_WORLD_SCAN_CELLS + 1;
            if (width * height > MAX_WORLD_SCAN_CELLS
                    || width * height * depth > MAX_WORLD_SCAN_CELLS) return MAX_WORLD_SCAN_CELLS + 1;
            return width * height * depth;
        }
    }

    private record ImpactSublevel(ServerLevel world, LevelAccessor accessor, BlockPos center, Object pose,
                                  LocalBounds storageBounds, WorldBounds worldBounds, Vec3 velocity,
                                  double speed, double mass, String id, String key) {
    }

    private record WaterCandidate(BlockPos pos, Fluid type, int amount, boolean source,
                                  double forwardProjection) {
    }

    private record SplashPlacement(BlockPos pos, int units) {
    }

    private record TargetCandidate(BlockPos pos, int distanceSquared) {
    }

    private record ImpactResult(int sourceBlocks, int sourceUnits, int splashUnits, int lossUnits,
                                BlockPos firstSource, BlockPos firstSplash, int splashRadius) {
    }

    private static final class RuntimeState {
        private long gameTime = Long.MIN_VALUE;
        private int eventsProcessed;
        private int splashUnitsPlaced;
        private final Map<String, Long> lastImpactAt = new HashMap<>();
    }

    private static final class FluidAccess {
        private final Object api;
        private final Method isModEnabled;
        private final Method doesModifyThisFluid;
        private final Method modifyFluidAmountAtPos;
        private final Map<Fluid, Boolean> managedCache = new HashMap<>();

        private FluidAccess(Object api, Method isModEnabled, Method doesModifyThisFluid,
                            Method modifyFluidAmountAtPos) {
            this.api = api;
            this.isModEnabled = isModEnabled;
            this.doesModifyThisFluid = doesModifyThisFluid;
            this.modifyFluidAmountAtPos = modifyFluidAmountAtPos;
        }

        private boolean isEnabled() {
            try {
                return Boolean.TRUE.equals(isModEnabled.invoke(api));
            } catch (Throwable ignored) {
                return false;
            }
        }

        private boolean modifies(Fluid fluid) {
            Boolean cached = managedCache.get(fluid);
            if (cached != null) return cached;
            boolean result = false;
            try {
                result = Boolean.TRUE.equals(doesModifyThisFluid.invoke(api, fluid));
            } catch (Throwable ignored) {

            }
            managedCache.put(fluid, result);
            return result;
        }

        private boolean setAmount(LevelAccessor level, BlockPos pos, Fluid fluid, int amount) {
            try {
                return Boolean.TRUE.equals(modifyFluidAmountAtPos.invoke(api, level, pos, fluid, amount));
            } catch (Throwable ignored) {
                return false;
            }
        }
    }
}
