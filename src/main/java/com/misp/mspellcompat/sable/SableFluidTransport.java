package com.misp.mspellcompat.sable;

import com.misp.mspellcompat.util.MSpellCompatDebug;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


@SuppressWarnings("resource")
public final class SableFluidTransport {

    private static final AtomicInteger TRACE_EVENT_COUNT = new AtomicInteger();
    private static final Map<Level, Map<Long, Long>> TRACE_WORLD_POSITIONS = new WeakHashMap<>();
    private static final int TRACE_WORLD_POSITION_LIMIT = 512;
    private static final long TRACE_WORLD_POSITION_TTL = 200L;

    private static final String SABLE_CONTAINER_CLASS =
            "dev.ryanhcode.sable.api.sublevel.SubLevelContainer";
    private static final String FF_API_CLASS = "traben.flowing_fluids.api.FlowingFluidsAPI";
    private static final String FF_CLIENT_NAME = "mspellcompat_sable_block_transport";

    private static final DirectionOffset[] DIRECTIONS = {
            new DirectionOffset(1, 0, 0), new DirectionOffset(-1, 0, 0),
            new DirectionOffset(0, 1, 0), new DirectionOffset(0, -1, 0),
            new DirectionOffset(0, 0, 1), new DirectionOffset(0, 0, -1)
    };


    private static final DirectionOffset LOCAL_UP = new DirectionOffset(0, 1, 0);
    private static final DirectionOffset[] HORIZONTAL_DIRECTIONS = {
            new DirectionOffset(1, 0, 0), new DirectionOffset(-1, 0, 0),
            new DirectionOffset(0, 0, 1), new DirectionOffset(0, 0, -1)
    };
    private static final int INLET_MAX_CATCH_POINTS = 4096;
    private static final int INLET_MAX_OCCLUSION_HEIGHT = 32;
    private static final int INLET_MAX_CONTAINMENT_DISTANCE = 16;
    private static final int INLET_MAX_FILL_HEIGHT = 32;
    private static final int INLET_AMBIENT_BODY_DEPTH = 3;


    private static final Map<ServerLevel, List<StorageBounds>> ACTIVE_PLOT_BOUNDS = new WeakHashMap<>();
    private static final int MAX_KNOWN_STORAGE_BOUNDS_PER_LEVEL = 512;
    private static final Map<MinecraftServer, Integer> TICK_COUNTERS = new WeakHashMap<>();
    private static final Map<MinecraftServer, Boolean> INITIALIZED_SERVERS = new WeakHashMap<>();

    private static final Map<String, Map<Long, DetachedSurfaceMark>> RECENTLY_DETACHED_WORLD_CELLS = new ConcurrentHashMap<>();
    private static final long DETACHED_WORLD_CELL_COOLDOWN_TICKS = 20L;
    private static int roundRobinStart;
    private static volatile ApiAccess cachedApi;
    private static final Map<Class<?>, Method> POSE_TRANSFORM_METHODS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Method> POSE_INVERSE_TRANSFORM_METHODS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Method> POSE_INVERSE_NORMAL_METHODS = new ConcurrentHashMap<>();
    private static final Map<NoArgMethodKey, Method> NO_ARG_METHODS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Method> SABLE_CONTAINER_METHODS = new ConcurrentHashMap<>();

    private static final Map<String, LocalGridCache> CACHED_LOCAL_GRIDS = new ConcurrentHashMap<>();
    private static volatile boolean apiLookupAttempted;

    private SableFluidTransport() {
    }

    private static void trace(String event, String details) {
        if (MSpellCompatDebug.isDebugLoggingDisabled()) return;
        int index = TRACE_EVENT_COUNT.incrementAndGet();
        MSpellCompatDebug.log("sable_transport", event + " #" + index, details);
    }

    private static String fluidId(Fluid fluid) {
        if (fluid == null) return "none";
        try {
            return BuiltInRegistries.FLUID.getKey(fluid).toString();
        } catch (Throwable ignored) {
            return fluid.toString();
        }
    }

    private static String blockId(BlockState state) {
        try {
            return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        } catch (Throwable ignored) {
            return state.getBlock().toString();
        }
    }

    private static String levelId(LevelAccessor level) {
        if (level instanceof Level mcLevel) {
            return mcLevel.getClass().getSimpleName() + "/" + mcLevel.dimension().location();
        }
        return level.getClass().getName();
    }

    private static String cellDescription(LevelAccessor level, BlockPos pos) {
        BlockState block = level.getBlockState(pos);
        FluidState state = level.getFluidState(pos);
        String fluid = state.isEmpty() ? "none"
                : fluidId(state.getType()) + ":amount=" + state.getAmount();
        return "level=" + levelId(level) + ",pos=" + pos + ",block=" + blockId(block)
                + ",fluid=" + fluid;
    }

    private static String summarizeFluids(Map<CellPos, CellData> cells) {
        Map<String, int[]> summary = new TreeMap<>();
        for (CellData data : cells.values()) {
            if (data == null || !data.liquid() || data.amount() <= 0 || data.fluidType() == null) continue;
            int[] values = summary.computeIfAbsent(fluidId(data.fluidType()), ignored -> new int[2]);
            values[0]++;
            values[1] += data.amount();
        }
        if (summary.isEmpty()) return "fluids={}";
        StringBuilder result = new StringBuilder("fluids={");
        boolean first = true;
        for (Map.Entry<String, int[]> entry : summary.entrySet()) {
            if (!first) result.append(',');
            first = false;
            result.append(entry.getKey()).append(":cells=").append(entry.getValue()[0])
                    .append(":units=").append(entry.getValue()[1]);
        }
        return result.append('}').toString();
    }

    private static String summarizeFluidContacts(Map<CellPos, CellData> cells) {
        int count = 0;
        StringBuilder sample = new StringBuilder();
        DirectionOffset[] positiveDirections = {
                new DirectionOffset(1, 0, 0),
                new DirectionOffset(0, 1, 0),
                new DirectionOffset(0, 0, 1)
        };
        for (Map.Entry<CellPos, CellData> entry : cells.entrySet()) {
            CellData first = entry.getValue();
            if (first == null || !first.liquid() || first.fluidType() == null) continue;
            for (DirectionOffset direction : positiveDirections) {
                CellPos otherPos = entry.getKey().offset(direction);
                CellData second = cells.get(otherPos);
                if (second == null || !second.liquid() || second.fluidType() == null
                        || sameFluidFamily(first.fluidType(), second.fluidType())) continue;
                count++;
                if (count <= 12) {
                    if (!sample.isEmpty()) sample.append(';');
                    sample.append(entry.getKey()).append('=')
                            .append(fluidId(first.fluidType())).append(':').append(first.amount())
                            .append("<->").append(otherPos).append('=')
                            .append(fluidId(second.fluidType())).append(':').append(second.amount());
                }
            }
        }
        return "fluidContacts=" + count + ",contactSample=[" + sample + "]";
    }

    private static void markWorldTracePosition(Level level, BlockPos pos) {
        if (level.isClientSide) return;
        long now = level.getGameTime();
        synchronized (TRACE_WORLD_POSITIONS) {
            Map<Long, Long> positions = TRACE_WORLD_POSITIONS.computeIfAbsent(level, ignored -> new HashMap<>());
            positions.entrySet().removeIf(entry -> now - entry.getValue() > TRACE_WORLD_POSITION_TTL);
            positions.put(pos.asLong(), now);
            while (positions.size() > TRACE_WORLD_POSITION_LIMIT) {
                Long oldestKey = null;
                long oldestTime = Long.MAX_VALUE;
                for (Map.Entry<Long, Long> entry : positions.entrySet()) {
                    if (entry.getValue() < oldestTime) {
                        oldestTime = entry.getValue();
                        oldestKey = entry.getKey();
                    }
                }
                if (oldestKey == null) break;
                positions.remove(oldestKey);
            }
        }
    }

