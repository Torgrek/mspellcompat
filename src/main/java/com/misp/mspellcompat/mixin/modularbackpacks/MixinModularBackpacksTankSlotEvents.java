package com.misp.mspellcompat.mixin.modularbackpacks;

import com.misp.mspellcompat.modularbackpacks.ModularBackpacksFractionalTankTransfer;
import com.misp.mspellcompat.util.MSpellCompatDebug;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.Arrays;

@Pseudo
@Mixin(targets = "it.mralxart.modular_backpacks.client.menu.BackpackMenu", remap = false)
public abstract class MixinModularBackpacksTankSlotEvents {
    @Unique
    private static final int MSPELLCOMPAT$MODULE_SLOTS = 4;

    @Shadow
    public IItemHandler handler;

    @Shadow(remap = false)
    public abstract void syncModuleStackFromPassiveTick(int moduleIndex, ItemStack moduleStack);

    @Unique
    private ItemStack[] mspellcompat$beforeTankModules = new ItemStack[MSPELLCOMPAT$MODULE_SLOTS];

    @Unique
    private ItemStack[] mspellcompat$beforeTankInputs = new ItemStack[MSPELLCOMPAT$MODULE_SLOTS];

    @Inject(
            method = "clicked(IILnet/minecraft/world/inventory/ClickType;Lnet/minecraft/world/entity/player/Player;)V",
            at = @At("HEAD"),
            remap = false,
            require = 1
    )
    private void mspellcompat$captureTankInputsBeforeClick(int slotId, int button,
            net.minecraft.world.inventory.ClickType clickType, Player player, CallbackInfo ci) {
        mspellcompat$clearPending();
        if (player == null || player.level().isClientSide || handler == null) {
            return;
        }

        int count = Math.min(MSPELLCOMPAT$MODULE_SLOTS, handler.getSlots());
        for (int moduleSlot = 0; moduleSlot < count; moduleSlot++) {
            ItemStack tankStack = handler.getStackInSlot(moduleSlot);
            if (!ModularBackpacksFractionalTankTransfer.isTankStack(tankStack)) {
                continue;
            }
            mspellcompat$beforeTankModules[moduleSlot] = tankStack.copy();
            mspellcompat$beforeTankInputs[moduleSlot] =
                    ModularBackpacksFractionalTankTransfer.snapshotTankInput(player, tankStack);
        }
    }

    @Inject(
            method = "clicked(IILnet/minecraft/world/inventory/ClickType;Lnet/minecraft/world/entity/player/Player;)V",
            at = @At("RETURN"),
            remap = false,
            require = 1
    )
    private void mspellcompat$handleChangedTankInputs(int slotId, int button,
            net.minecraft.world.inventory.ClickType clickType, Player player, CallbackInfo ci) {
        try {
            if (player == null || player.level().isClientSide || handler == null
                    || !(handler instanceof IItemHandlerModifiable)) {
                return;
            }

            int count = Math.min(MSPELLCOMPAT$MODULE_SLOTS, handler.getSlots());
            for (int moduleSlot = 0; moduleSlot < count; moduleSlot++) {
                ItemStack beforeTank = mspellcompat$beforeTankModules[moduleSlot];
                ItemStack beforeInput = mspellcompat$beforeTankInputs[moduleSlot];
                if (beforeTank == null || beforeTank.isEmpty() || beforeInput == null) {
                    continue;
                }

                ItemStack tankStack = handler.getStackInSlot(moduleSlot);
                if (!ModularBackpacksFractionalTankTransfer.isTankStack(tankStack)) {
                    continue;
                }

                ItemStack afterInput = ModularBackpacksFractionalTankTransfer
                        .snapshotTankInput(player, tankStack);

                if (ItemStack.matches(beforeInput, afterInput)) {
                    continue;
                }

                boolean transferred = ModularBackpacksFractionalTankTransfer
                        .onTankSlotInteraction(player, tankStack);
                boolean guardCacheChanged = ModularBackpacksFractionalTankTransfer
                        .refreshNativeTickCache(player, tankStack);

                if (transferred || guardCacheChanged) {

                    syncModuleStackFromPassiveTick(moduleSlot, tankStack.copy());
                    mspellcompat$saveBackpackHandlerAndSync(player, tankStack);
                }
            }
        } finally {
            mspellcompat$clearPending();
        }
    }

    @Unique
    private void mspellcompat$clearPending() {
        Arrays.fill(mspellcompat$beforeTankModules, ItemStack.EMPTY);
        Arrays.fill(mspellcompat$beforeTankInputs, null);
    }

    @Unique
    private void mspellcompat$saveBackpackHandlerAndSync(Player player, ItemStack tankStack) {
        try {
            ClassLoader loader = tankStack.getItem().getClass().getClassLoader();
            Class<?> equippedBackpackHelper = Class.forName(
                    "it.mralxart.modular_backpacks.compat.EquippedBackpackHelper", false, loader);
            ItemStack backpackStack = (ItemStack) equippedBackpackHelper
                    .getMethod("getStack", Player.class)
                    .invoke(null, player);
            if (backpackStack == null || backpackStack.isEmpty()) {
                return;
            }

            Class<?> backpackItem = Class.forName(
                    "it.mralxart.modular_backpacks.items.BackpackItem", false, loader);
            backpackItem.getMethod("saveBackpackHandlerAndSync", Player.class, ItemStack.class, Object.class)
                    .invoke(null, player, backpackStack, handler);
        } catch (ReflectiveOperationException | SecurityException exception) {
            if (MSpellCompatDebug.isDebugLoggingEnabled()) {
                MSpellCompatDebug.debugException(
                        "[MSpellCompat/MB-FF] Failed to save/sync Modular Backpacks after tank-slot transfer",
                        exception);
            }
        }
    }
}
