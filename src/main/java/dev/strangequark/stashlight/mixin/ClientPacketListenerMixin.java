package dev.strangequark.stashlight.mixin;

import dev.strangequark.stashlight.scan.SilentOpenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class ClientPacketListenerMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("Stashlight/Mixin");

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void stashlight$silentSetScreen(Screen screen, CallbackInfo ci) {
        boolean silent = SilentOpenManager.isSilent();
        LOGGER.debug("setScreen({}) silent={}", screen != null ? screen.getClass().getSimpleName() : "null", silent);
        if (silent) {
            ci.cancel();
        }
    }
}