    private static boolean nearWorldTracePosition(Level level, BlockPos pos) {
        long now = level.getGameTime();
        synchronized (TRACE_WORLD_POSITIONS) {
            Map<Long, Long> positions = TRACE_WORLD_POSITIONS.get(level);
            if (positions == null || positions.isEmpty()) return false;
            positions.entrySet().removeIf(entry -> now - entry.getValue() > TRACE_WORLD_POSITION_TTL);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Long lastSeen = positions.get(pos.offset(dx, dy, dz).asLong());
                        if (lastSeen != null && now - lastSeen <= TRACE_WORLD_POSITION_TTL) return true;
                    }
                }
            }
            return false;
        }
    }

    private static boolean nearKnownPlotStorage(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return false;
        synchronized (ACTIVE_PLOT_BOUNDS) {
            List<StorageBounds> boundsList = ACTIVE_PLOT_BOUNDS.get(serverLevel);
            if (boundsList == null) return false;
            for (StorageBounds bounds : boundsList) {
                if (bounds.inflate(16).contains(pos)) return true;
            }
        }
        return false;
    }

    public static void traceFluidTick(Level level, BlockPos pos, FluidState state,
                                      String tickKind, boolean suppressed) {
        if (MSpellCompatDebug.isDebugLoggingDisabled() || level.isClientSide || state == null || state.isEmpty()
                || !(state.getType() instanceof FlowingFluid)) return;
        boolean nearPlot = nearKnownPlotStorage(level, pos);
        boolean nearby = suppressed || nearWorldTracePosition(level, pos) || nearPlot;
        if (!nearby) return;
        trace("FLUID_TICK", "kind=" + tickKind + ",suppressed=" + suppressed
                + ",nearPlot=" + nearPlot + ",gameTime=" + level.getGameTime()
                + "," + cellDescription(level, pos));
        if (!suppressed && !nearPlot) markWorldTracePosition(level, pos);
    }


    public static void onServerTickPre(ServerTickEvent.Pre event) {
        MinecraftServer server = event.getServer();
        if (!SableFluidRuntimeConfig.ENABLED.get()
                || !ModList.get().isLoaded("sable")
                || !ModList.get().isLoaded("flowing_fluids")) return;

        ApiAccess api = apiAccess();
        if (api == null || !api.isEnabled()) return;

        boolean initialized;
        synchronized (INITIALIZED_SERVERS) {
            initialized = INITIALIZED_SERVERS.containsKey(server);
        }
        if (!initialized) {
            runTransportPass(server, api);
            synchronized (INITIALIZED_SERVERS) {
                INITIALIZED_SERVERS.put(server, Boolean.TRUE);
            }
            synchronized (TICK_COUNTERS) {
                TICK_COUNTERS.put(server, 0);
            }
            return;
        }
        runLocalBoundaryInputPass(server, api);
    }


    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!SableFluidRuntimeConfig.ENABLED.get()
                || !ModList.get().isLoaded("sable")
                || !ModList.get().isLoaded("flowing_fluids")) {
            clearPlotBounds();
            return;
        }

        ApiAccess api = apiAccess();
        if (api == null) {
            clearPlotBounds();
            return;
        }
        boolean flowingFluidsEnabled = api.isEnabled();
        if (!flowingFluidsEnabled) {
            clearPlotBounds();
            return;
        }


        if (!intervalElapsed(server, SableFluidRuntimeConfig.SCAN_INTERVAL_TICKS.get())) {
            return;
        }

        runTransportPass(server, api);
    }


    public static boolean shouldSuppressManagedFluidTick(Level level, BlockPos pos, FluidState state,
                                                         String tickKind) {
        if (!(level instanceof ServerLevel serverLevel)
                || level.isClientSide
                || state == null
                || state.isEmpty()
                || !(state.getType() instanceof FlowingFluid)
                || !SableFluidRuntimeConfig.ENABLED.get()
                || !ModList.get().isLoaded("sable")
                || !ModList.get().isLoaded("flowing_fluids")) {
            return false;
        }

        ApiAccess api = apiAccess();
        if (api == null || !api.isEnabled() || !api.isManagedFluid(state.getType())) {
            return false;
        }

        synchronized (ACTIVE_PLOT_BOUNDS) {
            List<StorageBounds> boundsList = ACTIVE_PLOT_BOUNDS.get(serverLevel);
            if (boundsList != null) {
                for (StorageBounds bounds : boundsList) {
                    if (bounds.contains(pos)) {
                        trace("FLUID_TICK_SUPPRESS", "kind=" + tickKind + ",reason=known-sable-storage"
                                + "," + cellDescription(level, pos) + ",bounds=" + bounds);
                        return true;
                    }
                }
            }

        }
        return false;
    }

    private static boolean intervalElapsed(MinecraftServer server, int interval) {
        synchronized (TICK_COUNTERS) {
            int next = TICK_COUNTERS.getOrDefault(server, 0) + 1;
            if (next >= interval) {
                TICK_COUNTERS.put(server, 0);
                return true;
            }
            TICK_COUNTERS.put(server, next);
            return false;
        }
    }

    private static void clearPlotBounds() {
        synchronized (ACTIVE_PLOT_BOUNDS) {
            ACTIVE_PLOT_BOUNDS.clear();
        }
        CACHED_LOCAL_GRIDS.clear();
    }

    private static void runTransportPass(MinecraftServer server, ApiAccess api) {
        trace("PASS_START", "begin");
        List<SublevelTask> tasks = new ArrayList<>();
        Map<ServerLevel, List<StorageBounds>> nextBounds = new WeakHashMap<>();
        try {
            Class<?> containerClass = Class.forName(
                    SABLE_CONTAINER_CLASS, true, SableFluidTransport.class.getClassLoader());
            Method getContainer = sableContainerMethod(containerClass);

            for (ServerLevel level : server.getAllLevels()) {
                nextBounds.put(level, new ArrayList<>());

                Object container = getContainer.invoke(null, level);
                if (container == null) {
                    continue;
                }
                Object raw = invoke(container, "getAllSubLevels");
                if (!(raw instanceof Collection<?> sublevels)) {
                    continue;
                }

                for (Object sublevel : sublevels) {
                    SublevelTask task = createTask(level, sublevel);
                    if (task != null) {
                        tasks.add(task);
                    } else {


                        StorageBounds observed = readStorageBounds(sublevel);
                        if (observed != null) {
                            nextBounds.get(level).add(observed.inflate(2));
                        }
                    }
                }
            }
        } catch (Throwable throwable) {
            trace("PASS_ERROR", "phase=enumerate,error=" + throwable.getClass().getName()
                    + ",message=" + throwable.getMessage());
            MSpellCompatDebug.error("sable_transport", "pass_enumeration_failed", throwable,
                    "server=" + server + ",phase=enumerate");


            return;
        }

        trace("PASS_TASKS", "sublevels=" + tasks.size());
        if (tasks.isEmpty()) {


            mergeKnownStorageBounds(nextBounds);
            trace("PASS_END", "result=no-ready-sublevels");
            return;
        }

        int cellBudget = SableFluidRuntimeConfig.MAX_CELLS_PER_PASS.get();
        int perSublevelCap = SableFluidRuntimeConfig.MAX_CELLS_PER_SUBLEVEL.get();
        int usedCells = 0;
        int start = Math.floorMod(roundRobinStart, tasks.size());


        for (SublevelTask task : tasks) {


            nextBounds.computeIfAbsent(task.world(), ignored -> new ArrayList<>())
                    .add(task.storageBounds().inflate(2));
        }

        for (int i = 0; i < tasks.size(); i++) {
            SublevelTask task = tasks.get((start + i) % tasks.size());
            if (task.volume() > perSublevelCap || usedCells + task.volume() > cellBudget) {
                trace("SUBLEVEL_BUDGET_SKIP", "id=" + task.id() + ",volume=" + task.volume()
                        + ",usedCells=" + usedCells + ",passBudget=" + cellBudget
                        + ",sublevelCap=" + perSublevelCap);
                continue;
            }


            usedCells += task.volume();
            try {
                processSublevel(task, api);
            } catch (Throwable throwable) {
                trace("SUBLEVEL_ERROR", "id=" + task.id() + ",error=" + throwable.getClass().getName()
                        + ",message=" + throwable.getMessage());
                MSpellCompatDebug.error("sable_transport", "sublevel_processing_failed", throwable,
                        "sublevel=" + task.id() + ",bounds=" + task.storageBounds());

            }
        }

        roundRobinStart = (start + 1) % Math.max(1, tasks.size());
        mergeKnownStorageBounds(nextBounds);
        trace("PASS_END", "processedSublevels=" + tasks.size() + ",cellsScannedBudget=" + usedCells);
    }


    private static StorageBounds readStorageBounds(Object sublevel) {
        try {
            Object plot = invoke(sublevel, "getPlot");
            Object rawBounds = invoke(plot, "getBoundingBox");
            int minX = readInt(rawBounds, "minX");
            int minY = readInt(rawBounds, "minY");
            int minZ = readInt(rawBounds, "minZ");
            int maxX = readInt(rawBounds, "maxX");
            int maxY = readInt(rawBounds, "maxY");
            int maxZ = readInt(rawBounds, "maxZ");
            if (minX > maxX || minY > maxY || minZ > maxZ) return null;
            return new StorageBounds(minX, minY, minZ, maxX, maxY, maxZ);
        } catch (Throwable failure) {
            MSpellCompatDebug.error("sable_transport", "storage_bounds_read_failed", failure,
                    "sublevel=" + sublevel);
            return null;
        }
    }


    private static void mergeKnownStorageBounds(Map<ServerLevel, List<StorageBounds>> observedByLevel) {
        synchronized (ACTIVE_PLOT_BOUNDS) {
            for (Map.Entry<ServerLevel, List<StorageBounds>> entry : observedByLevel.entrySet()) {
                List<StorageBounds> merged = new ArrayList<>();
                for (StorageBounds bounds : entry.getValue()) {
                    if (bounds != null && !merged.contains(bounds)) merged.add(bounds);
                }
                List<StorageBounds> previouslyKnown = ACTIVE_PLOT_BOUNDS.get(entry.getKey());
                if (previouslyKnown != null) {
                    for (StorageBounds bounds : previouslyKnown) {
                        if (merged.size() >= MAX_KNOWN_STORAGE_BOUNDS_PER_LEVEL) break;
                        if (!merged.contains(bounds)) merged.add(bounds);
                    }
                }
                ACTIVE_PLOT_BOUNDS.put(entry.getKey(), List.copyOf(merged));
            }
        }
    }

    private static SublevelTask createTask(ServerLevel world, Object sublevel) {
        try {
            if (Boolean.TRUE.equals(invoke(sublevel, "isRemoved"))) {
                return null;
            }

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
            long volumeLong = width * height * depth;
            if (width <= 0 || height <= 0 || depth <= 0
                    || volumeLong > Integer.MAX_VALUE
                    || volumeLong > Math.min(
                            SableFluidRuntimeConfig.MAX_CELLS_PER_SUBLEVEL.get(),
                            SableFluidRuntimeConfig.MAX_CELLS_PER_PASS.get())) {
                return null;
            }

            BlockPos center = (BlockPos) invoke(plot, "getCenterBlock");
            Object rawAccessor = invoke(plot, "getEmbeddedLevelAccessor");
            if (!(rawAccessor instanceof LevelAccessor accessor)) {
                return null;
            }

            StorageBounds storage = new StorageBounds(minX, minY, minZ, maxX, maxY, maxZ);
            if (!storageChunksAlreadyLoaded(world, storage)) {
                return null;
            }

            LocalBounds local = new LocalBounds(
                    minX - center.getX(), minY - center.getY(), minZ - center.getZ(),
                    maxX - center.getX(), maxY - center.getY(), maxZ - center.getZ());
            Object pose = invoke(sublevel, "logicalPose");
            Vec3 gravity = localGravityDown(pose);
            if (gravity == null) {
                return null;
            }
            String id = String.valueOf(invoke(sublevel, "getUniqueId"));
            return new SublevelTask(world, accessor, pose, id, center,
                    storage, local, (int) volumeLong, gravity);
        } catch (Throwable failure) {
            MSpellCompatDebug.error("sable_transport", "sublevel_task_create_failed", failure,
                    "world=" + world + ",sublevel=" + sublevel);
            return null;
        }
    }

    private static boolean storageChunksAlreadyLoaded(ServerLevel world, StorageBounds bounds) {
        for (int chunkX = bounds.minX() >> 4; chunkX <= (bounds.maxX() >> 4); chunkX++) {
            for (int chunkZ = bounds.minZ() >> 4; chunkZ <= (bounds.maxZ() >> 4); chunkZ++) {
                if (!world.hasChunk(chunkX, chunkZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void processSublevel(SublevelTask task, ApiAccess api) {
        Map<CellPos, CellData> cells = readLocalCells(task, api);
        LocalBounds hullBounds = deriveHullBounds(cells);
        if (hullBounds == null) {
            trace("SUBLEVEL_SKIP", "id=" + task.id() + ",reason=no-hull,volume=" + task.volume());
            CACHED_LOCAL_GRIDS.remove(surfaceCacheKey(task));
            return;
        }
        trace("SUBLEVEL_BEGIN", "id=" + task.id() + ",center=" + task.center()
                + ",volume=" + task.volume() + ",localBounds=" + task.localBounds()
                + ",storageBounds=" + task.storageBounds() + ",gravityDown=" + task.gravityDown()
                + "," + summarizeFluids(cells) + "," + summarizeFluidContacts(cells));


        LocalGridCache grid = buildLocalGridCache(task, cells);
        CACHED_LOCAL_GRIDS.put(surfaceCacheKey(task), grid);

        List<CellPos> fluidCells = new ArrayList<>();
        for (Map.Entry<CellPos, CellData> entry : cells.entrySet()) {
            CellData data = entry.getValue();
            if (data.liquid() && data.amount() > 0) fluidCells.add(entry.getKey());
        }

        Comparator<CellPos> downFirst = Comparator
                .comparingDouble((CellPos p) -> gravityProjection(task.gravityDown(), p)).reversed()
                .thenComparingInt(CellPos::y).thenComparingInt(CellPos::z).thenComparingInt(CellPos::x);
        fluidCells.sort(downFirst);

        Set<CellPos> lockedCells = new HashSet<>();
        Set<Long> claimedExternalPositions = new HashSet<>();
        int transfers = 0;
        int maxTransfers = Math.max(SableFluidRuntimeConfig.MAX_TRANSFERS_PER_SUBLEVEL.get(), 16);
        int maxUnits = Math.max(SableFluidRuntimeConfig.UNITS_PER_TRANSFER.get(), 2);


        fluidCells.clear();
        for (Map.Entry<CellPos, CellData> entry : cells.entrySet()) {
            CellData data = entry.getValue();
            if (data.liquid() && data.amount() > 0) {


                fluidCells.add(entry.getKey());
            }
        }
        fluidCells.sort(downFirst);

        for (CellPos sourcePos : fluidCells) {
            if (transfers >= maxTransfers) break;
            if (lockedCells.contains(sourcePos)) continue;
            CellData source = cells.get(sourcePos);
            if (source == null || !source.liquid() || source.amount() <= 0) continue;

            List<GridNeighborCandidate> candidates = new ArrayList<>(6);
            for (DirectionOffset direction : DIRECTIONS) {
                CellPos targetPos = sourcePos.offset(direction);
                if (lockedCells.contains(targetPos)) continue;
                double slope = task.gravityDown().x * direction.dx()
                        + task.gravityDown().y * direction.dy()
                        + task.gravityDown().z * direction.dz();
                if (slope < -0.05 || (Math.abs(slope) <= 0.05 && source.amount() < 2)) continue;


                boolean localTarget = task.localBounds().contains(targetPos);
                int targetAmount = 0;
                BlockPos worldTarget = null;
                if (localTarget) {
                    CellData target = cells.get(targetPos);
                    if (!canReceiveInPlot(target, source.fluidType()) || (target.liquid() && target.amount() >= 8)) continue;
                    targetAmount = target.liquid() ? target.amount() : 0;
                } else {


                    for (WorldCellCandidate candidate : worldCellsForLocalTransition(task, sourcePos, targetPos)) {
                        CellData external = readCell(task.world(), candidate.worldPos(), api);
                        if (!canReceiveFromSource(external, source.fluidType(), api)) continue;
                        worldTarget = candidate.worldPos();
                        targetAmount = external.liquid() ? external.amount() : 0;
                        break;
                    }
                    if (worldTarget == null) continue;
                }

                int priority = flowPriority(slope, source.amount(), targetAmount);
                if (priority < 0) continue;
                candidates.add(new GridNeighborCandidate(targetPos, slope, priority, worldTarget, targetAmount));
            }


            candidates.sort(Comparator
                    .comparing((GridNeighborCandidate candidate) -> candidate.worldTarget() != null)
                    .thenComparing(Comparator.comparingInt(GridNeighborCandidate::priority).reversed()));

            for (GridNeighborCandidate candidate : candidates) {
                CellData currentSource = cells.get(sourcePos);
                if (currentSource == null || !currentSource.liquid() || currentSource.amount() <= 0) break;
                int transferLimit = maxUnits;
                if (candidate.slope() <= 0.05) {
                    transferLimit = Math.clamp(
                            (currentSource.amount() - candidate.targetAmount()) / 2, 1, transferLimit);
                }
                int moved;
                if (candidate.worldTarget() != null) {


                    if (candidate.targetAmount() == 0
                            && candidate.slope() <= 0.05
                            && task.localBounds().isBoundary(sourcePos)
                            && isHorizontallyContained(task, api, sourcePos)) {
                        continue;
                    }
                    if (claimedExternalPositions.contains(candidate.worldTarget().asLong())) continue;
                    moved = moveUnits(task.accessor(), toBlockPos(sourcePos),
                            task.world(), candidate.worldTarget(), currentSource.fluidType(),
                            transferLimit, api, true, false);
                    if (moved > 0) {
                        cells.put(sourcePos, readCell(task.accessor(), toBlockPos(sourcePos), api));
                        lockedCells.add(sourcePos);
                        lockedCells.add(candidate.localTarget());
                        claimedExternalPositions.add(candidate.worldTarget().asLong());
                        transfers++;
                        markRecentlyDetachedSurfaceCell(task, candidate.worldTarget());
                        break;
                    }
                } else {
                    BlockPos localSource = toBlockPos(sourcePos);
                    BlockPos localTarget = toBlockPos(candidate.localTarget());
                    moved = moveUnits(task.accessor(), localSource,
                            task.accessor(), localTarget, currentSource.fluidType(),
                            transferLimit, api, true, true);
                    if (moved > 0) {
                        cells.put(sourcePos, readCell(task.accessor(), localSource, api));
                        cells.put(candidate.localTarget(), readCell(task.accessor(), localTarget, api));
                        lockedCells.add(sourcePos);
                        lockedCells.add(candidate.localTarget());
                        transfers++;
                        break;
                    }
                }
            }
        }

        trace("SUBLEVEL_END", "id=" + task.id() + ",transfers=" + transfers
                + "," + summarizeFluids(cells) + "," + summarizeFluidContacts(cells));
    }

    private static int flowPriority(double slope, int sourceAmount, int targetAmount) {
        if (slope > 0.05) return 100 + (int) Math.round(slope * 100.0);
        if (Math.abs(slope) <= 0.05 && sourceAmount >= targetAmount + 2) {
            return 50 + sourceAmount - targetAmount;
        }
        return -1;
    }

    private static boolean canReceiveInPlot(CellData data, Fluid sourceFluid) {
        return data != null && (data.air() || (data.liquid() && data.amount() < 8
                && sameFluidFamily(sourceFluid, data.fluidType())));
    }

    private static String surfaceCacheKey(SublevelTask task) {
        return task.world().dimension().location() + "|" + task.id();
    }


    private static void runLocalBoundaryInputPass(MinecraftServer server, ApiAccess api) {
        Set<String> activeKeys = new HashSet<>();
        Map<ServerLevel, List<StorageBounds>> observedBounds = new WeakHashMap<>();
        try {
            Class<?> containerClass = Class.forName(
                    SABLE_CONTAINER_CLASS, true, SableFluidTransport.class.getClassLoader());
            Method getContainer = sableContainerMethod(containerClass);
            for (ServerLevel level : server.getAllLevels()) {
                observedBounds.put(level, new ArrayList<>());
                Set<Long> claimedExternal = new HashSet<>();
                Object container = getContainer.invoke(null, level);
                if (container == null) continue;
                Object raw = invoke(container, "getAllSubLevels");
                if (!(raw instanceof Collection<?> sublevels)) continue;

                for (Object sublevel : sublevels) {
                    if (Boolean.TRUE.equals(invoke(sublevel, "isRemoved"))) continue;
                    StorageBounds currentBounds = readStorageBounds(sublevel);
                    if (currentBounds != null) {
                        observedBounds.get(level).add(currentBounds.inflate(2));
                    }
                    String id = String.valueOf(invoke(sublevel, "getUniqueId"));
                    String key = level.dimension().location() + "|" + id;
                    activeKeys.add(key);
                    LocalGridCache grid = CACHED_LOCAL_GRIDS.get(key);
                    SublevelTask task;
                    if (grid == null) {


                        task = createTask(level, sublevel);
                        if (task == null) continue;
                        Map<CellPos, CellData> snapshot = readLocalCells(task, api);
                        LocalBounds hull = deriveHullBounds(snapshot);
                        if (hull == null) continue;
                        grid = buildLocalGridCache(task, snapshot);
                        CACHED_LOCAL_GRIDS.put(key, grid);
                    } else {
                        Object pose = invoke(sublevel, "logicalPose");
                        Vec3 gravity = localGravityDown(pose);
                        if (gravity == null) continue;
                        task = new SublevelTask(level, grid.accessor(), pose, id, grid.center(),
                                grid.storageBounds(), grid.localBounds(), grid.volume(), gravity);
                    }

                    int maxTransfers = SableFluidRuntimeConfig.MAX_TRANSFERS_PER_SUBLEVEL.get();
                    processInletCatchPointIntake(task, grid, api,
                            claimedExternal, maxTransfers);
                }
            }
        } catch (Throwable failure) {
            MSpellCompatDebug.error("sable_transport", "boundary_input_scan_failed", failure,
                    "server=" + server);
        }


        mergeKnownStorageBounds(observedBounds);
        CACHED_LOCAL_GRIDS.keySet().removeIf(key -> !activeKeys.contains(key));
        trace("BOUNDARY_INPUT_PASS_END", "activeSublevels=" + activeKeys.size()
                + ",levels=" + observedBounds.size() + ",cachedGrids=" + CACHED_LOCAL_GRIDS.size());
    }

    private static Map<CellPos, CellData> readLocalCells(SublevelTask task, ApiAccess api) {
        Map<CellPos, CellData> cells = new HashMap<>(Math.max(16, task.volume() * 2));
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        LocalBounds bounds = task.localBounds();
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                    mutable.set(x, y, z);
                    cells.put(new CellPos(x, y, z), readCell(task.accessor(), mutable, api));
                }
            }
        }
        return cells;
    }

    private static LocalGridCache buildLocalGridCache(
            SublevelTask task, Map<CellPos, CellData> cells) {
        List<InletCatchPoint> inletCatchPoints = findInletCatchPoints(task, cells);
        return new LocalGridCache(List.copyOf(inletCatchPoints), task.accessor(), task.center(),
                task.storageBounds(), task.localBounds(), task.volume());
    }


    private static List<InletCatchPoint> findInletCatchPoints(
            SublevelTask task, Map<CellPos, CellData> cells) {
        List<CellPos> solidDecks = new ArrayList<>();
        for (Map.Entry<CellPos, CellData> entry : cells.entrySet()) {
            CellData data = entry.getValue();
            if (data == null || data.blockState().isAir() || data.blockState().canBeReplaced()) continue;
            solidDecks.add(entry.getKey());
        }
        solidDecks.sort(Comparator.comparingInt(CellPos::y)
                .thenComparingInt(CellPos::x).thenComparingInt(CellPos::z));

        List<InletCatchPoint> result = new ArrayList<>();
        for (CellPos deck : solidDecks) {
            CellPos above = deck.offset(LOCAL_UP);
            if (!hasOpenPathAbove(task.localBounds(), cells, above)) {
                continue;
            }
            boolean contained = isHorizontallyContained(task.localBounds(), cells, above);
            result.add(new InletCatchPoint(deck, contained));
            if (result.size() >= INLET_MAX_CATCH_POINTS) break;
        }
        return List.copyOf(result);
    }

    private static boolean hasOpenPathAbove(LocalBounds bounds, Map<CellPos, CellData> cells,
                                            CellPos start) {
        CellPos cursor = start;
        for (int i = 0; i < INLET_MAX_OCCLUSION_HEIGHT; i++) {
            if (!bounds.contains(cursor)) return true;
            CellData data = cells.get(cursor);
            if (data == null) return false;

            if (!data.blockState().isAir() && data.fluidState().isEmpty()) return false;
            cursor = cursor.offset(LOCAL_UP);
        }
        return true;
    }

    private static boolean isHorizontallyContained(LocalBounds bounds, Map<CellPos, CellData> cells,
                                                    CellPos start) {
        for (DirectionOffset direction : HORIZONTAL_DIRECTIONS) {
            boolean wallFound = false;
            CellPos cursor = start;
            for (int distance = 0; distance < INLET_MAX_CONTAINMENT_DISTANCE; distance++) {
                cursor = cursor.offset(direction);
                if (!bounds.contains(cursor)) break;
                CellData data = cells.get(cursor);
                if (data == null) break;
                if (!data.blockState().isAir() && data.fluidState().isEmpty()) {
                    wallFound = true;
                    break;
                }
            }
            if (!wallFound) return false;
        }
        return true;
    }


    private static void processInletCatchPointIntake(
            SublevelTask task, LocalGridCache grid, ApiAccess api,
            Set<Long> claimedExternal, int maxTransfers) {
        if (maxTransfers <= 0 || grid.inletCatchPoints().isEmpty()) return;


        List<InletCatchPoint> ordered = new ArrayList<>(grid.inletCatchPoints());
        ordered.sort(Comparator
                .comparingDouble((InletCatchPoint point) -> gravityProjection(task.gravityDown(), point.localDeck())).reversed()
                .thenComparingInt(point -> point.localDeck().y())
                .thenComparingInt(point -> point.localDeck().z())
                .thenComparingInt(point -> point.localDeck().x()));

        Set<CellPos> lockedTargets = new HashSet<>();
        int movedThisSublevel = 0;
        for (InletCatchPoint catchPoint : ordered) {
            if (movedThisSublevel >= maxTransfers) break;

            boolean cavityCatchPoint = catchPoint.horizontallyContained();
            CellPos firstFillCell = catchPoint.localDeck().offset(LOCAL_UP);
            if (!task.localBounds().contains(firstFillCell)) continue;


            CellPos fillTarget = findInletFillTarget(task, api, catchPoint.localDeck(),
                    cavityCatchPoint);
            if (fillTarget == null || lockedTargets.contains(fillTarget)
                    || !task.localBounds().contains(fillTarget)
                    || (!cavityCatchPoint && task.localBounds().isBoundary(fillTarget))) continue;

            Vec3 worldCenter = transformedCellCenter(task, fillTarget);
            if (worldCenter == null) continue;
            BlockPos sourcePos = BlockPos.containing(worldCenter.x, worldCenter.y, worldCenter.z);
            if (!task.world().isLoaded(sourcePos) || claimedExternal.contains(sourcePos.asLong())
                    || isRecentlyDetachedEcho(task, sourcePos)) continue;


            CellData external = readCell(task.world(), sourcePos, api);
            if (!external.liquid() || external.amount() <= 0
                    || !isSupportedFluid(external.fluidType(), api)
                    || !isInletColumnCompatible(task, api, catchPoint.localDeck(), fillTarget,
                            external.fluidType())) continue;
            if (!cavityCatchPoint) {
                FluidState aboveSource = task.world().getFluidState(sourcePos.above());
                if (!aboveSource.isEmpty()) continue;
                if (isPartOfDeepFluidBody(task.world(), api, sourcePos, external.fluidType())) continue;
            }


            CellData currentExternal = readCell(task.world(), sourcePos, api);
            if (!currentExternal.liquid() || currentExternal.amount() <= 0
                    || !isSupportedFluid(currentExternal.fluidType(), api)
                    || !sameFluidFamily(external.fluidType(), currentExternal.fluidType())) continue;

            int sourceUnitsBefore = currentExternal.amount();
            int totalMoved = 0;
            CellPos layerTarget = fillTarget;
            int maxLayers = cavityCatchPoint ? INLET_MAX_FILL_HEIGHT : 1;
            for (int layer = 0; layer < maxLayers && totalMoved < sourceUnitsBefore; layer++) {
                if (layer > 0) {
                    layerTarget = findInletFillTarget(task, api, catchPoint.localDeck(), true);
                }
                if (layerTarget == null || lockedTargets.contains(layerTarget)
                        || !task.localBounds().contains(layerTarget)
                        || (!cavityCatchPoint && task.localBounds().isBoundary(layerTarget))) break;

                CellData localTarget = readCell(task.accessor(), toBlockPos(layerTarget), api);
                if ((!localTarget.air()
                        && (!localTarget.liquid() || localTarget.amount() >= 8))) break;
                if (localTarget.liquid()
                        && !sameFluidFamily(currentExternal.fluidType(), localTarget.fluidType())) break;

                int remainingSourceUnits = sourceUnitsBefore - totalMoved;
                int movedLayer = moveUnits(task.world(), sourcePos,
                        task.accessor(), toBlockPos(layerTarget), currentExternal.fluidType(),
                        remainingSourceUnits, api, false, true);
                if (movedLayer <= 0) break;

                totalMoved += movedLayer;
                lockedTargets.add(layerTarget);
            }
            if (totalMoved <= 0) continue;

            claimedExternal.add(sourcePos.asLong());
            movedThisSublevel++;
        }
    }

    private static boolean isInletColumnCompatible(SublevelTask task, ApiAccess api,
                                                    CellPos localDeck, CellPos fillTarget,
                                                    Fluid expectedFluid) {
        CellPos cursor = localDeck.offset(LOCAL_UP);
        for (int i = 0; i < INLET_MAX_FILL_HEIGHT && task.localBounds().contains(cursor); i++) {
            CellData data = readCell(task.accessor(), toBlockPos(cursor), api);
            if (cursor.equals(fillTarget)) {
                return data.air() || (data.liquid() && data.amount() < 8
                        && sameFluidFamily(expectedFluid, data.fluidType()));
            }
            if (!data.liquid() || data.amount() < 8
                    || !sameFluidFamily(expectedFluid, data.fluidType())) return false;
            cursor = cursor.offset(LOCAL_UP);
        }
        return false;
    }

    private static boolean isPartOfDeepFluidBody(ServerLevel world, ApiAccess api,
                                                       BlockPos sourcePos, Fluid expectedFluid) {
        BlockPos.MutableBlockPos cursor = sourcePos.mutable();
        for (int i = 0; i < INLET_AMBIENT_BODY_DEPTH; i++) {
            if (!world.isLoaded(cursor)) return false;
            FluidState fluid = world.getFluidState(cursor);
            if (fluid.isEmpty() || !isSupportedFluid(fluid.getType(), api)
                    || !sameFluidFamily(expectedFluid, fluid.getType())) return false;
            cursor.move(net.minecraft.core.Direction.DOWN);
        }
        return true;
    }

    private static CellPos findInletFillTarget(SublevelTask task, ApiAccess api,
                                                     CellPos localDeck, boolean requireContainedColumn) {
        CellPos cursor = localDeck.offset(LOCAL_UP);
        for (int i = 0; i < INLET_MAX_FILL_HEIGHT; i++) {
            if (!task.localBounds().contains(cursor)
                    || (!requireContainedColumn && task.localBounds().isBoundary(cursor))) return null;
            if (requireContainedColumn && !isHorizontallyContained(task, api, cursor)) return null;

            CellData current = readCell(task.accessor(), toBlockPos(cursor), api);
            if (current.fluidState().isEmpty()) {
                return current.blockState().isAir() ? cursor : null;
            }


            if (!current.liquid() || current.fluidType() == null
                    || !api.isManagedFluid(current.fluidType())) return null;
            if (current.amount() < 8) return cursor;
            cursor = cursor.offset(LOCAL_UP);
        }
        return null;
    }

    private static boolean isHorizontallyContained(SublevelTask task, ApiAccess api,
                                                   CellPos start) {
        for (DirectionOffset direction : HORIZONTAL_DIRECTIONS) {
            boolean wallFound = false;
            CellPos cursor = start;
            for (int distance = 0; distance < INLET_MAX_CONTAINMENT_DISTANCE; distance++) {
                cursor = cursor.offset(direction);
                if (!task.localBounds().contains(cursor)) break;
                CellData data = readCell(task.accessor(), toBlockPos(cursor), api);
                if (!data.blockState().isAir() && data.fluidState().isEmpty()) {
                    wallFound = true;
                    break;
                }
            }
            if (!wallFound) return false;
        }
        return true;
    }

    private static BlockPos toBlockPos(CellPos pos) {
        return new BlockPos(pos.x(), pos.y(), pos.z());
    }


    private static List<WorldCellCandidate> worldCellsForLocalTransition(SublevelTask task,
                                                                           CellPos fromLocal,
                                                                           CellPos toLocal) {
        Vec3 fromCenter = transformedCellCenter(task, fromLocal);
        Vec3 toCenter = transformedCellCenter(task, toLocal);
        if (fromCenter == null || toCenter == null) return List.of();

        BlockPos fromBlock = BlockPos.containing(fromCenter.x, fromCenter.y, fromCenter.z);
        BlockPos toBlock = BlockPos.containing(toCenter.x, toCenter.y, toCenter.z);


        if (task.world().hasChunk(toBlock.getX() >> 4, toBlock.getZ() >> 4)
                && (fromLocal.equals(toLocal) || !toBlock.equals(fromBlock))) {
            Vec3 projectedPosition = inverseTransformedLocalPosition(task,
                    new Vec3(toBlock.getX() + 0.5, toBlock.getY() + 0.5, toBlock.getZ() + 0.5));
            CellPos projected = localCellFromPosition(projectedPosition);
            if (toLocal.equals(projected)) {
                double dx = toBlock.getX() + 0.5 - toCenter.x;
                double dy = toBlock.getY() + 0.5 - toCenter.y;
                double dz = toBlock.getZ() + 0.5 - toCenter.z;
                return List.of(new WorldCellCandidate(toBlock.immutable(),
                        dx * dx + dy * dy + dz * dz));
            }
        }


        Set<Long> seen = new HashSet<>();
        LinkedHashSet<BlockPos> probes = new LinkedHashSet<>();
        probes.addAll(centerAndNeighbors(toBlock));
        probes.addAll(centerAndNeighbors(fromBlock));
        List<WorldCellCandidate> result = new ArrayList<>(54);
        for (BlockPos probe : probes) {
            if (!seen.add(probe.asLong())
                    || (!fromLocal.equals(toLocal) && probe.equals(fromBlock))
                    || !task.world().hasChunk(probe.getX() >> 4, probe.getZ() >> 4)) continue;
            Vec3 projectedPosition = inverseTransformedLocalPosition(task,
                    new Vec3(probe.getX() + 0.5, probe.getY() + 0.5, probe.getZ() + 0.5));
            CellPos projectedCell = localCellFromPosition(projectedPosition);
            if (projectedPosition == null) continue;

            boolean targetSide;
            if (fromLocal.equals(toLocal)) {


                targetSide = projectedCell.equals(toLocal);
            } else {
                targetSide = isOnTargetSide(projectedPosition, fromLocal, toLocal);
            }
            if (!targetSide) continue;

            double dx = probe.getX() + 0.5 - toCenter.x;
            double dy = probe.getY() + 0.5 - toCenter.y;
            double dz = probe.getZ() + 0.5 - toCenter.z;
            result.add(new WorldCellCandidate(probe.immutable(),
                    dx * dx + dy * dy + dz * dz));
        }
        result.sort(Comparator.comparingDouble(WorldCellCandidate::distanceSq));
        return result;
    }

    private static boolean isOnTargetSide(Vec3 localPosition, CellPos fromLocal, CellPos toLocal) {
        double dx = toLocal.x() - fromLocal.x();
        double dy = toLocal.y() - fromLocal.y();
        double dz = toLocal.z() - fromLocal.z();
        double deltaLengthSq = dx * dx + dy * dy + dz * dz;
        if (deltaLengthSq < 0.5) return false;

        double fromX = fromLocal.x() + 0.5;
        double fromY = fromLocal.y() + 0.5;
        double fromZ = fromLocal.z() + 0.5;
        double toX = toLocal.x() + 0.5;
        double toY = toLocal.y() + 0.5;
        double toZ = toLocal.z() + 0.5;
        double midpointX = (fromX + toX) * 0.5;
        double midpointY = (fromY + toY) * 0.5;
        double midpointZ = (fromZ + toZ) * 0.5;
        double side = (localPosition.x - midpointX) * dx
                + (localPosition.y - midpointY) * dy
                + (localPosition.z - midpointZ) * dz;
        if (side < -1.0e-7) return false;


        double targetDx = localPosition.x - toX;
        double targetDy = localPosition.y - toY;
        double targetDz = localPosition.z - toZ;
        double along = (targetDx * dx + targetDy * dy + targetDz * dz) / deltaLengthSq;
        double perpendicularSq = targetDx * targetDx + targetDy * targetDy + targetDz * targetDz
                - along * along * deltaLengthSq;
        return perpendicularSq <= 1.0;
    }

    private static CellPos localCellFromPosition(Vec3 localPosition) {
        if (localPosition == null) return null;
        return new CellPos(
                (int) Math.floor(localPosition.x + 1.0e-7),
                (int) Math.floor(localPosition.y + 1.0e-7),
                (int) Math.floor(localPosition.z + 1.0e-7));
    }

    private static void markRecentlyDetachedSurfaceCell(SublevelTask task, BlockPos worldDestination) {
        String key = surfaceCacheKey(task);
        RECENTLY_DETACHED_WORLD_CELLS
                .computeIfAbsent(key, ignored -> new ConcurrentHashMap<>())
                .put(worldDestination.asLong(), new DetachedSurfaceMark(worldDestination.immutable(), task.world().getGameTime()));
    }


    private static boolean isRecentlyDetachedEcho(SublevelTask task, BlockPos externalSource) {
        String key = surfaceCacheKey(task);
        Map<Long, DetachedSurfaceMark> marks = RECENTLY_DETACHED_WORLD_CELLS.get(key);
        if (marks == null || marks.isEmpty()) return false;

        long now = task.world().getGameTime();
        DetachedSurfaceMark match = null;
        for (Map.Entry<Long, DetachedSurfaceMark> entry : marks.entrySet()) {
            DetachedSurfaceMark mark = entry.getValue();
            long age = now - mark.gameTime();
            if (age < 0L || age > DETACHED_WORLD_CELL_COOLDOWN_TICKS) {
                marks.remove(entry.getKey(), mark);
                continue;
            }

            BlockPos spill = mark.worldPos();
            if (Math.abs(externalSource.getX() - spill.getX()) <= 1
                    && Math.abs(externalSource.getY() - spill.getY()) <= 1
                    && Math.abs(externalSource.getZ() - spill.getZ()) <= 1) {
                match = mark;
                break;
            }
        }
        return match != null;
    }

    private static LocalBounds deriveHullBounds(Map<CellPos, CellData> cells) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        boolean foundSolid = false;
        for (Map.Entry<CellPos, CellData> entry : cells.entrySet()) {


            if (isPassable(entry.getValue())) continue;
            CellPos pos = entry.getKey();
            foundSolid = true;
            minX = Math.min(minX, pos.x()); minY = Math.min(minY, pos.y()); minZ = Math.min(minZ, pos.z());
            maxX = Math.max(maxX, pos.x()); maxY = Math.max(maxY, pos.y()); maxZ = Math.max(maxZ, pos.z());
        }
        return foundSolid ? new LocalBounds(minX, minY, minZ, maxX, maxY, maxZ) : null;
    }


    private static boolean isPassable(CellData cell) {
        return cell != null && (cell.air() || cell.liquid());
    }

    private static CellData readCell(LevelAccessor level, BlockPos pos, ApiAccess api) {
        BlockState block = level.getBlockState(pos);
        FluidState fluid = level.getFluidState(pos);
        boolean liquid = !fluid.isEmpty()
                && isSupportedFluid(fluid.getType(), api)
                && block.getBlock() == fluid.createLegacyBlock().getBlock();
        boolean air = fluid.isEmpty() && block.isAir();
        int amount = liquid ? Math.clamp(fluid.getAmount(), 0, 8) : 0;
        return new CellData(block, fluid, liquid, air, amount, liquid ? fluid.getType() : null);
    }

    private static int moveUnits(LevelAccessor sourceLevel, BlockPos sourcePos,
                                 LevelAccessor targetLevel, BlockPos targetPos,
                                 Fluid sourceFluid, int requestedUnits, ApiAccess api,
                                 boolean sourceIsPlot, boolean targetIsPlot) {
        trace("MOVE_BEGIN", "requestedFluid=" + fluidId(sourceFluid) + ",requestedUnits=" + requestedUnits
                + ",sourceIsPlot=" + sourceIsPlot + ",targetIsPlot=" + targetIsPlot
                + ",source=" + cellDescription(sourceLevel, sourcePos)
                + ",target=" + cellDescription(targetLevel, targetPos));
        if (sourceLevel == targetLevel && sourcePos.equals(targetPos)) {
            trace("MOVE_REJECT", "reason=same-cell,source=" + cellDescription(sourceLevel, sourcePos));
            return 0;
        }
        BlockState sourceBlock = sourceLevel.getBlockState(sourcePos);
        FluidState sourceState = sourceLevel.getFluidState(sourcePos);
        if (sourceState.isEmpty()
                || sourceBlock.getBlock() != sourceState.createLegacyBlock().getBlock()
                || !isSupportedFluid(sourceState.getType(), api)
                || !sameFluidFamily(sourceFluid, sourceState.getType())) {
            trace("MOVE_REJECT", "reason=source-invalid,requestedFluid=" + fluidId(sourceFluid)
                    + ",requestedUnits=" + requestedUnits + ",sourceIsPlot=" + sourceIsPlot
                    + ",source=" + cellDescription(sourceLevel, sourcePos));
            return 0;
        }
        int sourceAmount = Math.clamp(sourceState.getAmount(), 0, 8);
        if (sourceAmount <= 0) {
            trace("MOVE_REJECT", "reason=source-empty,source=" + cellDescription(sourceLevel, sourcePos));
            return 0;
        }

        BlockState targetBlock = targetLevel.getBlockState(targetPos);
        FluidState targetState = targetLevel.getFluidState(targetPos);
        int targetAmount = 0;
        Fluid targetFluid = sourceState.getType();
        if (!targetState.isEmpty()) {
            if (targetBlock.getBlock() != targetState.createLegacyBlock().getBlock()
                    || !isSupportedFluid(targetState.getType(), api)
                    || !sameFluidFamily(sourceState.getType(), targetState.getType())) {
                trace("MOVE_REJECT", "reason=target-fluid-conflict,sourceIsPlot=" + sourceIsPlot
                        + ",targetIsPlot=" + targetIsPlot + ",sourceFluid=" + fluidId(sourceState.getType())
                        + ",targetFluid=" + fluidId(targetState.getType())
                        + ",source=" + cellDescription(sourceLevel, sourcePos)
                        + ",target=" + cellDescription(targetLevel, targetPos));
                return 0;
            }
            targetAmount = Math.clamp(targetState.getAmount(), 0, 8);
            targetFluid = targetState.getType();
        } else if (!targetBlock.isAir()) {
            trace("MOVE_REJECT", "reason=target-solid,sourceIsPlot=" + sourceIsPlot
                    + ",targetIsPlot=" + targetIsPlot + ",source=" + cellDescription(sourceLevel, sourcePos)
                    + ",target=" + cellDescription(targetLevel, targetPos));
            return 0;
        }

        int units = Math.min(Math.min(requestedUnits, sourceAmount), 8 - targetAmount);
        if (units <= 0) {
            trace("MOVE_REJECT", "reason=no-capacity,requestedUnits=" + requestedUnits
                    + ",sourceAmount=" + sourceAmount + ",targetAmount=" + targetAmount
                    + ",source=" + cellDescription(sourceLevel, sourcePos)
                    + ",target=" + cellDescription(targetLevel, targetPos));
            return 0;
        }
        int newTargetAmount = targetAmount + units;
        int newSourceAmount = sourceAmount - units;
        String sourceBeforeDescription = cellDescription(sourceLevel, sourcePos);
        String targetBeforeDescription = cellDescription(targetLevel, targetPos);
        int totalBefore = sourceAmount + targetAmount;
        int totalExpectedAfter = newSourceAmount + newTargetAmount;
        List<FluidCellSnapshot> sourceInteractionSnapshot = snapshotInteractionNeighborhood(sourceLevel, sourcePos);
        List<FluidCellSnapshot> targetInteractionSnapshot = snapshotInteractionNeighborhood(targetLevel, targetPos);

        if (!api.setAmountQuietVerified(targetLevel, targetPos, targetFluid, newTargetAmount)) {
            if (commitFluidInteractionResult(sourceLevel, sourcePos, sourceState.getType(), sourceAmount,
                    newSourceAmount, targetLevel, targetPos, targetFluid, targetAmount,
                    newTargetAmount, units, api, sourceIsPlot, targetIsPlot,
                    sourceInteractionSnapshot, targetInteractionSnapshot)) {
                return units;
            }
            boolean rollback = api.setAmountQuiet(targetLevel, targetPos, targetFluid, targetAmount);
            trace("MOVE_ABORT", "stage=write-target,rollback=" + rollback + ",fluid=" + fluidId(targetFluid)
                    + ",units=" + units + ",source=" + cellDescription(sourceLevel, sourcePos)
                    + ",target=" + cellDescription(targetLevel, targetPos));
            return 0;
        }
        if (!api.setAmountQuietVerified(sourceLevel, sourcePos, sourceState.getType(), newSourceAmount)) {
            if (commitFluidInteractionResult(sourceLevel, sourcePos, sourceState.getType(), sourceAmount,
                    newSourceAmount, targetLevel, targetPos, targetFluid, targetAmount,
                    newTargetAmount, units, api, sourceIsPlot, targetIsPlot,
                    sourceInteractionSnapshot, targetInteractionSnapshot)) {
                return units;
            }
            boolean sourceRollback = api.setAmountQuiet(sourceLevel, sourcePos, sourceState.getType(), sourceAmount);
            boolean targetRollback = api.setAmountQuiet(targetLevel, targetPos, targetFluid, targetAmount);
            trace("MOVE_ABORT", "stage=write-source,sourceRollback=" + sourceRollback
                    + ",targetRollback=" + targetRollback + ",fluid=" + fluidId(sourceState.getType())
                    + ",units=" + units + ",source=" + cellDescription(sourceLevel, sourcePos)
                    + ",target=" + cellDescription(targetLevel, targetPos));
            return 0;
        }
        if (!amountMatches(sourceLevel, sourcePos, sourceState.getType(), newSourceAmount)
                || !amountMatches(targetLevel, targetPos, targetFluid, newTargetAmount)) {
            if (commitFluidInteractionResult(sourceLevel, sourcePos, sourceState.getType(), sourceAmount,
                    newSourceAmount, targetLevel, targetPos, targetFluid, targetAmount,
                    newTargetAmount, units, api, sourceIsPlot, targetIsPlot,
                    sourceInteractionSnapshot, targetInteractionSnapshot)) {
                return units;
            }
            boolean sourceRollback = api.setAmountQuiet(sourceLevel, sourcePos, sourceState.getType(), sourceAmount);
            boolean targetRollback = api.setAmountQuiet(targetLevel, targetPos, targetFluid, targetAmount);
            trace("MOVE_ABORT", "stage=post-write-verification,sourceRollback=" + sourceRollback
                    + ",targetRollback=" + targetRollback + ",fluid=" + fluidId(sourceState.getType())
                    + ",units=" + units + ",source=" + cellDescription(sourceLevel, sourcePos)
                    + ",target=" + cellDescription(targetLevel, targetPos));
            return 0;
        }

        FluidState afterSourceState = sourceLevel.getFluidState(sourcePos);
        FluidState afterTargetState = targetLevel.getFluidState(targetPos);
        int actualSourceAmount = afterSourceState.isEmpty() ? 0 : afterSourceState.getAmount();
        int actualTargetAmount = afterTargetState.isEmpty() ? 0 : afterTargetState.getAmount();
        int totalActualAfter = actualSourceAmount + actualTargetAmount;
        trace("MOVE_COMMIT", "fluid=" + fluidId(sourceState.getType()) + ",units=" + units
                + ",sourceIsPlot=" + sourceIsPlot + ",targetIsPlot=" + targetIsPlot
                + ",totalBefore=" + totalBefore + ",totalExpectedAfter=" + totalExpectedAfter
                + ",totalActualAfter=" + totalActualAfter
                + ",sourceBefore={" + sourceBeforeDescription + "}"
                + ",sourceExpectedAmountAfter=" + newSourceAmount
                + ",targetBefore={" + targetBeforeDescription + "}"
                + ",targetExpectedAmountAfter=" + newTargetAmount
                + ",sourceAfter=" + cellDescription(sourceLevel, sourcePos)
                + ",targetAfter=" + cellDescription(targetLevel, targetPos));

        if (!sourceIsPlot && sourceLevel instanceof Level sourceWorld) {
            markWorldTracePosition(sourceWorld, sourcePos);
        }
        if (!targetIsPlot && targetLevel instanceof Level targetWorld) {
            markWorldTracePosition(targetWorld, targetPos);
        }


        if (!sourceIsPlot) {
            FluidState afterSource = sourceLevel.getFluidState(sourcePos);
            boolean deferIntakeSourceUpdate = targetIsPlot && newSourceAmount > 0;
            if (!deferIntakeSourceUpdate) {
                notifyExternalFluidChange(sourceLevel, sourcePos,
                        afterSource.isEmpty() ? null : afterSource.getType());
            }
        }
        if (!targetIsPlot) {
            FluidState afterTarget = targetLevel.getFluidState(targetPos);
            notifyExternalFluidChange(targetLevel, targetPos, afterTarget.isEmpty() ? null : afterTarget.getType());
        }
        return units;
    }

    private static List<FluidCellSnapshot> snapshotInteractionNeighborhood(LevelAccessor level, BlockPos center) {
        List<FluidCellSnapshot> snapshots = new ArrayList<>(7);
        addFluidCellSnapshot(snapshots, level, center);
        for (DirectionOffset direction : DIRECTIONS) {
            addFluidCellSnapshot(snapshots, level,
                    center.offset(direction.dx(), direction.dy(), direction.dz()));
        }
        return snapshots;
    }

    private static void addFluidCellSnapshot(List<FluidCellSnapshot> snapshots,
                                             LevelAccessor level, BlockPos pos) {
        BlockPos immutable = pos.immutable();
        snapshots.add(new FluidCellSnapshot(level, immutable,
                level.getBlockState(immutable), level.getFluidState(immutable)));
    }

    private static boolean commitFluidInteractionResult(
            LevelAccessor sourceLevel, BlockPos sourcePos, Fluid sourceFluid,
            int sourceBeforeAmount, int sourceAfterAmount,
            LevelAccessor targetLevel, BlockPos targetPos, Fluid targetFluid,
            int targetBeforeAmount, int targetAfterAmount, int movedUnits, ApiAccess api,
            boolean sourceIsPlot, boolean targetIsPlot,
            List<FluidCellSnapshot> sourceSnapshots, List<FluidCellSnapshot> targetSnapshots) {
        FluidInteractionResult result = findFluidInteractionResult(sourceSnapshots, targetSnapshots,
                sourceLevel, sourcePos, sourceFluid, sourceAfterAmount,
                targetLevel, targetPos, targetFluid, targetAfterAmount);
        if (result == null) return false;

        boolean sourceApplied = amountMatches(sourceLevel, sourcePos, sourceFluid, sourceAfterAmount)
                || hasInteractionResultAt(sourceSnapshots, sourceLevel, sourcePos,
                sourceLevel, sourcePos, sourceFluid, sourceAfterAmount,
                targetLevel, targetPos, targetFluid, targetAfterAmount);
        if (!sourceApplied) {
            api.setAmountQuietVerified(sourceLevel, sourcePos, sourceFluid, sourceAfterAmount);
            sourceApplied = amountMatches(sourceLevel, sourcePos, sourceFluid, sourceAfterAmount)
                    || hasInteractionResultAt(sourceSnapshots, sourceLevel, sourcePos,
                    sourceLevel, sourcePos, sourceFluid, sourceAfterAmount,
                    targetLevel, targetPos, targetFluid, targetAfterAmount);
            result = findFluidInteractionResult(sourceSnapshots, targetSnapshots,
                    sourceLevel, sourcePos, sourceFluid, sourceAfterAmount,
                    targetLevel, targetPos, targetFluid, targetAfterAmount);
        }

        boolean targetApplied = amountMatches(targetLevel, targetPos, targetFluid, targetAfterAmount)
                || hasAnyInteractionResult(targetSnapshots, sourceSnapshots,
                sourceLevel, sourcePos, sourceFluid, sourceAfterAmount,
                targetLevel, targetPos, targetFluid, targetAfterAmount);
        if (!sourceApplied || !targetApplied || result == null) return false;

        trace("FLUID_INTERACTION_COMMIT", "movedUnits=" + movedUnits
                + ",sourceFluid=" + fluidId(sourceFluid) + ",targetFluid=" + fluidId(targetFluid)
                + ",sourceBeforeAmount=" + sourceBeforeAmount + ",sourceExpectedAfter=" + sourceAfterAmount
                + ",targetBeforeAmount=" + targetBeforeAmount + ",targetExpectedAfter=" + targetAfterAmount
                + ",sourceIsPlot=" + sourceIsPlot + ",targetIsPlot=" + targetIsPlot
                + ",resultLevel=" + levelId(result.level()) + ",resultPos=" + result.pos()
                + ",resultBeforeBlock=" + blockId(result.beforeBlock())
                + ",resultBeforeFluid=" + (result.beforeFluid().isEmpty() ? "none" : fluidId(result.beforeFluid().getType()))
                + ",resultAfterBlock=" + blockId(result.afterBlock())
                + ",resultAfterFluid=" + (result.afterFluid().isEmpty() ? "none"
                : fluidId(result.afterFluid().getType()) + ":amount=" + result.afterFluid().getAmount())
                + ",source=" + cellDescription(sourceLevel, sourcePos)
                + ",target=" + cellDescription(targetLevel, targetPos));
        finishCommittedMove(sourceLevel, sourcePos, targetLevel, targetPos,
                sourceIsPlot, targetIsPlot, sourceAfterAmount);
        return true;
    }

    private static void finishCommittedMove(
            LevelAccessor sourceLevel, BlockPos sourcePos,
            LevelAccessor targetLevel, BlockPos targetPos,
            boolean sourceIsPlot, boolean targetIsPlot, int sourceAfterAmount) {
        if (!sourceIsPlot && sourceLevel instanceof Level sourceWorld) {
            markWorldTracePosition(sourceWorld, sourcePos);
        }
        if (!targetIsPlot && targetLevel instanceof Level targetWorld) {
            markWorldTracePosition(targetWorld, targetPos);
        }

        if (!sourceIsPlot) {
            FluidState afterSource = sourceLevel.getFluidState(sourcePos);
            if (!(targetIsPlot && sourceAfterAmount > 0)) {
                notifyExternalFluidChange(sourceLevel, sourcePos,
                        afterSource.isEmpty() ? null : afterSource.getType());
            }
        }
        if (!targetIsPlot) {
            FluidState afterTarget = targetLevel.getFluidState(targetPos);
            notifyExternalFluidChange(targetLevel, targetPos,
                    afterTarget.isEmpty() ? null : afterTarget.getType());
        }
    }

    private static FluidInteractionResult findFluidInteractionResult(
            List<FluidCellSnapshot> sourceSnapshots, List<FluidCellSnapshot> targetSnapshots,
            LevelAccessor sourceLevel, BlockPos sourcePos, Fluid sourceFluid, int sourceAfterAmount,
            LevelAccessor targetLevel, BlockPos targetPos, Fluid targetFluid, int targetAfterAmount) {
        for (List<FluidCellSnapshot> snapshots : List.of(sourceSnapshots, targetSnapshots)) {
            for (FluidCellSnapshot before : snapshots) {
                BlockState afterBlock = before.level().getBlockState(before.pos());
                FluidState afterFluid = before.level().getFluidState(before.pos());
                if (isExpectedInteractionCell(before,
                        sourceLevel, sourcePos, sourceFluid, sourceAfterAmount,
                        targetLevel, targetPos, targetFluid, targetAfterAmount)) continue;
                if (!isInteractionResultTransition(before, afterBlock, afterFluid, sourceFluid, targetFluid)) continue;
                return new FluidInteractionResult(before.level(), before.pos(), before.blockState(),
                        before.fluidState(), afterBlock, afterFluid);
            }
        }
        return null;
    }

    private static boolean hasInteractionResultAt(
            List<FluidCellSnapshot> snapshots, LevelAccessor resultLevel, BlockPos resultPos,
            LevelAccessor sourceLevel, BlockPos sourcePos, Fluid sourceFluid, int sourceAfterAmount,
            LevelAccessor targetLevel, BlockPos targetPos, Fluid targetFluid, int targetAfterAmount) {
        for (FluidCellSnapshot before : snapshots) {
            if (before.level() != resultLevel || !before.pos().equals(resultPos)) continue;
            BlockState afterBlock = before.level().getBlockState(before.pos());
            FluidState afterFluid = before.level().getFluidState(before.pos());
            if (isExpectedInteractionCell(before,
                    sourceLevel, sourcePos, sourceFluid, sourceAfterAmount,
                    targetLevel, targetPos, targetFluid, targetAfterAmount)) return false;
            return isInteractionResultTransition(before, afterBlock, afterFluid, sourceFluid, targetFluid);
        }
        return false;
    }

    private static boolean hasAnyInteractionResult(
            List<FluidCellSnapshot> first, List<FluidCellSnapshot> second,
            LevelAccessor sourceLevel, BlockPos sourcePos, Fluid sourceFluid, int sourceAfterAmount,
            LevelAccessor targetLevel, BlockPos targetPos, Fluid targetFluid, int targetAfterAmount) {
        return findFluidInteractionResult(first, second, sourceLevel, sourcePos, sourceFluid, sourceAfterAmount,
                targetLevel, targetPos, targetFluid, targetAfterAmount) != null;
    }

    private static boolean isExpectedInteractionCell(
            FluidCellSnapshot before,
            LevelAccessor sourceLevel, BlockPos sourcePos, Fluid sourceFluid, int sourceAfterAmount,
            LevelAccessor targetLevel, BlockPos targetPos, Fluid targetFluid, int targetAfterAmount) {
        if (before.level() == sourceLevel && before.pos().equals(sourcePos)
                && amountMatches(sourceLevel, sourcePos, sourceFluid, sourceAfterAmount)) return true;
        return before.level() == targetLevel && before.pos().equals(targetPos)
                && amountMatches(targetLevel, targetPos, targetFluid, targetAfterAmount);
    }

    private static boolean isInteractionResultTransition(FluidCellSnapshot before,
                                                         BlockState afterBlock, FluidState afterFluid,
                                                         Fluid sourceFluid, Fluid targetFluid) {
        if (before.blockState().equals(afterBlock) && before.fluidState().equals(afterFluid)) return false;

        if (afterFluid.isEmpty()) {
            if (!afterBlock.isAir()) {
                return before.blockState().isAir() || !before.fluidState().isEmpty();
            }
            return !before.fluidState().isEmpty();
        }

        if (!before.fluidState().isEmpty()
                && !sameFluidFamily(before.fluidState().getType(), afterFluid.getType())) {
            return true;
        }
        if (before.fluidState().isEmpty()
                && !sameFluidFamily(afterFluid.getType(), sourceFluid)
                && !sameFluidFamily(afterFluid.getType(), targetFluid)
                && afterBlock.getBlock() == afterFluid.createLegacyBlock().getBlock()) {
            return true;
        }
        return afterBlock.getBlock() != afterFluid.createLegacyBlock().getBlock();
    }

    private record FluidCellSnapshot(LevelAccessor level, BlockPos pos,
                                     BlockState blockState, FluidState fluidState) {
    }

    private record FluidInteractionResult(LevelAccessor level, BlockPos pos,
                                          BlockState beforeBlock, FluidState beforeFluid,
                                          BlockState afterBlock, FluidState afterFluid) {
    }

    private static boolean amountMatches(LevelAccessor level, BlockPos pos, Fluid fluid, int amount) {
        FluidState actual = level.getFluidState(pos);
        if (amount <= 0) return actual.isEmpty() && level.getBlockState(pos).isAir();
        return !actual.isEmpty()
                && actual.getType() instanceof FlowingFluid
                && sameFluidFamily(fluid, actual.getType())
                && actual.getAmount() == amount
                && level.getBlockState(pos).getBlock() == actual.createLegacyBlock().getBlock();
    }

    private static void notifyExternalFluidChange(LevelAccessor level, BlockPos pos, Fluid fluid) {
        if (fluid != null) {
            trace("EXTERNAL_TICK_SCHEDULE", "delay=1,fluid=" + fluidId(fluid)
                    + "," + cellDescription(level, pos));
            try {

                level.scheduleTick(pos, fluid, 1);
            } catch (Throwable failure) {
                MSpellCompatDebug.error("sable_transport", "external_tick_schedule_failed", failure,
                        "level=" + levelId(level) + ",pos=" + pos + ",fluid=" + fluidId(fluid));
            }
        }
        try {
            java.lang.reflect.Method update = level.getClass().getMethod("updateNeighborsAt", BlockPos.class,
                    net.minecraft.world.level.block.Block.class);
            net.minecraft.world.level.block.Block updateBlock = fluid != null
                    ? fluid.defaultFluidState().createLegacyBlock().getBlock()
                    : level.getBlockState(pos).getBlock();
            update.invoke(level, pos, updateBlock);
        } catch (Throwable failure) {
            MSpellCompatDebug.error("sable_transport", "neighbor_update_failed", failure,
                    "level=" + levelId(level) + ",pos=" + pos + ",fluid=" + fluidId(fluid));
        }
    }

    private static boolean canReceiveFromSource(CellData target, Fluid sourceFluid, ApiAccess api) {
        if (target == null) return false;
        if (target.air()) return true;
        return target.liquid() && target.amount() < 8 && target.fluidType() != null
                && api.isManagedFluid(target.fluidType())
                && sameFluidFamily(sourceFluid, target.fluidType());
    }


    private static List<BlockPos> centerAndNeighbors(BlockPos center) {
        List<BlockPos> result = new ArrayList<>(27);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    result.add(center.offset(dx, dy, dz).immutable());
                }
            }
        }
        return result;
    }

    private static Vec3 transformedCellCenter(SublevelTask task, CellPos cell) {


        BlockPos center = task.center();
        return transformedPosition(task.pose(), new Vec3(
                center.getX() + cell.x() + 0.5,
                center.getY() + cell.y() + 0.5,
                center.getZ() + cell.z() + 0.5));
    }

    private static Vec3 transformedPosition(Object pose, Vec3 plotPosition) {
        return invokePoseVector(pose, plotPosition, "transformPosition", POSE_TRANSFORM_METHODS);
    }

    private static Vec3 inverseTransformedPosition(Object pose, Vec3 world) {
        return invokePoseVector(pose, world, "transformPositionInverse", POSE_INVERSE_TRANSFORM_METHODS);
    }


    private static Vec3 inverseTransformedLocalPosition(SublevelTask task, Vec3 world) {
        Vec3 plotPosition = inverseTransformedPosition(task.pose(), world);
        if (plotPosition == null) return null;
        BlockPos center = task.center();
        return new Vec3(
                plotPosition.x - center.getX(),
                plotPosition.y - center.getY(),
                plotPosition.z - center.getZ());
    }

    private static Vec3 invokePoseVector(Object pose, Vec3 vector, String methodName,
                                         Map<Class<?>, Method> cache) {
        if (pose == null || vector == null) return null;
        try {
            Class<?> type = pose.getClass();
            Method method = cache.get(type);
            if (method == null) {
                Method found = type.getMethod(methodName, Vec3.class);
                Method previous = cache.putIfAbsent(type, found);
                method = previous == null ? found : previous;
            }
            Object result = method.invoke(pose, vector);
            return result instanceof Vec3 vec ? vec : null;
        } catch (Throwable failure) {
            MSpellCompatDebug.error("sable_transport", "pose_vector_invoke_failed", failure,
                    "pose=" + pose.getClass().getName() + ",method=" + methodName + ",vector=" + vector);
            return null;
        }
    }

    private static Vec3 localGravityDown(Object pose) {
        Vec3 down = invokePoseVector(pose, new Vec3(0.0, -1.0, 0.0),
                "transformNormalInverse", POSE_INVERSE_NORMAL_METHODS);
        if (down == null) return null;
        double length = down.length();
        return length < 1.0e-9 ? null : down.scale(1.0 / length);
    }

    private static double gravityProjection(Vec3 gravityDown, CellPos pos) {
        return gravityDown.x * pos.x() + gravityDown.y * pos.y() + gravityDown.z * pos.z();
    }

    private static ApiAccess apiAccess() {
        if (apiLookupAttempted) return cachedApi;
        synchronized (SableFluidTransport.class) {
            if (apiLookupAttempted) return cachedApi;
            apiLookupAttempted = true;
            if (!ModList.get().isLoaded("flowing_fluids")) return null;
            try {
                Class<?> type = Class.forName(FF_API_CLASS, true, SableFluidTransport.class.getClassLoader());
                Object api = type.getMethod("getInstance", String.class).invoke(null, FF_CLIENT_NAME);
                Class<?> ffUtils = Class.forName("traben.flowing_fluids.FFFluidUtils", true,
                        SableFluidTransport.class.getClassLoader());
                cachedApi = new ApiAccess(
                        api,
                        type.getMethod("isModEnabled"),
                        type.getMethod("doesModifyThisFluid", Fluid.class),
                        ffUtils.getMethod("getBlockForFluidByAmount", Fluid.class, int.class));
            } catch (Throwable failure) {
                MSpellCompatDebug.error("sable_transport", "flowing_fluids_api_bind_failed", failure,
                        "apiClass=" + FF_API_CLASS);
                cachedApi = null;
            }
            return cachedApi;
        }
    }

    private static boolean isSupportedFluid(Fluid fluid, ApiAccess api) {
        return fluid instanceof FlowingFluid && api.isManagedFluid(fluid);
    }

    private static boolean sameFluidFamily(Fluid first, Fluid second) {
        return first != null && second != null && sourceFluid(first) == sourceFluid(second);
    }

    private static Fluid sourceFluid(Fluid fluid) {
        return fluid instanceof FlowingFluid flowing ? flowing.getSource() : fluid;
    }

    private static Method sableContainerMethod(Class<?> containerClass) throws NoSuchMethodException {
        Method cached = SABLE_CONTAINER_METHODS.get(containerClass);
        if (cached != null) return cached;
        Method resolved = containerClass.getMethod("getContainer", ServerLevel.class);
        Method previous = SABLE_CONTAINER_METHODS.putIfAbsent(containerClass, resolved);
        return previous == null ? resolved : previous;
    }

    private static Object invoke(Object target, String methodName) throws ReflectiveOperationException {
        if (target == null) throw new ReflectiveOperationException("target for " + methodName + "() is null");
        Class<?> type = target.getClass();
        NoArgMethodKey key = new NoArgMethodKey(type, methodName);
        Method method = NO_ARG_METHODS.get(key);
        if (method == null) {
            Method resolved = type.getMethod(methodName);
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

    private record NoArgMethodKey(Class<?> owner, String name) {
    }

    private record DirectionOffset(int dx, int dy, int dz) {
    }

    private record DetachedSurfaceMark(BlockPos worldPos, long gameTime) {
    }

    private record CellPos(int x, int y, int z) {
        private CellPos offset(DirectionOffset direction) {
            return new CellPos(x + direction.dx(), y + direction.dy(), z + direction.dz());
        }
    }

    private record CellData(BlockState blockState, FluidState fluidState, boolean liquid,
                            boolean air, int amount, Fluid fluidType) {
    }

    private record GridNeighborCandidate(CellPos localTarget, double slope, int priority,
                                         BlockPos worldTarget, int targetAmount) {
    }

    private record InletCatchPoint(CellPos localDeck, boolean horizontallyContained) {
    }

    private record WorldCellCandidate(BlockPos worldPos, double distanceSq) {
    }

    private record LocalGridCache(List<InletCatchPoint> inletCatchPoints,
                                  LevelAccessor accessor, BlockPos center, StorageBounds storageBounds,
                                  LocalBounds localBounds, int volume) {
    }

    private record LocalBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        private boolean contains(CellPos pos) {
            return pos.x() >= minX && pos.x() <= maxX
                    && pos.y() >= minY && pos.y() <= maxY
                    && pos.z() >= minZ && pos.z() <= maxZ;
        }

        private boolean isBoundary(CellPos pos) {
            return pos.x() == minX || pos.x() == maxX
                    || pos.y() == minY || pos.y() == maxY
                    || pos.z() == minZ || pos.z() == maxZ;
        }
    }

    private record StorageBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        private StorageBounds inflate(int amount) {
            return new StorageBounds(minX - amount, minY - amount, minZ - amount,
                    maxX + amount, maxY + amount, maxZ + amount);
        }

        private boolean contains(BlockPos pos) {
            return pos.getX() >= minX && pos.getX() <= maxX
                    && pos.getY() >= minY && pos.getY() <= maxY
                    && pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }
    }

    private record SublevelTask(ServerLevel world, LevelAccessor accessor, Object pose, String id,
                                BlockPos center, StorageBounds storageBounds, LocalBounds localBounds,
                                int volume, Vec3 gravityDown) {
    }

    private static final class ApiAccess {
        private final Object api;
        private final Method isModEnabled;
        private final Method doesModifyThisFluid;
        private final Method getBlockForFluidByAmount;
        private final Map<Fluid, Boolean> managedFluidCache = new IdentityHashMap<>();

        private ApiAccess(Object api, Method isModEnabled,
                          Method doesModifyThisFluid, Method getBlockForFluidByAmount) {
            this.api = api;
            this.isModEnabled = isModEnabled;
            this.doesModifyThisFluid = doesModifyThisFluid;
            this.getBlockForFluidByAmount = getBlockForFluidByAmount;
        }


        private boolean isEnabled() {
            try {
                return Boolean.TRUE.equals(isModEnabled.invoke(api));
            } catch (Throwable throwable) {
                MSpellCompatDebug.error("sable_transport", "flowing_fluids_is_enabled_failed", throwable,
                        "api=" + api.getClass().getName());
                return false;
            }
        }

        private boolean isManagedFluid(Fluid fluid) {
            if (fluid == null) return false;
            Boolean cached = managedFluidCache.get(fluid);
            if (cached != null) return cached;
            boolean managed;
            try {
                managed = Boolean.TRUE.equals(doesModifyThisFluid.invoke(api, fluid));
            } catch (Throwable throwable) {
                MSpellCompatDebug.error("sable_transport", "flowing_fluids_is_managed_failed", throwable,
                        "fluid=" + fluidId(fluid));
                managed = false;
            }
            managedFluidCache.put(fluid, managed);
            return managed;
        }


        private boolean setAmountQuiet(LevelAccessor level, BlockPos pos, Fluid fluid, int amount) {
            try {
                if (amount <= 0) {
                    return level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
                Object rawState = getBlockForFluidByAmount.invoke(null, fluid, Math.clamp(amount, 1, 8));
                if (!(rawState instanceof BlockState state)) return false;
                return level.setBlock(pos, state, 2);
            } catch (Throwable throwable) {
                MSpellCompatDebug.error("sable_transport", "fluid_state_write_failed", throwable,
                        "level=" + levelId(level) + ",pos=" + pos + ",fluid=" + fluidId(fluid)
                                + ",amount=" + amount);
                return false;
            }
        }

        private boolean setAmountQuietVerified(LevelAccessor level, BlockPos pos, Fluid fluid, int amount) {
            return setAmountQuiet(level, pos, fluid, amount) && amountMatches(level, pos, fluid, amount);
        }
    }
}
