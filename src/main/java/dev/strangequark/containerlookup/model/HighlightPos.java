package dev.strangequark.containerlookup.model;

import net.minecraft.util.math.BlockPos;

/**
 * Immutable data describing a block position being highlighted.
 */
public record HighlightPos(
        BlockPos pos,
        long startTimeMillis
) {
}

