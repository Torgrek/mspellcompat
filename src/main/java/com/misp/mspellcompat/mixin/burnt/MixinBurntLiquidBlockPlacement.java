package com.misp.mspellcompat.mixin.burnt;

import com.misp.mspellcompat.burnt.BurntWaterPlacementHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LiquidBlock.class)
public abstract class MixinBurntLiquidBlockPlacement {
    @Inject(method = "onPlace", at = @At("TAIL"), require = 1)
    private void mspell$checkBurntAtWaterPlacement(
            BlockState state,
            Level level,
            BlockPos pos,
            BlockState oldState,
            boolean isMoving,
            CallbackInfo ci
    ) {
        BurntWaterPlacementHandler.onWaterPlaced(level, pos);
    }
}
