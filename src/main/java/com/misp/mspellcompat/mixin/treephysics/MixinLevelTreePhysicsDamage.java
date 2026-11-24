package com.misp.mspellcompat.mixin.treephysics;

import com.misp.mspellcompat.sable.TreePhysicsDamageBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class MixinLevelTreePhysicsDamage {
    private static final TagKey<Block> TREE_PHYSICS_LOGS = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("treephysics", "logs")
    );

    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD")
    )
    private void mspellcompat$treePhysicsOnLogReplacement(
            BlockPos pos,
            BlockState nextState,
            int flags,
            int recursionLeft,
            CallbackInfoReturnable<Boolean> cir
    ) {
        Level level = (Level) (Object) this;
        if (!(level instanceof ServerLevel)) {
            return;
        }
        if (!nextState.isAir() && !nextState.is(Blocks.BARRIER)) {
            return;
        }
        if (!TreePhysicsDamageBridge.shouldInspectBlockReplacement(level, pos, nextState)) {
            return;
        }

        BlockState previousState = level.getBlockState(pos);
        if (previousState.is(TREE_PHYSICS_LOGS)) {
            TreePhysicsDamageBridge.beforeBlockReplacement(level, pos, previousState, nextState);
        }
    }
}
