package com.misp.mspellcompat.mixin.diagnostics;

import com.misp.mspellcompat.util.MSpellCompatDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BucketItem.class)
public abstract class MixinBucketItemDiagnostics {
    @Inject(
            method = "use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResultHolder;",
            at = @At("HEAD"),
            require = 1
    )
    private void mspellcompat$bucketUseHead(
            Level level,
            Player player,
            InteractionHand hand,
            CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir
    ) {
        MSpellCompatDebug.log("bucket", "use_head", () -> mspellcompat$describeUse(level, player, hand, "phase=head"));
    }

    @Inject(
            method = "use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResultHolder;",
            at = @At("RETURN"),
            require = 1
    )
    private void mspellcompat$bucketUseReturn(
            Level level,
            Player player,
            InteractionHand hand,
            CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir
    ) {
        InteractionResultHolder<ItemStack> result = cir.getReturnValue();
        String resultInfo = result == null ? "result=null"
                : "result=" + result.getResult() + ",returnedItem=" + mspellcompat$itemId(result.getObject())
                + ",returnedCount=" + result.getObject().getCount();
        MSpellCompatDebug.log("bucket", "use_return", () -> mspellcompat$describeUse(level, player, hand,
                "phase=return," + resultInfo));
    }

    @Unique
    private static String mspellcompat$describeUse(Level level, Player player, InteractionHand hand, String phase) {
        ItemStack held = player.getItemInHand(hand);
        HitResult hit = player.pick(5.0D, 0.0F, true);
        String target;
        if (hit instanceof BlockHitResult blockHit) {
            BlockPos pos = blockHit.getBlockPos();
            target = "hitType=block,hit=" + pos + ",hitFace=" + blockHit.getDirection()
                    + "," + MSpellCompatDebug.describeFluid(level, pos);
        } else {
            target = "hitType=" + hit.getType() + ",hitLocation=" + hit.getLocation();
        }
        return MSpellCompatDebug.describeLevel(level) + ",player=" + player.getGameProfile().getName()
                + ",playerPos=" + player.blockPosition() + ",hand=" + hand
                + ",heldItem=" + mspellcompat$itemId(held) + ",heldCount=" + held.getCount()
                + ",eye=" + player.getEyePosition() + ",look=" + player.getLookAngle()
                + "," + target + "," + phase;
    }

    @Unique
    private static String mspellcompat$itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "empty";
        try {
            return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        } catch (Throwable ignored) {
            return String.valueOf(stack.getItem());
        }
    }
}
