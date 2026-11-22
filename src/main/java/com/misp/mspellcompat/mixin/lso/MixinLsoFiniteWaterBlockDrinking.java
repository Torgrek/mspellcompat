package com.misp.mspellcompat.mixin.lso;

import com.misp.mspellcompat.config.MSpellCompatConfig;
import com.misp.mspellcompat.util.MSpellCompatDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import traben.flowing_fluids.FFFluidUtils;

@Mixin(targets = "sfiomn.legendarysurvivaloverhaul.common.events.CommonNeoForgeEvents", remap = false)
public abstract class MixinLsoFiniteWaterBlockDrinking {

    @Unique
    private static final String MSpell$TAKE_DRINK_TARGET =
            "Lsfiomn/legendarysurvivaloverhaul/api/thirst/ThirstUtil;takeDrink(" +
            "Lnet/minecraft/world/entity/player/Player;IFLjava/util/List;)V";

    @Inject(
            method = "onRightClickBlock(Lnet/neoforged/neoforge/event/entity/player/PlayerInteractEvent$RightClickBlock;)V",
            at = @At(value = "INVOKE", target = MSpell$TAKE_DRINK_TARGET,
                    ordinal = 0, shift = At.Shift.AFTER, remap = false),
            require = 1,
            remap = false
    )
    private static void mspell$consumeWaterAfterFirstBlockDrink(
            PlayerInteractEvent.RightClickBlock event,
            CallbackInfo ci
    ) {
        mspell$consumeWaterAfterAcceptedBlockDrink(event);
    }

    @Inject(
            method = "onRightClickBlock(Lnet/neoforged/neoforge/event/entity/player/PlayerInteractEvent$RightClickBlock;)V",
            at = @At(value = "INVOKE", target = MSpell$TAKE_DRINK_TARGET,
                    ordinal = 1, shift = At.Shift.AFTER, remap = false),
            require = 1,
            remap = false
    )
    private static void mspell$consumeWaterAfterSecondBlockDrink(
            PlayerInteractEvent.RightClickBlock event,
            CallbackInfo ci
    ) {
        mspell$consumeWaterAfterAcceptedBlockDrink(event);
    }

    @Unique
    private static void mspell$consumeWaterAfterAcceptedBlockDrink(
            PlayerInteractEvent.RightClickBlock event
    ) {
        Level level = event.getLevel();
        Player player = event.getEntity();

        MSpellCompatDebug.log("lso_block_drink", "accepted_drink_hook",
                "player=" + player.getGameProfile().getName() + ",eventPos=" + event.getPos()
                        + "," + MSpellCompatDebug.describeLevel(level));
        if (level.isClientSide) {
            MSpellCompatDebug.log("lso_block_drink", "skip", "reason=client_side");
            return;
        }

        if (!MSpellCompatConfig.LSO_CONSUME_FINITE_WATER_WHEN_DRINKING.get()) {
            MSpellCompatDebug.log("lso_block_drink", "skip", "reason=config_disabled");
            return;
        }

        boolean drinkingRain = player.getViewXRot(1.0F) < -60.0F
                && level.isRainingAt(player.blockPosition().above());
        if (drinkingRain) {
            MSpellCompatDebug.log("lso_block_drink", "skip", "reason=rain_drinking");
            return;
        }

        double reach = (float) (player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) / 2.0D);
        HitResult hit = player.pick(reach, 0.0F, true);
        if (!(hit instanceof BlockHitResult blockHit)) {
            MSpellCompatDebug.log("lso_block_drink", "skip", "reason=raycast_not_block,reach=" + reach + ",hit=" + hit);
            return;
        }

        BlockPos waterPos = blockHit.getBlockPos();
        FluidState before = level.getFluidState(waterPos);
        Fluid fluid = before.getType();
        if (fluid != Fluids.WATER && fluid != Fluids.FLOWING_WATER) {
            MSpellCompatDebug.log("lso_block_drink", "skip", "reason=not_supported_water,pos=" + waterPos
                    + "," + MSpellCompatDebug.describeFluid(level, waterPos));
            return;
        }

        int requested = MSpellCompatConfig.LSO_WATER_UNITS_PER_DRINK.get();
        MSpellCompatDebug.log("lso_block_drink", "consume_begin", "player=" + player.getGameProfile().getName()
                + ",pos=" + waterPos + ",requested=" + requested + ",before=" + before);
        FFFluidUtils.removeAmountFromFluidAtPosWithRemainder(level, waterPos, fluid, requested);
        MSpellCompatDebug.log("lso_block_drink", "consume_end", () -> "pos=" + waterPos + ",after="
                + MSpellCompatDebug.describeFluid(level, waterPos));
    }
}
