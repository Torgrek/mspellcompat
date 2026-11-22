package com.misp.mspellcompat.burnt;

import com.misp.mspellcompat.config.MSpellCompatConfig;
import com.misp.mspellcompat.util.MSpellCompatDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class BurntWaterPlacementHandler {
    private static final Direction[] DIRECTIONS = {
            Direction.EAST, Direction.WEST, Direction.SOUTH,
            Direction.NORTH, Direction.DOWN, Direction.UP
    };

    private static final TagKey<Block> BURNT_ON_FIRE = BlockTags.create(
            ResourceLocation.fromNamespaceAndPath("burnt", "on_fire"));
    private static final TagKey<Block> BURNT_FIRE = BlockTags.create(
            ResourceLocation.fromNamespaceAndPath("burnt", "fire"));

    private static volatile Method hasAdjacentWaterMethod;
    private static volatile boolean hasAdjacentWaterResolved;

    private BurntWaterPlacementHandler() {
    }

    public static void onWaterPlaced(Level level, BlockPos waterPos) {
        if (level.isClientSide() || !MSpellCompatConfig.BURNT_COUNT_FLOWING_FLUIDS_WATER.get()) return;

        FluidState initialWater = level.getFluidState(waterPos);
        if (!isWater(initialWater)) return;

        int layersToConsume = MSpellCompatConfig.BURNT_WATER_LAYERS_CONSUMED_PER_EXTINGUISH.get();
        int requiredAmount = Math.max(
                MSpellCompatConfig.BURNT_MIN_WATER_AMOUNT.get(), layersToConsume);
        if (initialWater.getAmount() < requiredAmount) return;

        Method predicate = getHasAdjacentWaterMethod();
        if (predicate == null) return;

        boolean sawBurntNamespaceNeighbor = false;
        boolean sawBurningTarget = false;
        StringBuilder burntNeighbors = new StringBuilder();
        for (Direction direction : DIRECTIONS) {
            BlockPos targetPos = waterPos.relative(direction);
            BlockState beforeTarget = level.getBlockState(targetPos);
            ResourceLocation targetId = BuiltInRegistries.BLOCK.getKey(beforeTarget.getBlock());
            if (targetId != null && targetId.getNamespace().equals("burnt")) {
                sawBurntNamespaceNeighbor = true;
                if (!burntNeighbors.isEmpty()) burntNeighbors.append(';');
                burntNeighbors.append(direction.getName()).append(':').append(targetId)
                        .append(",tagged=").append(isBurningBlock(beforeTarget));
            }
            if (!isBurningBlock(beforeTarget)) continue;
            sawBurningTarget = true;

            FluidState beforeWater = level.getFluidState(waterPos);
            int beforeAmount = isWater(beforeWater) ? beforeWater.getAmount() : 0;

            try (BurntFluidTickContext.Scope ignored = BurntFluidTickContext.preferWaterPosition(waterPos)) {
                predicate.invoke(null, (LevelAccessor) level,
                        (double) targetPos.getX(), (double) targetPos.getY(), (double) targetPos.getZ());
            } catch (Throwable failure) {
                MSpellCompatDebug.debugException(
                        "[MSpellCompatDebug][burnt_water] Immediate placement extinguish check failed at " + targetPos,
                        failure);
                continue;
            }

            BlockState afterTarget = level.getBlockState(targetPos);
            FluidState afterWater = level.getFluidState(waterPos);
            int afterAmount = isWater(afterWater) ? afterWater.getAmount() : 0;
            int consumed = Math.max(0, beforeAmount - afterAmount);
            boolean extinguished = !isBurningBlock(afterTarget);
            boolean costPaid = layersToConsume <= 0 || consumed >= layersToConsume;

            if (extinguished && costPaid) {
                MSpellCompatDebug.log("burnt_water", "water_placement_extinguished",
                        "waterPos=" + waterPos + ",target=" + targetPos + ",beforeTarget="
                                + beforeTarget.getBlock() + ",afterTarget=" + afterTarget.getBlock()
                                + ",beforeAmount=" + beforeAmount + ",afterAmount=" + afterAmount
                                + ",consumed=" + consumed + ",configuredCost=" + layersToConsume);
                if (afterAmount > 0) {
                    Fluid remainingFluid = afterWater.getType();
                    level.scheduleTick(waterPos, remainingFluid, remainingFluid.getTickDelay(level));
                }
                return;
            }

            MSpellCompatDebug.log("burnt_water", "water_placement_attempt_incomplete",
                    "waterPos=" + waterPos + ",target=" + targetPos + ",extinguished=" + extinguished
                            + ",beforeAmount=" + beforeAmount + ",afterAmount=" + afterAmount
                            + ",consumed=" + consumed + ",configuredCost=" + layersToConsume);

            if (afterAmount < requiredAmount) return;
        }
        if (sawBurntNamespaceNeighbor) {
            MSpellCompatDebug.log("burnt_water",
                    sawBurningTarget ? "water_placement_scan_finished" : "water_placement_no_burning_tag_match",
                    "waterPos=" + waterPos + ",amount=" + initialWater.getAmount()
                            + ",neighbors=" + burntNeighbors);
        }
    }

    private static boolean isWater(FluidState state) {
        if (state.isEmpty()) return false;
        Fluid fluid = state.getType();
        return fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER;
    }

    private static boolean isBurningBlock(BlockState state) {
        return state.is(BURNT_ON_FIRE) || state.is(BURNT_FIRE);
    }

    private static Method getHasAdjacentWaterMethod() {
        if (hasAdjacentWaterResolved) return hasAdjacentWaterMethod;

        synchronized (BurntWaterPlacementHandler.class) {
            if (hasAdjacentWaterResolved) return hasAdjacentWaterMethod;
            try {
                Class<?> utilityClass = Class.forName(
                        "net.pixelbank.burnt.util.BurntBlockUtil",
                        true,
                        BurntWaterPlacementHandler.class.getClassLoader());
                Method method = utilityClass.getDeclaredMethod(
                        "hasAdjacentWater", LevelAccessor.class, double.class, double.class, double.class);
                method.setAccessible(true);
                if (Modifier.isStatic(method.getModifiers())) hasAdjacentWaterMethod = method;
            } catch (Throwable failure) {
                MSpellCompatDebug.debugException(
                        "[MSpellCompatDebug][burnt_water] Could not resolve Burnt water predicate for placement",
                        failure);
            }
            hasAdjacentWaterResolved = true;
            return hasAdjacentWaterMethod;
        }
    }
}
