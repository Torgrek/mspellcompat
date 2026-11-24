package com.misp.mspellcompat.mixin.lso;

import com.misp.mspellcompat.config.MSpellCompatConfig;
import com.misp.mspellcompat.lso.LsoHeatSnowMelting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SnowLayerBlock.class)
public abstract class MixinSnowLayerTemperatureMelt {
    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void mspellcompat$handleSnowLayerTemperature(
            BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci
    ) {
        if (!MSpellCompatConfig.LSO_HEAT_SNOW_MELTING_ENABLED.get()
                || !ModList.get().isLoaded("legendarysurvivaloverhaul")
                || !ModList.get().isLoaded("flowing_fluids")) return;

        LsoHeatSnowMelting.tryMelt(level, pos, state, random);
        ci.cancel();
    }
}
