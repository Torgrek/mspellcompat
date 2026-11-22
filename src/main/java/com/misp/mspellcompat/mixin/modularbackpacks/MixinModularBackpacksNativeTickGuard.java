package com.misp.mspellcompat.mixin.modularbackpacks;

import com.misp.mspellcompat.modularbackpacks.ModularBackpacksFractionalTankTransfer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "it.mralxart.modular_backpacks.items.CopperTankItem", remap = false)
public abstract class MixinModularBackpacksNativeTickGuard {
    @Inject(
            method = "processTank(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 1
    )
    private static void mspellcompat$guardNativeTankTransfer(Player player, ItemStack tankStack,
            CallbackInfoReturnable<Boolean> cir) {
        ModularBackpacksFractionalTankTransfer.NativeTickDecision decision =
                ModularBackpacksFractionalTankTransfer.prepareNativeTick(player, tankStack);
        if (decision == ModularBackpacksFractionalTankTransfer.NativeTickDecision.SUPPRESS) {
            cir.setReturnValue(false);
        } else if (decision == ModularBackpacksFractionalTankTransfer.NativeTickDecision.INITIALIZED) {

            cir.setReturnValue(true);
        }
    }
}
