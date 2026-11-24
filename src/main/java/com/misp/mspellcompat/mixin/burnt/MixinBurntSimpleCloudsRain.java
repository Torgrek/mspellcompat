package com.misp.mspellcompat.mixin.burnt;

import com.misp.mspellcompat.config.MSpellCompatConfig;
import com.misp.mspellcompat.util.MSpellCompatDebug;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.pixelbank.burnt.util.BurntBlockUtil", remap = false)
public abstract class MixinBurntSimpleCloudsRain {
    @Inject(
            method = "rainCanReach(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;)Z",
            at = @At("RETURN"),
            cancellable = true,
            require = 1,
            remap = false
    )
    private static void mspell$requireLocalSimpleCloudsRain(
            LevelAccessor level,
            BlockPos pos,
            CallbackInfoReturnable<Boolean> cir
    ) {
        boolean originalResult = cir.getReturnValueZ();
        if (!originalResult) {
            if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                MSpellCompatDebug.log("burnt_rain", "rain_can_reach",
                        "pos=" + pos + ",original=false,final=false,reason=original_rejected");
            }
            return;
        }
        if (level.isClientSide()) {
            if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                MSpellCompatDebug.log("burnt_rain", "rain_can_reach",
                        "pos=" + pos + ",original=true,client=true,final=true,reason=client_side");
            }
            return;
        }
        boolean featureEnabled = MSpellCompatConfig.BURNT_REQUIRE_LOCAL_SIMPLE_CLOUDS_RAIN.get();
        if (!featureEnabled) {
            if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                MSpellCompatDebug.log("burnt_rain", "rain_can_reach",
                        "pos=" + pos + ",original=true,featureEnabled=false,final=true,reason=config_disabled");
            }
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                MSpellCompatDebug.log("burnt_rain", "rain_can_reach",
                        "pos=" + pos + ",original=true,featureEnabled=true,final=true,reason=not_server_level");
            }
            return;
        }

        CloudManager<ServerLevel> clouds = CloudManager.get(serverLevel);
        if (clouds.shouldUseVanillaWeather()) {
            boolean result = serverLevel.isRainingAt(pos);
            cir.setReturnValue(result);
            if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                MSpellCompatDebug.log("burnt_rain", "rain_can_reach_override",
                        "mode=vanilla,pos=" + pos + ",original=" + originalResult + ",final=" + result);
            }
            return;
        }

        var precipitation = clouds.getPrecipitationAt(pos);
        boolean rainingHere = Boolean.TRUE.equals(precipitation.getLeft())
                && precipitation.getRight() == Biome.Precipitation.RAIN;
        cir.setReturnValue(rainingHere);
        if (MSpellCompatDebug.isDebugLoggingEnabled()) {
            MSpellCompatDebug.log("burnt_rain", "rain_can_reach_override",
                    "mode=simpleclouds,pos=" + pos + ",original=" + originalResult
                            + ",precipitation=" + precipitation + ",final=" + rainingHere);
        }
    }
}
