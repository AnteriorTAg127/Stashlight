package dev.strangequark.stashlight.mixin;

import dev.strangequark.stashlight.scan.SilentOpenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into {@link Minecraft} to intercept screen openings for
 * silent (screen-less) container operations.
 * <p>
 * When {@link SilentOpenManager#isSilent()} is {@code true}, calls to
 * {@link Minecraft#setScreen(Screen)} are suppressed. This prevents the
 * container GUI from appearing while allowing the underlying menu to be
 * created and populated by the normal packet handling chain.
 */
@Mixin(Minecraft.class)
public abstract class ClientPacketListenerMixin {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void stashlight$silentSetScreen(Screen screen, CallbackInfo ci) {
        if (SilentOpenManager.isSilent()) {
            ci.cancel();
        }
    }
}
