package com.misp.mspellcompat.mixin.burnt;

import com.misp.mspellcompat.burnt.BurntFluidTickContext;
import com.misp.mspellcompat.config.MSpellCompatConfig;
import com.misp.mspellcompat.util.MSpellCompatDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.FlowingFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import traben.flowing_fluids.FFFluidUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

@Mixin(targets = "net.pixelbank.burnt.util.BurntBlockUtil", remap = false)
public abstract class MixinBurntAdjacentFluidWater {
    @Unique
    private static final Direction[] mspell$WATER_SEARCH_ORDER = {
            Direction.EAST, Direction.WEST, Direction.SOUTH,
            Direction.NORTH, Direction.DOWN, Direction.UP
    };

    @Unique
    private static final TagKey<Block> mspell$BURNT_ON_FIRE =
            BlockTags.create(ResourceLocation.fromNamespaceAndPath("burnt", "on_fire"));

    @Unique
    private static final TagKey<Block> mspell$BURNT_FIRE =
            BlockTags.create(ResourceLocation.fromNamespaceAndPath("burnt", "fire"));

    @Unique
    private static final ThreadLocal<Boolean> mspell$INSIDE_EXTINGUISH = ThreadLocal.withInitial(() -> false);

    @Unique
    private static volatile Method mspell$extinguishProcedure;

    @Unique
    private static volatile boolean mspell$extinguishProcedureResolved;

    @Inject(
            method = "hasAdjacentWater",
            at = @At("RETURN"),
            cancellable = true,
            require = 1,
            remap = false
    )
    private static void mspell$countFlowingFluidWater(
            LevelAccessor level,
            double x,
            double y,
            double z,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (level.isClientSide() || !MSpellCompatConfig.BURNT_WATER_EXTINGUISH_ENABLED.get()
                || !MSpellCompatConfig.BURNT_COUNT_FLOWING_FLUIDS_WATER.get()) return;

        BlockPos center = BlockPos.containing(x, y, z);
        boolean originalResult = cir.getReturnValueZ();
        int layersToConsume = MSpellCompatConfig.BURNT_WATER_LAYERS_CONSUMED_PER_EXTINGUISH.get();
        boolean partialWaterEnabled = MSpellCompatConfig.BURNT_COUNT_FLOWING_FLUIDS_WATER.get();
        BlockPos preferredWaterPos = BurntFluidTickContext.getPreferredWaterPosition();

        if (mspell$INSIDE_EXTINGUISH.get()) {
            if (originalResult || !partialWaterEnabled) return;
            int nestedMinimumAmount = MSpellCompatConfig.BURNT_MIN_WATER_AMOUNT.get();
            int nestedRequiredAmount = Math.max(nestedMinimumAmount, layersToConsume);
            BlockPos nestedWaterPos = mspell$findAdjacentWaterLayer(
                    level, center, nestedRequiredAmount, false
            );
            if (nestedWaterPos != null) {
                cir.setReturnValue(true);
                if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                    MSpellCompatDebug.log("burnt_water", "nested_water_match",
                            "center=" + center + ",waterPos=" + nestedWaterPos
                                    + ",requiredAmount=" + nestedRequiredAmount);
                }
            }
            return;
        }

