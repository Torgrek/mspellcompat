package com.misp.frostypatches.mixin.epicdeathscreen;

import com.epicdeathscreen.client.DeathCinematicController;
import com.epicdeathscreen.config.ModClientConfig;
import com.misp.frostypatches.util.StateKeeper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.concurrent.ThreadLocalRandom;

@SuppressWarnings("unused") // Убирает предупреждения о неиспользуемом классе и методах
@Mixin(value = DeathCinematicController.class, remap = false)
public class DeathCinematicControllerMixin {

    @Inject(method = "begin", at = @At("HEAD"), cancellable = true)
    private static void onBeginHead(Minecraft minecraft, CallbackInfo ci) {
        if (StateKeeper.isItNotHardcore(minecraft)) return;

        if (minecraft.player != null) {
            ci.cancel();

            DeathControllerBridge.callStopSounds(minecraft);
            DeathControllerBridge.setActive(true);
            DeathControllerBridge.setRespawnSent(false);
            DeathControllerBridge.setAwaiting(false);
            DeathControllerBridge.setTicks(0);
            DeathControllerBridge.setRespawnTick(-1);

            try {
                Class<?> themeClass = Class.forName("com.epicdeathscreen.client.DeathTheme");

                java.lang.reflect.Method fromSource = null;
                for (java.lang.reflect.Method m : themeClass.getDeclaredMethods()) {
                    if (m.getName().equals("fromSource") && m.getParameterCount() == 1) {
                        fromSource = m;
                        break;
                    }
                }

                if (fromSource != null) {
                    fromSource.setAccessible(true);
                    Object themeObj = fromSource.invoke(null, minecraft.player.getLastDamageSource());

                    java.lang.reflect.Field themeField = DeathCinematicController.class.getDeclaredField("theme");
                    themeField.setAccessible(true);
                    themeField.set(null, themeObj);

                    java.lang.reflect.Method getCount = null;
                    for (java.lang.reflect.Method m : themeClass.getDeclaredMethods()) {
                        if (m.getName().equals("messageCount") && m.getParameterCount() == 0) {
                            getCount = m;
                            break;
                        }
                    }

                    if (getCount != null) {
                        getCount.setAccessible(true);
                        int count = (int) getCount.invoke(themeObj);
                        DeathControllerBridge.setMessageIndex(ThreadLocalRandom.current().nextInt(1, count + 1));
                    } else {
                        StateKeeper.LOGGER.warn("[EpicDeathScreenHardcorePatch] Can't find messageCount method in DeathTheme");
                        DeathControllerBridge.setMessageIndex(1);
                    }
                }
            } catch (Exception e) {
                StateKeeper.LOGGER.error("[EpicDeathScreenHardcorePatch] Failed to reflect EpicDeathScreen on hardcore death: ", e);
            }

            if (minecraft.player.shouldShowDeathScreen()) {
                minecraft.setScreen(null);
            }

            DeathControllerBridge.callStartSounds(minecraft);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private static void onTick(Minecraft minecraft, CallbackInfo ci) {
        if (StateKeeper.isItNotHardcore(minecraft)) return;

        if (minecraft.level != null && minecraft.player != null) {
            ci.cancel();

            if (DeathControllerBridge.getActive()) {
                if (minecraft.screen instanceof DeathScreen) {
                    minecraft.setScreen(null);
                }

                int currentTicks = DeathControllerBridge.getTicks() + 1;
                DeathControllerBridge.setTicks(currentTicks);

                int respawnStartTick = DeathControllerBridge.callRespawnStartTick();
                int sceneDurationTicks = ModClientConfig.sceneDurationTicks();
                boolean respawnSent = DeathControllerBridge.getRespawnSent();

                if (ModClientConfig.autoRespawn() && !respawnSent && currentTicks >= respawnStartTick) {
                    frostypatches$triggerHardcoreRespawn(minecraft);
                }

                if (!ModClientConfig.autoRespawn() && !DeathControllerBridge.getAwaiting() && currentTicks >= sceneDurationTicks) {
                    DeathControllerBridge.callStopSounds(minecraft);
                    DeathControllerBridge.setAwaiting(true);
                }

                int savedRespawnTick = DeathControllerBridge.getRespawnTick();
                if (respawnSent && savedRespawnTick >= 0 && currentTicks >= savedRespawnTick + ModClientConfig.fadeOutTicks()) {
                    if (minecraft.screen != null && minecraft.screen.getClass().getSimpleName().equals("DeathCinematicScreen")) {
                        minecraft.setScreen(null);
                    }
                    DeathControllerBridge.callReset(minecraft);
                }
            }
        } else {
            DeathControllerBridge.callReset(minecraft);
        }
    }

    @Inject(method = "confirmManualRespawn", at = @At("HEAD"), cancellable = true)
    private static void onConfirmManualRespawn(Minecraft minecraft, CallbackInfo ci) {
        if (StateKeeper.isItNotHardcore(minecraft)) return;

        if (DeathControllerBridge.getActive() && DeathControllerBridge.getAwaiting() && !DeathControllerBridge.getRespawnSent()) {
            ci.cancel();
            frostypatches$triggerHardcoreRespawn(minecraft);
        }
    }

    @Unique
    private static void frostypatches$triggerHardcoreRespawn(Minecraft minecraft) {
        DeathControllerBridge.callStopSounds(minecraft);

        if (minecraft.player != null) {
            if (minecraft.gameMode != null) {
                minecraft.gameMode.setLocalMode(net.minecraft.world.level.GameType.SPECTATOR);
            }

            minecraft.player.onGameModeChanged(net.minecraft.world.level.GameType.SPECTATOR);
            minecraft.player.respawn();
        }

        minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE, 0.9F, 0.8F));

        DeathControllerBridge.setRespawnSent(true);
        DeathControllerBridge.setAwaiting(false);
        DeathControllerBridge.setRespawnTick(DeathControllerBridge.getTicks());
    }
}
