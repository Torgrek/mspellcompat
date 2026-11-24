package com.misp.mspellcompat.modularbackpacks;

import com.misp.mspellcompat.config.MSpellCompatConfig;
import com.misp.mspellcompat.util.MSpellCompatDebug;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import traben.flowing_fluids.FFBucketItem;
import traben.flowing_fluids.api.FlowingFluidsAPI;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ModularBackpacksFractionalTankTransfer {
    private static final int INPUT_SLOT = 0;
    private static final int OUTPUT_SLOT = 1;
    private static final int LEVELS_PER_BUCKET = FlowingFluidsAPI.FLUID_LEVELS_PER_BLOCK;
    private static final int MB_PER_LEVEL = FluidType.BUCKET_VOLUME / LEVELS_PER_BUCKET;
    private static final String LEGACY_TRANSFER_COOLDOWN_TAG = "TankTransferCooldownUntil";
    private static final String LEGACY_EVENT_HANDLED_TAG = "MSpellCompatFractionalFFEventHandled";

    private static final Map<Fluid, Optional<Item>> BUCKET_CACHE = new ConcurrentHashMap<>();
    private static volatile FlowingFluidsAPI cachedApi;
    private static volatile TankAccessors cachedTankAccessors;
    private static volatile boolean tankAccessorsResolutionFailed;
    private static final String TANK_ITEM_CLASS = "it.mralxart.modular_backpacks.items.CopperTankItem";

    private ModularBackpacksFractionalTankTransfer() {
    }

    private static void debugLog(String message, Object... arguments) {
        MSpellCompatDebug.debug(message, arguments);
    }

    private static void debugException(String message, Throwable exception) {
        MSpellCompatDebug.debugException(message, exception);
    }

    private static final String NATIVE_GUARD_KNOWN_TAG = "MSpellCompatFractionalFFGuardKnown";
    private static final String NATIVE_GUARD_SUPPRESS_TAG = "MSpellCompatFractionalFFGuardSuppress";

    public enum NativeTickDecision {
        ALLOW,
        SUPPRESS,
        INITIALIZED
    }

    public static NativeTickDecision prepareNativeTick(Player player, ItemStack tankStack) {
        if (!MSpellCompatConfig.MODULAR_BACKPACKS_FRACTIONAL_TRANSFER_ENABLED.get()) return NativeTickDecision.ALLOW;
        if (player == null || tankStack == null || tankStack.isEmpty() || player.level().isClientSide) {
            return NativeTickDecision.ALLOW;
        }

        CustomData customData = tankStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag readOnlyTag = customData.getUnsafe();
        if (readOnlyTag.getBoolean(NATIVE_GUARD_KNOWN_TAG)) {
            return readOnlyTag.getBoolean(NATIVE_GUARD_SUPPRESS_TAG)
                    ? NativeTickDecision.SUPPRESS
                    : NativeTickDecision.ALLOW;
        }

        boolean suppress = computeNativeSuppression(player, tankStack);
        writeNativeGuardCache(tankStack, suppress);
        return NativeTickDecision.INITIALIZED;
    }

    public static boolean refreshNativeTickCache(Player player, ItemStack tankStack) {
        if (!MSpellCompatConfig.MODULAR_BACKPACKS_FRACTIONAL_TRANSFER_ENABLED.get()) return false;
        if (player == null || tankStack == null || tankStack.isEmpty() || player.level().isClientSide) {
            return false;
        }
        return writeNativeGuardCache(tankStack, computeNativeSuppression(player, tankStack));
    }

    public static ItemStack snapshotTankInput(Player player, ItemStack tankStack) {
        if (!MSpellCompatConfig.MODULAR_BACKPACKS_FRACTIONAL_TRANSFER_ENABLED.get()) return ItemStack.EMPTY;
        if (player == null || tankStack == null || tankStack.isEmpty() || !isTankStack(tankStack)) {
            return ItemStack.EMPTY;
        }
        try {
            return createTankInventory(tankStack, player.registryAccess())
                    .getStackInSlot(INPUT_SLOT).copy();
        } catch (RuntimeException exception) {
            debugException("[MSpellCompat/MB-FF] Failed to snapshot Modular Backpacks tank input", exception);
            return ItemStack.EMPTY;
        }
    }

    public static boolean isTankStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Class<?> type = stack.getItem().getClass();
        while (type != null && !TANK_ITEM_CLASS.equals(type.getName())) {
            type = type.getSuperclass();
        }
        return type != null;
    }

    private static boolean computeNativeSuppression(Player player, ItemStack tankStack) {
        FlowingFluidsAPI api = getApi();
        if (!api.isModEnabled() || getTankAccessors(tankStack) == null) {
            return false;
        }

        final ItemStack input;
        try {
            input = createTankInventory(tankStack, player.registryAccess()).getStackInSlot(INPUT_SLOT);
        } catch (RuntimeException exception) {
            debugException("[MSpellCompat/MB-FF] Failed to inspect tank before native processTank", exception);
            return false;
        }

        if (input.isEmpty()) {
            return false;
        }

        if (input.is(Items.BUCKET)) {
            IFluidHandler tank = tankStack.getCapability(Capabilities.FluidHandler.ITEM);
            if (tank == null) {
                return false;
            }
            FluidStack stored = tank.drain(FluidType.BUCKET_VOLUME, IFluidHandler.FluidAction.SIMULATE);
            return !stored.isEmpty() && api.doesModifyThisFluid(stored.getFluid());
        }

        if (input.getItem() instanceof FFBucketItem flowingBucket) {
            Fluid fluid = flowingBucket.ff$getFluid();
            return fluid != null && api.doesModifyThisFluid(fluid);
        }

        return false;
    }

    private static boolean writeNativeGuardCache(ItemStack tankStack, boolean suppress) {
        CustomData customData = tankStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        boolean wasKnown = tag.getBoolean(NATIVE_GUARD_KNOWN_TAG);
        boolean wasSuppressed = tag.getBoolean(NATIVE_GUARD_SUPPRESS_TAG);
        boolean hasLegacyTags = tag.contains(LEGACY_TRANSFER_COOLDOWN_TAG)
                || tag.contains(LEGACY_EVENT_HANDLED_TAG);
        if (wasKnown && wasSuppressed == suppress && !hasLegacyTags) {
            return false;
        }

        tag.putBoolean(NATIVE_GUARD_KNOWN_TAG, true);
        if (suppress) {
            tag.putBoolean(NATIVE_GUARD_SUPPRESS_TAG, true);
        } else {
            tag.remove(NATIVE_GUARD_SUPPRESS_TAG);
        }

        tag.remove(LEGACY_TRANSFER_COOLDOWN_TAG);
        tag.remove(LEGACY_EVENT_HANDLED_TAG);
        tankStack.set(DataComponents.CUSTOM_DATA, tag.isEmpty() ? CustomData.EMPTY : CustomData.of(tag));
        return true;
    }

    public static boolean onTankSlotInteraction(Player player, ItemStack tankStack) {
        if (!MSpellCompatConfig.MODULAR_BACKPACKS_FRACTIONAL_TRANSFER_ENABLED.get()) return false;
        if (player == null || tankStack == null || tankStack.isEmpty() || player.level().isClientSide) {
            return false;
        }

        if (getTankAccessors(tankStack) == null) {
            return false;
        }

        FlowingFluidsAPI api = getApi();
        if (!api.isModEnabled()) {
            return false;
        }

        IFluidHandler tank = tankStack.getCapability(Capabilities.FluidHandler.ITEM);
        if (tank == null) {
            return false;
        }

        ItemStackHandler inventory = createTankInventory(tankStack, player.registryAccess());
        ItemStack input = inventory.getStackInSlot(INPUT_SLOT);
        ItemStack output = inventory.getStackInSlot(OUTPUT_SLOT);

        if (!input.is(Items.BUCKET)
                && input.getItem() instanceof FFBucketItem flowingBucket
                && flowingBucket.ff$getFluid() != null) {
            Fluid fluid = flowingBucket.ff$getFluid();
            if (!api.doesModifyThisFluid(fluid)) {

                return false;
            }

            int bucketUnits = bucketUnits(input);
            if (bucketUnits < 1 || bucketUnits > LEVELS_PER_BUCKET) {
                return false;
            }

            int requestedMb = bucketUnits * MB_PER_LEVEL;
            int simulatedAcceptedMb = tank.fill(
                    new FluidStack(fluid, requestedMb), IFluidHandler.FluidAction.SIMULATE);
            int transferUnits = Math.min(bucketUnits, simulatedAcceptedMb / MB_PER_LEVEL);
            if (transferUnits <= 0) {
                return false;
            }

            int transferMb = transferUnits * MB_PER_LEVEL;
            int remainingUnits = bucketUnits - transferUnits;
            ItemStack containerAfter = flowingBucket.ff$bucketOfAmount(input.copyWithCount(1), remainingUnits);
            boolean resultFitsOutput = canMerge(output, containerAfter);
            boolean replaceInputWithResult = !resultFitsOutput && input.getCount() == 1;
            if (!resultFitsOutput && !replaceInputWithResult) {
                return false;
            }

            int actualFilledMb = tank.fill(
                    new FluidStack(fluid, transferMb), IFluidHandler.FluidAction.EXECUTE);
            if (actualFilledMb != transferMb) {
                if (actualFilledMb > 0) {
                    tank.drain(actualFilledMb, IFluidHandler.FluidAction.EXECUTE);
                }
                return false;
            }

            if (replaceInputWithResult) {
                replaceInputWithResult(player, tankStack, inventory, containerAfter);
            } else {
                moveOneInputToOutput(player, tankStack, inventory, input, output, containerAfter);
            }

            debugLog("[MSpellCompat/MB-FF] bucket->tank fluid={} transferredMb={} remainingBucketUnits={} resultSlot={}",
                    BuiltInRegistries.FLUID.getKey(fluid), transferMb, remainingUnits,
                    replaceInputWithResult ? "input" : "output");
            return true;
        }

        if (input.is(Items.BUCKET)) {
            FluidStack simulatedTankFluid = tank.drain(
                    FluidType.BUCKET_VOLUME, IFluidHandler.FluidAction.SIMULATE);
            if (simulatedTankFluid.isEmpty()
                    || !api.doesModifyThisFluid(simulatedTankFluid.getFluid())) {
                return false;
            }

            int availableUnits = Math.min(LEVELS_PER_BUCKET,
                    simulatedTankFluid.getAmount() / MB_PER_LEVEL);
            if (availableUnits <= 0) {
                return false;
            }

            Item bucketItem = findBucketItem(simulatedTankFluid.getFluid());
            if (!(bucketItem instanceof FFBucketItem flowingBucket)) {
                return false;
            }

            ItemStack bucketAfter = flowingBucket.ff$bucketOfAmount(new ItemStack(bucketItem), availableUnits);
            boolean resultFitsOutput = canMerge(output, bucketAfter);
            boolean replaceInputWithResult = !resultFitsOutput && input.getCount() == 1;
            boolean mergeRemainderIntoOutput = !resultFitsOutput && input.getCount() > 1
                    && canMerge(output, input.copyWithCount(input.getCount() - 1));
            if (!resultFitsOutput && !replaceInputWithResult && !mergeRemainderIntoOutput) {
                return false;
            }

            int transferMb = availableUnits * MB_PER_LEVEL;
            FluidStack actualDrained = tank.drain(transferMb, IFluidHandler.FluidAction.EXECUTE);
            if (actualDrained.isEmpty()
                    || actualDrained.getAmount() != transferMb
                    || !actualDrained.is(simulatedTankFluid.getFluid())) {
                if (!actualDrained.isEmpty()) {
                    tank.fill(actualDrained, IFluidHandler.FluidAction.EXECUTE);
                }
                return false;
            }

            if (mergeRemainderIntoOutput) {
                replaceInputWithFilledBucketAndMergeRemainder(
                        player, tankStack, inventory, input, output, bucketAfter);
            } else if (replaceInputWithResult) {
                replaceInputWithResult(player, tankStack, inventory, bucketAfter);
            } else {
                moveOneInputToOutput(player, tankStack, inventory, input, output, bucketAfter);
            }

            debugLog("[MSpellCompat/MB-FF] tank->bucket fluid={} transferredMb={} remainingTankMb={} resultSlot={}",
                    BuiltInRegistries.FLUID.getKey(actualDrained.getFluid()), transferMb,
                    Math.max(0, simulatedTankFluid.getAmount() - transferMb),
                    mergeRemainderIntoOutput ? "input+output-remainder" : (replaceInputWithResult ? "input" : "output"));
            return true;
        }

        return false;
    }

    private record TankAccessors(
            Class<?> owner,
            Method createInventory,
            Method saveInventory) {
    }

    private static TankAccessors getTankAccessors(ItemStack tankStack) {
        if (tankStack == null || tankStack.isEmpty()) {
            return null;
        }

        Class<?> owner = tankStack.getItem().getClass();
        while (owner != null && !TANK_ITEM_CLASS.equals(owner.getName())) {
            owner = owner.getSuperclass();
        }
        if (owner == null) {
            return null;
        }

        if (tankAccessorsResolutionFailed) {
            return null;
        }

        TankAccessors cached = cachedTankAccessors;
        if (cached != null && cached.owner() == owner) {
            return cached;
        }

        synchronized (ModularBackpacksFractionalTankTransfer.class) {
            if (tankAccessorsResolutionFailed) {
                return null;
            }
            cached = cachedTankAccessors;
            if (cached != null && cached.owner() == owner) {
                return cached;
            }
            try {
                cached = new TankAccessors(
                        owner,
                        owner.getMethod("createInventory", ItemStack.class, HolderLookup.Provider.class),
                        owner.getMethod("saveInventory", ItemStack.class, ItemStackHandler.class, HolderLookup.Provider.class));
                cachedTankAccessors = cached;
                return cached;
            } catch (ReflectiveOperationException | SecurityException exception) {
                tankAccessorsResolutionFailed = true;
                debugException("[MSpellCompat/MB-FF] Could not resolve Modular Backpacks 0.2.12 tank API", exception);
                return null;
            }
        }
    }

    private static Object invokeTankStatic(ItemStack tankStack, Method method, Object... arguments) {
        TankAccessors accessors = getTankAccessors(tankStack);
        if (accessors == null || method == null) {
            throw new IllegalStateException("Modular Backpacks tank API is unavailable");
        }
        try {
            return method.invoke(null, arguments);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot access Modular Backpacks tank API method " + method.getName(), exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw new IllegalStateException("Modular Backpacks tank API method failed: " + method.getName(), cause);
        }
    }

    private static ItemStackHandler createTankInventory(ItemStack tankStack, HolderLookup.Provider registries) {
        TankAccessors accessors = getTankAccessors(tankStack);
        Object result = invokeTankStatic(tankStack, accessors == null ? null : accessors.createInventory(), tankStack, registries);
        if (result instanceof ItemStackHandler inventory) {
            return inventory;
        }
        throw new IllegalStateException("Modular Backpacks createInventory returned an unexpected type");
    }

    private static void saveTankInventory(ItemStack tankStack, ItemStackHandler inventory,
                                          HolderLookup.Provider registries) {
        TankAccessors accessors = getTankAccessors(tankStack);
        invokeTankStatic(tankStack, accessors == null ? null : accessors.saveInventory(), tankStack, inventory, registries);
    }

    private static FlowingFluidsAPI getApi() {
        FlowingFluidsAPI api = cachedApi;
        if (api == null) {
            synchronized (ModularBackpacksFractionalTankTransfer.class) {
                api = cachedApi;
                if (api == null) {
                    api = FlowingFluidsAPI.getInstance("mspellcompat_modular_backpacks_tanks");
                    cachedApi = api;
                }
            }
        }
        return api;
    }

    private static int bucketUnits(ItemStack stack) {
        return LEVELS_PER_BUCKET - stack.getDamageValue();
    }

    private static boolean canMerge(ItemStack output, ItemStack container) {
        if (container.isEmpty()) {
            return false;
        }
        if (output.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameComponents(output, container)
                && output.getCount() + container.getCount() <= output.getMaxStackSize();
    }

    private static Item findBucketItem(Fluid fluid) {
        return BUCKET_CACHE.computeIfAbsent(fluid, key -> {
            for (Item candidate : BuiltInRegistries.ITEM) {
                if (candidate instanceof FFBucketItem bucket) {
                    Fluid candidateFluid = bucket.ff$getFluid();
                    if (candidateFluid != null && candidateFluid.isSame(key)) {
                        return Optional.of(candidate);
                    }
                }
            }
            return Optional.empty();
        }).orElse(null);
    }

    private static void replaceInputWithResult(Player player, ItemStack tankStack,
                                               ItemStackHandler inventory, ItemStack result) {
        inventory.setStackInSlot(INPUT_SLOT, result.copy());
        saveTankInventory(tankStack, inventory, player.registryAccess());
    }

    private static void replaceInputWithFilledBucketAndMergeRemainder(
            Player player, ItemStack tankStack, ItemStackHandler inventory,
            ItemStack input, ItemStack output, ItemStack filledBucket) {
        int remainderCount = input.getCount() - 1;
        if (remainderCount <= 0) {
            replaceInputWithResult(player, tankStack, inventory, filledBucket);
            return;
        }

        output.grow(remainderCount);
        inventory.setStackInSlot(INPUT_SLOT, filledBucket.copy());
        inventory.setStackInSlot(OUTPUT_SLOT, output);
        saveTankInventory(tankStack, inventory, player.registryAccess());
    }

    private static void moveOneInputToOutput(Player player, ItemStack tankStack,
                                            ItemStackHandler inventory, ItemStack input,
                                            ItemStack output, ItemStack containerAfter) {
        input.shrink(1);
        inventory.setStackInSlot(INPUT_SLOT, input);

        if (output.isEmpty()) {
            inventory.setStackInSlot(OUTPUT_SLOT, containerAfter.copy());
        } else {
            output.grow(containerAfter.getCount());
            inventory.setStackInSlot(OUTPUT_SLOT, output);
        }

        saveTankInventory(tankStack, inventory, player.registryAccess());
    }
}
