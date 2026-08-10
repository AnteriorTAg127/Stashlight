package dev.strangequark.stashlight.crafting;

import dev.strangequark.stashlight.Stashlight;
import dev.strangequark.stashlight.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/**
 * The crafting station currently available to the player:
 * a nearby crafting table (3x3 grid) or the always-present 2x2 inventory grid.
 */
public enum Station {
    CRAFTING_TABLE,
    INVENTORY;

    public static Station detect() {
        return findReachableCraftingTable() != null ? CRAFTING_TABLE : INVENTORY;
    }

    /** Nearest reachable {@code crafting_table} block, or null. */
    public static BlockPos findReachableCraftingTable() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return null;
        BlockPos center = mc.player.blockPosition();
        double reach = Util.reachRadius();
        double reachSq = reach * reach;
        int r = (int) Math.ceil(reach);
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (pos.distSqr(center) > reachSq) continue;
                    if (!mc.level.isLoaded(pos)) continue;
                    if (mc.level.getBlockState(pos).getBlock() == Blocks.CRAFTING_TABLE) {
                        double d = pos.distSqr(center);
                        if (d < bestDist) {
                            bestDist = d;
                            best = pos;
                        }
                    }
                }
            }
        }
        return best;
    }
}
