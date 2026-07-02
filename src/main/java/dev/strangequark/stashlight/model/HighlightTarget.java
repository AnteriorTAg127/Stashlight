package dev.strangequark.stashlight.model;

import net.minecraft.core.BlockPos;

/**
 * A pending or active highlight target. Contains enough information to render
 * every layer: world block outline, beacon beam, GUI slot overlay and nested
 * box hint.
 */
public record HighlightTarget(
        BlockPos pos,
        LocatePath path,
        Source source,
        long startTimeMillis,
        int color
) {
    /**
     * Top-level slot inside the outer container, or -1 if unknown.
     */
    public int topSlot() {
        return path.topSlot();
    }

    /**
     * True if the item is inside a nested container (shulker box, bundle, etc.).
     */
    public boolean isNested() {
        return path.slots().size() > 1;
    }
}
