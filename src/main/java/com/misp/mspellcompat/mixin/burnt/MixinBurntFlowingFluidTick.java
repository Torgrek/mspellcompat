package com.misp.mspellcompat.mixin.burnt;

import com.misp.mspellcompat.burnt.BurntFluidTickContext;
import com.misp.mspellcompat.config.MSpellCompatConfig;
import com.misp.mspellcompat.util.MSpellCompatDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.material.FlowingFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

@Mixin(FlowingFluid.class)
public abstract class MixinBurntFlowingFluidTick {
    @Unique
    private static final Direction[] mspell$DIRECTIONS = {
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
    private static volatile Method mspell$hasAdjacentWaterMethod;

    @Unique
    private static volatile boolean mspell$hasAdjacentWaterResolved;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 1)
    private void mspell$extinguishBeforeFlow(Level level, BlockPos pos, FluidState scheduledState, CallbackInfo ci) {
        if (level.isClientSide() || !MSpellCompatConfig.BURNT_WATER_EXTINGUISH_ENABLED.get()
                || !MSpellCompatConfig.BURNT_COUNT_FLOWING_FLUIDS_WATER.get()) return;

        Fluid self = (Fluid) (Object) this;
        if (self != Fluids.WATER && self != Fluids.FLOWING_WATER) return;

        FluidState before = level.getFluidState(pos);
        if (before.isEmpty() || (before.getType() != Fluids.WATER && before.getType() != Fluids.FLOWING_WATER)) return;

        int layersToConsume = MSpellCompatConfig.BURNT_WATER_LAYERS_CONSUMED_PER_EXTINGUISH.get();
        int requiredAmount = Math.max(
                MSpellCompatConfig.BURNT_MIN_WATER_AMOUNT.get(), layersToConsume
        );
        if (before.getAmount() < requiredAmount) return;

        Method hasAdjacentWater = mspell$getHasAdjacentWaterMethod();
        if (hasAdjacentWater == null) return;

        for (Direction direction : mspell$DIRECTIONS) {
            BlockPos targetPos = pos.relative(direction);
            BlockState beforeTarget = level.getBlockState(targetPos);
            if (!mspell$isBurntFireState(beforeTarget)) continue;

            try (BurntFluidTickContext.Scope ignored = BurntFluidTickContext.preferWaterPosition(pos)) {
                hasAdjacentWater.invoke(null, (LevelAccessor) level,
                        (double) targetPos.getX(), (double) targetPos.getY(), (double) targetPos.getZ());
            } catch (Throwable failure) {
                if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                    MSpellCompatDebug.debugException(
                            "[MSpellCompatDebug][burnt_water] Immediate Burnt check failed at " + targetPos,
                            failure
                    );
                }
                continue;
            }

            BlockState afterTarget = level.getBlockState(targetPos);
            if (mspell$isBurntFireState(afterTarget)) continue;

            FluidState after = level.getFluidState(pos);
            int afterAmount = !after.isEmpty() && after.getType() == before.getType() ? after.getAmount() : 0;
            int consumed = Math.max(0, before.getAmount() - afterAmount);

            if (layersToConsume > 0 && consumed >= layersToConsume) {
                if (!after.isEmpty()) {
                    level.scheduleTick(pos, after.getType(), after.getType().getTickDelay(level));
                }
                if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                    MSpellCompatDebug.log("burnt_water", "flow_tick_intercepted",
                            "waterPos=" + pos + ",target=" + targetPos + ",beforeAmount=" + before.getAmount()
                                    + ",afterAmount=" + afterAmount + ",consumed=" + consumed
                                    + ",scheduledRemainder=" + !after.isEmpty());
                }
                ci.cancel();
            } else {
                if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                    MSpellCompatDebug.log("burnt_water", "flow_tick_target_extinguished",
                            "waterPos=" + pos + ",target=" + targetPos + ",layersConsumed=" + consumed
                                    + ",configuredCost=" + layersToConsume + ",waterRemaining=" + afterAmount);
                }
            }
            return;
        }
    }

    @Unique
    private static boolean mspell$isBurntFireState(BlockState state) {
        return state.is(mspell$BURNT_ON_FIRE) || state.is(mspell$BURNT_FIRE);
    }

    @Unique
    private static Method mspell$getHasAdjacentWaterMethod() {
        if (mspell$hasAdjacentWaterResolved) return mspell$hasAdjacentWaterMethod;

        synchronized (MixinBurntFlowingFluidTick.class) {
            if (mspell$hasAdjacentWaterResolved) return mspell$hasAdjacentWaterMethod;
            try {
                Class<?> utilityClass = Class.forName(
                        "net.pixelbank.burnt.util.BurntBlockUtil",
                        true,
                        MixinBurntFlowingFluidTick.class.getClassLoader()
                );
                Method method = utilityClass.getDeclaredMethod(
                        "hasAdjacentWater", LevelAccessor.class, double.class, double.class, double.class
                );
                method.setAccessible(true);
                if (Modifier.isStatic(method.getModifiers())) {
                    mspell$hasAdjacentWaterMethod = method;
                }
            } catch (Throwable failure) {
                if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                    MSpellCompatDebug.debugException(
                            "[MSpellCompatDebug][burnt_water] Could not resolve Burnt water predicate",
                            failure
                    );
                }
            }
            mspell$hasAdjacentWaterResolved = true;
            return mspell$hasAdjacentWaterMethod;
        }
    }
}
