package com.misp.mspellcompat.mixin.burnt;

import com.misp.mspellcompat.config.MSpellCompatConfig;
import com.misp.mspellcompat.util.MSpellCompatDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
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

@Mixin(targets = "net.pixelbank.burnt.util.BurntBlockUtil", remap = false)
public abstract class MixinBurntAdjacentFluidWater {
    @Unique
    private static final Direction[] mspell$WATER_SEARCH_ORDER = {
            Direction.EAST, Direction.WEST, Direction.SOUTH,
            Direction.NORTH, Direction.DOWN, Direction.UP
    };

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
        BlockPos center = BlockPos.containing(x, y, z);
        boolean originalResult = cir.getReturnValueZ();
        if (level.isClientSide()) {
            MSpellCompatDebug.log("burnt_water", "adjacent_water_result",
                    "center=" + center + ",client=true,original=" + originalResult);
            return;
        }

        int layersToConsume = MSpellCompatConfig.BURNT_WATER_LAYERS_CONSUMED_PER_EXTINGUISH.get();
        if (originalResult) {
            if (layersToConsume > 0) {
                BlockPos waterPos = mspell$findAdjacentWaterLayer(level, center, layersToConsume, true);
                if (waterPos != null) {
                    if (!mspell$consumeWaterLayers(level, waterPos, layersToConsume)) {
                        cir.setReturnValue(false);
                        MSpellCompatDebug.log("burnt_water", "extinguish_denied",
                                "center=" + center + ",waterPos=" + waterPos
                                        + ",reason=layer_consumption_failed,cost=" + layersToConsume
                                        + ",neighbors=" + mspell$describeNeighbors(level, center));
                        return;
                    }
                    MSpellCompatDebug.log("burnt_water", "water_layer_consumed",
                            "center=" + center + ",waterPos=" + waterPos + ",cost=" + layersToConsume);
                } else if (mspell$findAdjacentWaterLayer(level, center, 1, true) != null) {
                    cir.setReturnValue(false);
                    MSpellCompatDebug.log("burnt_water", "extinguish_denied",
                            "center=" + center + ",reason=insufficient_water_layers,cost=" + layersToConsume
                                    + ",neighbors=" + mspell$describeNeighbors(level, center));
                    return;
                } else {
                    MSpellCompatDebug.log("burnt_water", "layer_consumption_skipped",
                            "center=" + center + ",reason=original_water_match_without_standalone_water_layer");
                }
            }

            MSpellCompatDebug.log("burnt_water", "adjacent_water_result",
                    "center=" + center + ",original=true,final=" + cir.getReturnValueZ()
                            + ",layersToConsume=" + layersToConsume);
            return;
        }

        if (!MSpellCompatConfig.BURNT_COUNT_FLOWING_FLUIDS_WATER.get()) {
            MSpellCompatDebug.log("burnt_water", "adjacent_water_result",
                    "center=" + center + ",original=false,partialWaterEnabled=false,final=false");
            return;
        }

        int minimumAmount = MSpellCompatConfig.BURNT_MIN_WATER_AMOUNT.get();
        int requiredAmount = Math.max(minimumAmount, layersToConsume);
        BlockPos waterPos = mspell$findAdjacentWaterLayer(
                level, center, requiredAmount, layersToConsume > 0
        );
        if (waterPos == null) {
            MSpellCompatDebug.log("burnt_water", "adjacent_water_scan",
                    "center=" + center + ",minimumAmount=" + minimumAmount
                            + ",layersToConsume=" + layersToConsume + ",foundWater=false"
                            + ",neighbors=" + mspell$describeNeighbors(level, center));
            return;
        }

        if (layersToConsume > 0 && !mspell$consumeWaterLayers(level, waterPos, layersToConsume)) {
            MSpellCompatDebug.log("burnt_water", "extinguish_denied",
                    "center=" + center + ",waterPos=" + waterPos
                            + ",reason=layer_consumption_failed,cost=" + layersToConsume);
            return;
        }

        cir.setReturnValue(true);
        if (layersToConsume > 0) {
            MSpellCompatDebug.log("burnt_water", "water_layer_consumed",
                    "center=" + center + ",waterPos=" + waterPos + ",cost=" + layersToConsume);
        }
        MSpellCompatDebug.log("burnt_water", "adjacent_water_scan",
                "center=" + center + ",minimumAmount=" + minimumAmount
                        + ",layersToConsume=" + layersToConsume + ",foundWater=true,waterPos=" + waterPos
                        + ",final=true,neighbors=" + mspell$describeNeighbors(level, center));
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
                && level.getBlockState(pos).getBlock() == fluidState.createLegacyBlock().getBlock();
    }

    @Unique
    private static boolean mspell$consumeWaterLayers(
            LevelAccessor level,
            BlockPos waterPos,
            int layersToConsume
    ) {
        if (layersToConsume <= 0) {
            return true;
        }

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

        MSpellCompatDebug.log("burnt_water", "layer_consumption_result",
                "waterPos=" + waterPos + ",fluid=" + fluid + ",requested=" + layersToConsume
                        + ",beforeAmount=" + before.getAmount() + ",expectedAmount=" + expectedAmount
                        + ",remainder=" + remainder + ",after=" + MSpellCompatDebug.describeFluid(level, waterPos)
                        + ",success=" + (remainder == 0 && amountMatches));
        return remainder == 0 && amountMatches;
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
