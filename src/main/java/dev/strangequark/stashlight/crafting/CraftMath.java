package dev.strangequark.stashlight.crafting;

import java.util.List;

/**
 * Pure arithmetic shared by the craft UI, coordinator and executor.
 * <p>
 * Deliberately free of Minecraft imports so the consumption / batching math
 * can be unit-tested without a game environment. All inputs are clamped to
 * sane non-negative values before use.
 */
public final class CraftMath {

    private CraftMath() {
    }

    /** Number of crafts needed to produce {@code qty} products, each craft yielding {@code resultPerCraft}. */
    public static int batchesFor(int qty, int resultPerCraft) {
        int q = Math.max(0, qty);
        if (q == 0) return 0;
        int per = Math.max(1, resultPerCraft);
        return (int) Math.ceil((double) q / per);
    }

    /** Total products produced by {@code batches} crafts (may overshoot the requested quantity). */
    public static int craftTarget(int batches, int resultPerCraft) {
        return Math.max(0, batches) * Math.max(1, resultPerCraft);
    }

    /** Units of one ingredient consumed by {@code batches} crafts. */
    public static int needFor(int perCraft, int batches) {
        return Math.max(0, perCraft) * Math.max(0, batches);
    }

    /** How much must still be taken: need minus what the inventory already holds. */
    public static int deficit(int need, int have) {
        return Math.max(0, need - Math.max(0, have));
    }

    /**
     * Maximum number of crafts possible given, for each ingredient, how many
     * units one craft consumes and how many units are available (inventory +
     * reachable chests). The limiting ingredient decides; 0 if any ingredient
     * is missing, its per-craft need is 0, or the lists are empty/mismatched.
     */
    public static int maxCraftable(List<Integer> perCraft, List<Integer> available) {
        if (perCraft == null || available == null || perCraft.isEmpty()) return 0;
        int max = Integer.MAX_VALUE;
        for (int i = 0; i < perCraft.size(); i++) {
            if (i >= available.size()) return 0;
            int need = Math.max(0, perCraft.get(i));
            int have = Math.max(0, available.get(i));
            if (need == 0 || have <= 0) return 0;
            max = Math.min(max, have / need);
        }
        return max;
    }
}
