package com.misp.mspellcompat.mixin.treephysics;

import com.misp.mspellcompat.sable.TreePhysicsDamageBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.farcr.treephysics.api.util.FloodFillUtil")
public abstract class MixinTreePhysicsFloodFillGuard {
    @Inject(method = "blockBroken", at = @At("HEAD"))
    private static void mspellcompat$enterTreePhysicsOperation(CallbackInfo ci) {
        TreePhysicsDamageBridge.enterTreePhysicsOperation();
    }

    @Inject(method = "blockBroken", at = @At("RETURN"))
    private static void mspellcompat$exitTreePhysicsOperation(CallbackInfo ci) {
        TreePhysicsDamageBridge.exitTreePhysicsOperation();
    }
}
