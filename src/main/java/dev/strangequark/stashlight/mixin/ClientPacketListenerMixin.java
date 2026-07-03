package dev.strangequark.stashlight.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into {@link ClientPacketListener} to support silent (screen-less)
 * container openings for the vanilla-fallback scanner and taker.
 * <p>
 * Two injections:
 * <ol>
 *   <li>{@link #stashlight$redirectSetScreen} — intercepts {@code Minecraft.setScreen}
 *       called from within {@code handleOpenScreen}. When a silent open is pending,
 *       the screen creation is skipped while the container menu creation proceeds
 *       normally, so {@code player.containerMenu} is updated.</li>
 *   <li>{@link #stashlight$captureMenu} — after the method completes, captures the
 *       created menu into the {@link SilentOpenManager} state machine for content
 *       extraction.</li>
 * </ol>
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    /**
     * Redirect {@code Minecraft.setScreen(Screen)} called from
     * {@code handleOpenScreen}. When a silent open is pending, the call is
     * suppressed (no UI shown), but the container menu has already been
     * created and assigned to {@code player.containerMenu} by the original
     * method body.
     */
    @Redirect(
            method = "handleOpenScreen",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"
            )
    )
    private void stashlight$redirectSetScreen(Minecraft mc, Screen screen) {
        if (!SilentOpenManager.isSilent()) {
            mc.setScreen(screen);
        }
        // Silent: skip setScreen — menu already created by the original method.
    }

    /**
     * After {@code handleOpenScreen} completes (with or without setScreen),
     * record the newly created container menu in {@link SilentOpenManager}
     * if a silent open is in progress.
     */
    @Inject(method = "handleOpenScreen", at = @At("TAIL"))
    private void stashlight$captureMenu(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        if (!SilentOpenManager.isSilent()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            SilentOpenManager.capture(mc.player.containerMenu);
        }
    }
}
