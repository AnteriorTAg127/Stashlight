package dev.strangequark.stashlight.logic.filter;

import dev.strangequark.stashlight.Stashlight;
import dev.strangequark.stashlight.model.IndexedItem;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * Filter that keeps only items the player can take right now: the container
 * must be within the current mode's take range (modded: serverMaxRadius in
 * blocks; vanilla-fallback: 4.5 blocks reach). Items already come from the
 * repository so the "scanned" half of the predicate is implicitly satisfied.
 */
public final class ReachableTakeFilter implements FilterStrategy {

    private static final double VANILLA_REACH = 4.5;

    private boolean enabled = false;

    public void setEnabled(boolean v) { this.enabled = v; }
    public boolean isEnabled() { return enabled; }

    @Override
    public Component getLabel() {
        return Component.translatable("gui.stashlight.label.takeableReachable");
    }

    @Override
    public boolean matches(IndexedItem item) {
        if (!enabled) return true;
        var player = Minecraft.getInstance().player;
        if (player == null || item.pos() == null) return false;

        double maxReach;
        var stashlight = Stashlight.getInstance();
        if (stashlight != null && stashlight.isModdedTakeAvailable()) {
            maxReach = stashlight.getServerMaxRadius();
            if (maxReach <= 0) return true; // handshake incomplete — don't filter
        } else {
            maxReach = VANILLA_REACH;
        }

        double distSq = player.position().distanceToSqr(Vec3.atCenterOf(item.pos()));
        return distSq <= maxReach * maxReach;
    }
}
