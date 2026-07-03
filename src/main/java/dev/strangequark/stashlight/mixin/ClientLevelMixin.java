package dev.strangequark.stashlight.mixin;

import dev.strangequark.stashlight.Stashlight;
import dev.strangequark.stashlight.repository.ContainerRepository;
import dev.strangequark.stashlight.util.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks {@code Level.setBlock} to detect when any block change
 * (player break, explosion, piston, etc.) turns a cached searchable
 * container into a non-container. Removes the stale entry from both
 * LOCAL_MAP and SERVER_MAP so the search UI never shows destroyed chests.
 */
@Mixin(Level.class)
public abstract class ClientLevelMixin {

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("RETURN"))
    private void stashlight$onSetBlock(BlockPos pos, BlockState state, int flags, int recursionLeft,
                                        CallbackInfoReturnable<Boolean> cir) {
        var stashlight = Stashlight.getInstance();
        if (stashlight == null) return;

        ContainerRepository repo = stashlight.getRepository();
        if (repo == null) return;

        String dimension = Util.getDimensionName((Level) (Object) this);
        repo.removeIfBroken(dimension, pos, state);
    }
}
