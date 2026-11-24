package com.misp.mspellcompat.sable;

import com.misp.mspellcompat.config.MSpellCompatConfig;
import com.misp.mspellcompat.util.MSpellCompatDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.WeakHashMap;

public final class TreePhysicsDamageBridge {
    private static final TagKey<Block> TREE_PHYSICS_LOGS = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("treephysics", "logs")
    );
    private static final ThreadLocal<Boolean> PROCESSING_TREE_PHYSICS = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Integer> TREE_PHYSICS_OPERATION_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final Map<ServerLevel, Map<Long, Long>> BLOCK_REPLACEMENT_SUPPRESSIONS = new WeakHashMap<>();
    private static final Map<ServerLevel, Long> LAST_SUPPRESSION_CLEANUP = new WeakHashMap<>();

    private static volatile boolean treePhysicsLoadedResolved;
    private static volatile boolean treePhysicsLoaded;
    private static volatile boolean apiResolved;
    private static volatile Method blockBrokenMethod;
    private static volatile Method findTreesAroundMethod;
    private static volatile Method treeResultGetBlocksMethod;
    private static volatile boolean treeLookupResolved;
    private static volatile boolean apiAvailable;

    private TreePhysicsDamageBridge() {
    }

    public static void onPlayerBreak(BlockEvent.BreakEvent event) {
        if (!MSpellCompatConfig.TREE_PHYSICS_DAMAGE_ENABLED.get()
                || !MSpellCompatConfig.TREE_PHYSICS_BLOCK_DAMAGE_FELLING_ENABLED.get()
                || !isTreePhysicsLoaded()
                || !(event.getPlayer().level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        if (!level.getBlockState(pos).is(TREE_PHYSICS_LOGS)) {
            return;
        }
        suppressReplacement(level, pos);
    }

    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!MSpellCompatConfig.TREE_PHYSICS_DAMAGE_ENABLED.get()
                || !isTreePhysicsLoaded()
                || !(event.getLevel() instanceof ServerLevel level)
                || !event.getExplosion().interactsWithBlocks()) {
            return;
        }

        Vec3 explosionOrigin = event.getExplosion().center();
        Set<Long> checkedTreeBlocks = new HashSet<>();
        for (BlockPos affectedPos : event.getAffectedBlocks()) {
            BlockPos pos = affectedPos.immutable();
            BlockState state = level.getBlockState(pos);
            if (!state.is(TREE_PHYSICS_LOGS)) {
                continue;
            }
            suppressReplacement(level, pos);
            if (!MSpellCompatConfig.TREE_PHYSICS_EXPLOSION_FELLING_ENABLED.get()
                    || checkedTreeBlocks.contains(pos.asLong())) {
                continue;
            }

            invokeTreePhysics(level, pos, state, explosionOrigin);
            if (level.getBlockState(pos).is(TREE_PHYSICS_LOGS)) {
                markTreeCandidateBlocks(level, pos, checkedTreeBlocks);
            }
        }
    }

    public static boolean shouldInspectBlockReplacement(Level level, BlockPos pos, BlockState nextState) {
        if (!(level instanceof ServerLevel serverLevel)
                || (!nextState.isAir() && !nextState.is(Blocks.BARRIER))) {
            return false;
        }
        if (hasBlockReplacementSuppression(serverLevel, pos)) {
            return true;
        }
        if (!MSpellCompatConfig.TREE_PHYSICS_DAMAGE_ENABLED.get()
                || !MSpellCompatConfig.TREE_PHYSICS_BLOCK_DAMAGE_FELLING_ENABLED.get()
                || !isTreePhysicsLoaded()
                || isTreePhysicsInternalMutation()) {
            return false;
        }
        return true;
    }

    public static void beforeBlockReplacement(Level level, BlockPos pos, BlockState previousState, BlockState nextState) {
        if (!(level instanceof ServerLevel serverLevel) || !isTreePhysicsLoaded()
                || !previousState.is(TREE_PHYSICS_LOGS)
                || previousState.is(TREE_PHYSICS_LOGS) == nextState.is(TREE_PHYSICS_LOGS)) {
            return;
        }

        if (consumeBlockReplacementSuppression(serverLevel, pos)) {
            return;
        }
        if (!MSpellCompatConfig.TREE_PHYSICS_DAMAGE_ENABLED.get()
                || !MSpellCompatConfig.TREE_PHYSICS_BLOCK_DAMAGE_FELLING_ENABLED.get()
                || isTreePhysicsInternalMutation()) {
            return;
        }

        invokeTreePhysics(serverLevel, pos, previousState, fallbackOrigin(pos));
    }

    private static void suppressReplacement(ServerLevel level, BlockPos pos) {
        synchronized (BLOCK_REPLACEMENT_SUPPRESSIONS) {
            Map<Long, Long> positions = BLOCK_REPLACEMENT_SUPPRESSIONS.computeIfAbsent(level, ignored -> new HashMap<>());
            long now = level.getGameTime();
            Long lastCleanup = LAST_SUPPRESSION_CLEANUP.get(level);
            if (lastCleanup == null || now - lastCleanup >= MSpellCompatConfig.TREE_PHYSICS_SUPPRESSION_CLEANUP_INTERVAL_TICKS.get()) {
                positions.entrySet().removeIf(entry -> entry.getValue() < now);
                LAST_SUPPRESSION_CLEANUP.put(level, now);
            }
            positions.put(pos.asLong(), now);
        }
    }

    private static boolean hasBlockReplacementSuppression(ServerLevel level, BlockPos pos) {
        synchronized (BLOCK_REPLACEMENT_SUPPRESSIONS) {
            Map<Long, Long> positions = BLOCK_REPLACEMENT_SUPPRESSIONS.get(level);
            if (positions == null) return false;
            Long expiresAt = positions.get(pos.asLong());
            return expiresAt != null && expiresAt >= level.getGameTime();
        }
    }

    private static boolean consumeBlockReplacementSuppression(ServerLevel level, BlockPos pos) {
        synchronized (BLOCK_REPLACEMENT_SUPPRESSIONS) {
            Map<Long, Long> positions = BLOCK_REPLACEMENT_SUPPRESSIONS.get(level);
            if (positions == null) return false;

            Long expiresAt = positions.remove(pos.asLong());
            if (positions.isEmpty()) BLOCK_REPLACEMENT_SUPPRESSIONS.remove(level);
            return expiresAt != null && expiresAt >= level.getGameTime();
        }
    }

    public static void enterTreePhysicsOperation() {
        TREE_PHYSICS_OPERATION_DEPTH.set(TREE_PHYSICS_OPERATION_DEPTH.get() + 1);
    }

    public static void exitTreePhysicsOperation() {
        int depth = TREE_PHYSICS_OPERATION_DEPTH.get();
        if (depth <= 1) TREE_PHYSICS_OPERATION_DEPTH.remove();
        else TREE_PHYSICS_OPERATION_DEPTH.set(depth - 1);
    }

    private static boolean isTreePhysicsInternalMutation() {
        return Boolean.TRUE.equals(PROCESSING_TREE_PHYSICS.get()) || TREE_PHYSICS_OPERATION_DEPTH.get() > 0;
    }

    private static void markTreeCandidateBlocks(ServerLevel level, BlockPos brokenPos, Set<Long> checkedBlocks) {
        checkedBlocks.add(brokenPos.asLong());
        if (!resolveTreeLookup()) return;

        try {
            Object rawResults = findTreesAroundMethod.invoke(null, level, brokenPos);
            if (!(rawResults instanceof Iterable<?> results)) return;
            for (Object result : results) {
                Object rawBlocks = treeResultGetBlocksMethod.invoke(result);
                if (!(rawBlocks instanceof Iterable<?> blocks)) continue;
                for (Object block : blocks) {
                    if (block instanceof BlockPos blockPos) checkedBlocks.add(blockPos.asLong());
                }
            }
        } catch (IllegalAccessException | InvocationTargetException exception) {
            if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                MSpellCompatDebug.log("tree_physics", "tree_candidate_lookup_failed",
                        "pos=" + brokenPos + ",cause=" + exception.getClass().getSimpleName());
            }
        }
    }

    private static boolean resolveTreeLookup() {
        if (treeLookupResolved) return findTreesAroundMethod != null && treeResultGetBlocksMethod != null;
        synchronized (TreePhysicsDamageBridge.class) {
            if (!treeLookupResolved) {
                try {
                    ClassLoader loader = TreePhysicsDamageBridge.class.getClassLoader();
                    Class<?> utility = Class.forName("com.farcr.treephysics.api.util.FloodFillUtil", true, loader);
                    Class<?> resultType = Class.forName("com.farcr.treephysics.api.flood_fill.TreeResult", true, loader);
                    findTreesAroundMethod = utility.getMethod("findTreesAround", ServerLevel.class, BlockPos.class);
                    treeResultGetBlocksMethod = resultType.getMethod("getBlocks");
                } catch (ReflectiveOperationException | LinkageError failure) {
                    findTreesAroundMethod = null;
                    treeResultGetBlocksMethod = null;
                } finally {
                    treeLookupResolved = true;
                }
            }
        }
        return findTreesAroundMethod != null && treeResultGetBlocksMethod != null;
    }

    private static Vec3 fallbackOrigin(BlockPos pos) {
        int direction = Math.floorMod(Long.hashCode(pos.asLong()), 4);
        Vec3 center = Vec3.atCenterOf(pos);
        return switch (direction) {
            case 0 -> center.add(1.0, 0.0, 0.0);
            case 1 -> center.add(-1.0, 0.0, 0.0);
            case 2 -> center.add(0.0, 0.0, 1.0);
            default -> center.add(0.0, 0.0, -1.0);
        };
    }

    private static void invokeTreePhysics(ServerLevel level, BlockPos pos, BlockState brokenState, Vec3 origin) {
        if (Boolean.TRUE.equals(PROCESSING_TREE_PHYSICS.get()) || !resolveApi()) {
            return;
        }

        PROCESSING_TREE_PHYSICS.set(true);
        try {
            blockBrokenMethod.invoke(null, level, pos, brokenState, origin, false);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            Throwable cause = exception instanceof InvocationTargetException invocation && invocation.getCause() != null
                    ? invocation.getCause() : exception;
            if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                MSpellCompatDebug.log("tree_physics", "damage_felling_failed", "pos=" + pos + ",cause="
                        + cause.getClass().getSimpleName() + ": " + String.valueOf(cause.getMessage()));
            }
        } finally {
            PROCESSING_TREE_PHYSICS.remove();
        }
    }

    private static boolean resolveApi() {
        if (apiResolved) {
            return apiAvailable;
        }
        synchronized (TreePhysicsDamageBridge.class) {
            if (apiResolved) {
                return apiAvailable;
            }
            try {
                Class<?> utility = Class.forName(
                        "com.farcr.treephysics.api.util.FloodFillUtil",
                        true,
                        TreePhysicsDamageBridge.class.getClassLoader()
                );
                blockBrokenMethod = utility.getMethod(
                        "blockBroken",
                        ServerLevel.class,
                        BlockPos.class,
                        BlockState.class,
                        Vec3.class,
                        boolean.class
                );
                apiAvailable = true;
            } catch (ReflectiveOperationException | LinkageError exception) {
                apiAvailable = false;
                if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                    MSpellCompatDebug.log("tree_physics", "api_unavailable", "cause="
                            + exception.getClass().getSimpleName() + ": " + String.valueOf(exception.getMessage()));
                }
            } finally {
                apiResolved = true;
            }
            return apiAvailable;
        }
    }

    private static boolean isTreePhysicsLoaded() {
        if (treePhysicsLoadedResolved) return treePhysicsLoaded;
        synchronized (TreePhysicsDamageBridge.class) {
            if (!treePhysicsLoadedResolved) {
                treePhysicsLoaded = ModList.get().isLoaded("treephysics");
                treePhysicsLoadedResolved = true;
            }
        }
        return treePhysicsLoaded;
    }
}
