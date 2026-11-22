package com.misp.mspellcompat.mixin.lso;

import com.misp.mspellcompat.config.MSpellCompatConfig;
import com.misp.mspellcompat.util.MSpellCompatDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import traben.flowing_fluids.FFFluidUtils;

@Mixin(targets = "sfiomn.legendarysurvivaloverhaul.common.items.drink.CanteenItem", remap = false)
public abstract class MixinLsoCanteenFiniteWater {

    @Inject(
            method = "useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;",
            at = @At("RETURN"),
            require = 1,
            remap = false
    )
    private void mspell$consumeWorldWaterAfterUseOn(
            UseOnContext context,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        Level level = context.getLevel();
        InteractionResult result = cir.getReturnValue();
        MSpellCompatDebug.log("lso_canteen", "use_on_return", "player=" + context.getPlayer()
                + ",pos=" + context.getClickedPos() + ",result=" + result
                + ",client=" + level.isClientSide
                + ",enabled=" + MSpellCompatConfig.LSO_CANTEEN_CONSUME_FINITE_WATER.get());
        if (level.isClientSide || !MSpellCompatConfig.LSO_CANTEEN_CONSUME_FINITE_WATER.get()) {
            return;
        }

        if (result == null || !result.consumesAction()) {
            MSpellCompatDebug.log("lso_canteen", "skip", "reason=use_on_not_consumed,result=" + result);
            return;
        }

        mspell$consumeWaterAt(level, context.getClickedPos());
    }

    @Inject(
            method = "use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResultHolder;",
            at = @At(
                    value = "INVOKE",
                    target = "Lsfiomn/legendarysurvivaloverhaul/common/items/drink/CanteenItem;fill(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;)V",
                    shift = At.Shift.AFTER,
                    remap = false
            ),
            require = 1,
            remap = false
    )
    private void mspell$consumeWorldWaterAfterUseFill(
            Level level,
            Player player,
            InteractionHand hand,
            CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir
    ) {
        MSpellCompatDebug.log("lso_canteen", "use_fill_hook", "player=" + player.getGameProfile().getName()
                + ",hand=" + hand + ",client=" + level.isClientSide
                + ",enabled=" + MSpellCompatConfig.LSO_CANTEEN_CONSUME_FINITE_WATER.get());
        if (level.isClientSide || !MSpellCompatConfig.LSO_CANTEEN_CONSUME_FINITE_WATER.get()) {
            return;
        }

        double reach = Math.max(
                3.0D,
                player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) / 2.0D
        );
        HitResult hit = player.pick(reach, 0.0F, true);
        if (hit instanceof BlockHitResult blockHit) {
            mspell$consumeWaterAt(level, blockHit.getBlockPos());
        } else {
            MSpellCompatDebug.log("lso_canteen", "skip", "reason=raycast_not_block,hit=" + hit + ",reach=" + reach);
        }
    }

    @Unique
    private static void mspell$consumeWaterAt(Level level, BlockPos pos) {
        FluidState state = level.getFluidState(pos);
        Fluid fluid = state.getType();
        if (fluid != Fluids.WATER && fluid != Fluids.FLOWING_WATER) {
            MSpellCompatDebug.log("lso_canteen", "skip", "reason=not_supported_water,pos=" + pos
                    + "," + MSpellCompatDebug.describeFluid(level, pos));
            return;
        }

        MSpellCompatDebug.log("lso_canteen", "consume_begin", "pos=" + pos + ",requested=1,state=" + state);
        FFFluidUtils.removeAmountFromFluidAtPosWithRemainder(level, pos, fluid, 1);
        MSpellCompatDebug.log("lso_canteen", "consume_end", () -> "pos=" + pos + ",after="
                + MSpellCompatDebug.describeFluid(level, pos));
    }
}
