package com.misp.mspellcompat.mixin.lso;

import com.misp.mspellcompat.config.MSpellCompatConfig;
import com.misp.mspellcompat.util.MSpellCompatDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import sfiomn.legendarysurvivaloverhaul.api.data.json.JsonThirstBlock;
import sfiomn.legendarysurvivaloverhaul.api.thirst.ThirstUtil;
import sfiomn.legendarysurvivaloverhaul.config.Config;
import traben.flowing_fluids.FFFluidUtils;

@Mixin(
        targets = "sfiomn.legendarysurvivaloverhaul.network.payloads.DrinkBlockFluidMessage",
        remap = false
)
public abstract class MixinLsoFiniteWaterDrinking {
    @Redirect(
            method = "DrinkWaterOnServer(Lnet/minecraft/world/entity/player/Player;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lsfiomn/legendarysurvivaloverhaul/api/thirst/ThirstUtil;getFluidThirstLookedAt(Lnet/minecraft/world/entity/player/Player;D)Lsfiomn/legendarysurvivaloverhaul/api/data/json/JsonThirstBlock;",
                    remap = false
            ),
            require = 1,
            remap = false
    )
    private static JsonThirstBlock mspell$consumeWaterForAcceptedDrink(Player player, double reach) {
        Level level = player.level();
        JsonThirstBlock thirstBlock = ThirstUtil.getFluidThirstLookedAt(player, reach);
        if (MSpellCompatDebug.isDebugLoggingEnabled()) {
            MSpellCompatDebug.log("lso_drink", "target_resolved", "player=" + player.getGameProfile().getName()
                    + ",reach=" + reach + ",client=" + level.isClientSide + ",thirstBlock=" + thirstBlock);
        }
        if (thirstBlock == null || level.isClientSide) {
            if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                MSpellCompatDebug.log("lso_drink", "skip", "reason=" + (thirstBlock == null ? "no_lso_target" : "client_side"));
            }
            return thirstBlock;
        }

        boolean drinkingRain = player.getViewXRot(1.0F) < -60.0F
                && level.isRainingAt(player.blockPosition().above());
        if (drinkingRain) {
            if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                MSpellCompatDebug.log("lso_drink", "skip", "reason=rain_drinking,player="
                        + player.getGameProfile().getName());
            }
            return thirstBlock;
        }

        boolean consumeEnabled = MSpellCompatConfig.LSO_CONSUME_FINITE_WATER_WHEN_DRINKING.get();
        if (!consumeEnabled) {
            if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                MSpellCompatDebug.log("lso_drink", "skip", "reason=config_disabled,consumeEnabled=false");
            }
            return thirstBlock;
        }

        boolean thirstEnabled = Config.Baked.thirstEnabled;
        if (!thirstEnabled) {
            if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                MSpellCompatDebug.log("lso_drink", "skip", "reason=thirst_disabled,thirstEnabled=false");
            }
            return thirstBlock;
        }

        boolean thirstActive = ThirstUtil.isThirstActive(player);
        if (!thirstActive) {
            if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                MSpellCompatDebug.log("lso_drink", "skip", "reason=thirst_inactive,thirstActive=false");
            }
            return thirstBlock;
        }

        HitResult hit = player.pick(reach, 0.0F, true);
        if (!(hit instanceof BlockHitResult blockHit)) {
            if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                MSpellCompatDebug.log("lso_drink", "skip", "reason=raycast_not_block,hit=" + hit);
            }
            return thirstBlock;
        }

        BlockPos waterPos = blockHit.getBlockPos();
        var before = level.getFluidState(waterPos);
        Fluid fluid = before.getType();
        if (fluid != Fluids.WATER && fluid != Fluids.FLOWING_WATER) {
            if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                MSpellCompatDebug.log("lso_drink", "skip", "reason=not_supported_water,pos=" + waterPos
                        + ",before=" + MSpellCompatDebug.describeFluid(level, waterPos));
            }
            return thirstBlock;
        }

        int requested = MSpellCompatConfig.LSO_WATER_UNITS_PER_DRINK.get();
        if (MSpellCompatDebug.isDebugLoggingEnabled()) {
            MSpellCompatDebug.log("lso_drink", "consume_begin", "pos=" + waterPos + ",requested=" + requested
                    + ",before=" + MSpellCompatDebug.describeFluid(level, waterPos));
        }
        FFFluidUtils.removeAmountFromFluidAtPosWithRemainder(level, waterPos, fluid, requested);
        MSpellCompatDebug.log("lso_drink", "consume_end", () -> "pos=" + waterPos + ",requested=" + requested
                + ",after=" + MSpellCompatDebug.describeFluid(level, waterPos));
        return thirstBlock;
    }
}
