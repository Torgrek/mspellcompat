package com.misp.frostypatches.mixin.epicdeathscreen;

import com.epicdeathscreen.client.DeathCinematicController;
import com.epicdeathscreen.client.DeathCinematicRenderer;
import com.misp.frostypatches.util.StateKeeper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DeathCinematicRenderer.class, remap = false)
public class DeathCinematicRendererMixin {

    @Inject(method = "onScreenOpening", at = @At("HEAD"), cancellable = true)
    private static void onScreenOpeningHardcorePatch(ScreenEvent.Opening event, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (StateKeeper.isItNotHardcore(minecraft)) return;

        if (minecraft.player != null && minecraft.level != null) {
            if (DeathCinematicController.shouldCancelDeathScreen(event.getNewScreen())) {
                DeathCinematicController.begin(minecraft);

                try {
                    Class<?> screenClass = Class.forName("com.epicdeathscreen.client.DeathCinematicScreen");
                    java.lang.reflect.Constructor<?> constructor = screenClass.getDeclaredConstructor();
                    constructor.setAccessible(true);
                    Screen originalScreen = (Screen) constructor.newInstance();
                    event.setNewScreen(originalScreen);
                } catch (Exception e) {
                    StateKeeper.LOGGER.error("[EpicDeathScreenHardcorePatch] Epicdeathscreen - screen build error", e);
                }
            }
        }
        ci.cancel();
    }
}
