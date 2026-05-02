package com.misp.mspellcompat.mixin.sable;

import com.misp.mspellcompat.sable.SableFluidTransport;
import com.misp.mspellcompat.util.MSpellCompatDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FlowingFluid.class, priority = 1500, remap = false)
public abstract class MixinSablePlotFluidTick {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 1, remap = false, order = 0)
    private void mspellcompat$skipSablePlotTick(Level level, BlockPos pos, FluidState state,
                                                CallbackInfo ci) {
        boolean suppressed = SableFluidTransport.shouldSuppressManagedFluidTick(level, pos, state, "scheduled tick");
        MSpellCompatDebug.log("fluid_tick", "scheduled", () -> MSpellCompatDebug.describeLevel(level)
                + ",decision=" + (suppressed ? "cancel" : "continue")
                + "," + MSpellCompatDebug.describeFluid(level, pos));
        SableFluidTransport.traceFluidTick(level, pos, state, "scheduled tick", suppressed);
        if (suppressed) ci.cancel();
    }

}
