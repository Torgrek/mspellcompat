package com.misp.frostypatches.mixin.epicdeathscreen;

import com.epicdeathscreen.client.DeathCinematicController;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = DeathCinematicController.class, remap = false)
public interface DeathControllerBridge {

    @Accessor("active") static boolean getActive() { throw new AssertionError(); }
    @Accessor("active") static void setActive(boolean v) { throw new AssertionError(); }

    @Accessor("ticks") static int getTicks() { throw new AssertionError(); }
    @Accessor("ticks") static void setTicks(int v) { throw new AssertionError(); }

    @Accessor("respawnSent") static boolean getRespawnSent() { throw new AssertionError(); }
    @Accessor("respawnSent") static void setRespawnSent(boolean v) { throw new AssertionError(); }

    @Accessor("awaitingManualRespawn") static boolean getAwaiting() { throw new AssertionError(); }
    @Accessor("awaitingManualRespawn") static void setAwaiting(boolean v) { throw new AssertionError(); }

    @Accessor("respawnTick") static int getRespawnTick() { throw new AssertionError(); }
    @Accessor("respawnTick") static void setRespawnTick(int v) { throw new AssertionError(); }

    @Accessor("messageIndex") static void setMessageIndex(int v) { throw new AssertionError(); }

    @Invoker("stopSounds") static void callStopSounds(Minecraft mc) { throw new AssertionError(); }
    @Invoker("startSounds") static void callStartSounds(Minecraft mc) { throw new AssertionError(); }
    @Invoker("reset") static void callReset(Minecraft mc) { throw new AssertionError(); }

    @Invoker("respawnStartTick") static int callRespawnStartTick() { throw new AssertionError(); }
}