        if (!originalResult && !partialWaterEnabled) {
            if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                MSpellCompatDebug.log("burnt_water", "adjacent_water_result",
                        "center=" + center + ",original=false,partialWaterEnabled=false,final=false");
            }
            return;
        }

        int minimumAmount = MSpellCompatConfig.BURNT_MIN_WATER_AMOUNT.get();
        int requiredAmount = Math.max(minimumAmount, layersToConsume);
        BlockPos waterPos;
        if (preferredWaterPos != null && mspell$isAdjacent(center, preferredWaterPos)) {
            FluidState preferredState = level.getFluidState(preferredWaterPos);
            boolean preferredWaterValid = mspell$isSupportedWater(preferredState)
                    && preferredState.getAmount() >= requiredAmount
                    && (layersToConsume <= 0
                            || mspell$isStandaloneWaterBlock(level, preferredWaterPos, preferredState));
            waterPos = preferredWaterValid ? preferredWaterPos : null;
        } else {
            waterPos = mspell$findAdjacentWaterLayer(
                    level, center, requiredAmount, layersToConsume > 0
            );
        }

        if (waterPos == null) {
            if (originalResult && layersToConsume > 0
                    && mspell$isBurntFireState(level.getBlockState(center))) {
                BlockPos insufficientWaterPos = mspell$findAdjacentWaterLayer(level, center, 1, true);
                if (insufficientWaterPos != null) {
                    cir.setReturnValue(false);
                    if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                        MSpellCompatDebug.log("burnt_water", "extinguish_denied",
                                "center=" + center + ",waterPos=" + insufficientWaterPos
                                        + ",reason=insufficient_water_layers,cost=" + layersToConsume);
                    }
                    return;
                }
            }
            if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                MSpellCompatDebug.log("burnt_water", "adjacent_water_scan",
                        "center=" + center + ",original=" + originalResult
                                + ",minimumAmount=" + minimumAmount + ",layersToConsume=" + layersToConsume
                                + ",foundWater=false,neighbors=" + mspell$describeNeighbors(level, center));
            }
            return;
        }

        if (mspell$isBurntFireState(level.getBlockState(center))) {
            boolean extinguished = mspell$extinguishBurntBlock(level, center);
            if (!extinguished) {
                if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                    MSpellCompatDebug.log("burnt_water", "extinguish_failed",
                            "center=" + center + ",waterPos=" + waterPos
                                    + ",reason=burnt_procedure_did_not_change_fire_state");
                }
                if (!originalResult) cir.setReturnValue(false);
                return;
            }

            boolean consumed = layersToConsume <= 0
                    || mspell$consumeWaterLayers(level, waterPos, layersToConsume);
            cir.setReturnValue(true);
            if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                MSpellCompatDebug.log("burnt_water", "burnt_block_extinguished",
                        "center=" + center + ",waterPos=" + waterPos + ",layersConsumed="
                                + (consumed ? layersToConsume : 0) + ",consumptionSucceeded=" + consumed);
            }
            return;
        }

        if (!originalResult) {
            cir.setReturnValue(true);
        }
        if (MSpellCompatDebug.isDebugLoggingEnabled()) {
            MSpellCompatDebug.log("burnt_water", "adjacent_water_scan",
                    "center=" + center + ",original=" + originalResult + ",minimumAmount=" + minimumAmount
                            + ",layersToConsume=" + layersToConsume + ",foundWater=true,waterPos=" + waterPos
                            + ",burntFireTarget=false,final=" + cir.getReturnValueZ());
        }
    }

    @Unique
    private static BlockPos mspell$findAdjacentWaterLayer(
            LevelAccessor level,
            BlockPos center,
            int minimumAmount,
            boolean requireStandaloneFluidBlock
    ) {
        for (Direction direction : mspell$WATER_SEARCH_ORDER) {
            BlockPos pos = center.relative(direction);
            FluidState fluidState = level.getFluidState(pos);
            if (!mspell$isSupportedWater(fluidState) || fluidState.getAmount() < minimumAmount) {
                continue;
            }
            if (requireStandaloneFluidBlock && !mspell$isStandaloneWaterBlock(level, pos, fluidState)) {
                continue;
            }
            return pos;
        }
        return null;
    }

    @Unique
    private static boolean mspell$isAdjacent(BlockPos first, BlockPos second) {
        return Math.abs(first.getX() - second.getX())
                + Math.abs(first.getY() - second.getY())
                + Math.abs(first.getZ() - second.getZ()) == 1;
    }

    @Unique
    private static boolean mspell$isSupportedWater(FluidState fluidState) {
        Fluid fluid = fluidState.getType();
        return !fluidState.isEmpty() && (fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER);
    }

    @Unique
    private static boolean mspell$isStandaloneWaterBlock(
            LevelAccessor level,
            BlockPos pos,
            FluidState fluidState
    ) {
        return fluidState.getType() instanceof FlowingFluid
                && level.getBlockEntity(pos) == null;
    }

    @Unique
    private static boolean mspell$consumeWaterLayers(
            LevelAccessor level,
            BlockPos waterPos,
            int layersToConsume
    ) {
        if (layersToConsume <= 0) return true;

        FluidState before = level.getFluidState(waterPos);
        if (!mspell$isSupportedWater(before)
                || !mspell$isStandaloneWaterBlock(level, waterPos, before)
                || before.getAmount() < layersToConsume) {
            return false;
        }

        Fluid fluid = before.getType();
        int expectedAmount = before.getAmount() - layersToConsume;
        int remainder = FFFluidUtils.removeAmountFromFluidAtPosWithRemainder(
                level, waterPos, fluid, layersToConsume
        );
        FluidState after = level.getFluidState(waterPos);
        boolean amountMatches = expectedAmount == 0
                ? after.isEmpty()
                : !after.isEmpty()
                        && after.getAmount() == expectedAmount
                        && after.getType().isSame(fluid);

        if (MSpellCompatDebug.isDebugLoggingEnabled()) {
            MSpellCompatDebug.log("burnt_water", "layer_consumption_result",
                    "waterPos=" + waterPos + ",fluid=" + fluid + ",requested=" + layersToConsume
                            + ",beforeAmount=" + before.getAmount() + ",expectedAmount=" + expectedAmount
                            + ",remainder=" + remainder + ",after=" + MSpellCompatDebug.describeFluid(level, waterPos)
                            + ",success=" + (remainder == 0 && amountMatches));
        }
        return remainder == 0 && amountMatches;
    }

    @Unique
    private static boolean mspell$isBurntFireState(BlockState state) {
        return state.is(mspell$BURNT_ON_FIRE) || state.is(mspell$BURNT_FIRE);
    }

    @Unique
    private static boolean mspell$extinguishBurntBlock(LevelAccessor level, BlockPos pos) {
        if (!mspell$isBurntFireState(level.getBlockState(pos))) return false;

        Method procedure = mspell$getExtinguishProcedure(level);
        if (procedure == null) return false;

        mspell$INSIDE_EXTINGUISH.set(true);
        try {
            procedure.invoke(null, level, (double) pos.getX(), (double) pos.getY(), (double) pos.getZ());
        } catch (Throwable failure) {
            if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                MSpellCompatDebug.debugException(
                        "[MSpellCompatDebug][burnt_water] Burnt single-block extinguish procedure failed at " + pos,
                        failure
                );
            }
            return false;
        } finally {
            mspell$INSIDE_EXTINGUISH.set(false);
        }

        return !mspell$isBurntFireState(level.getBlockState(pos));
    }

    @Unique
    private static Method mspell$getExtinguishProcedure(LevelAccessor level) {
        if (mspell$extinguishProcedureResolved) return mspell$extinguishProcedure;

        synchronized (MixinBurntAdjacentFluidWater.class) {
            if (mspell$extinguishProcedureResolved) return mspell$extinguishProcedure;
            try {
                Class<?> procedureClass = Class.forName(
                        "net.pixelbank.burnt.procedures.ExtinguishProcedure",
                        true,
                        MixinBurntAdjacentFluidWater.class.getClassLoader()
                );
                Method method = procedureClass.getMethod(
                        "execute", LevelAccessor.class, double.class, double.class, double.class
                );
                if (Modifier.isStatic(method.getModifiers())) {
                    mspell$extinguishProcedure = method;
                }
            } catch (Throwable failure) {
                if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                    MSpellCompatDebug.debugException(
                            "[MSpellCompatDebug][burnt_water] Could not resolve Burnt single-block extinguish procedure",
                            failure
                    );
                }
            }
            mspell$extinguishProcedureResolved = true;
            return mspell$extinguishProcedure;
        }
    }

    @Unique
    private static String mspell$describeNeighbors(LevelAccessor level, BlockPos center) {
        StringBuilder neighbors = new StringBuilder("[");
        for (Direction direction : mspell$WATER_SEARCH_ORDER) {
            BlockPos neighbor = center.relative(direction);
            if (neighbors.length() > 1) neighbors.append(';');
            neighbors.append(direction).append(':').append(MSpellCompatDebug.describeFluid(level, neighbor));
        }
        return neighbors.append(']').toString();
    }
}
