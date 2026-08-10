package dev.strangequark.stashlight.crafting;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure-math unit tests for {@link CraftMath} — no Minecraft environment needed.
 * These lock in the v1.5 consumption semantics: quantity is interpreted as the
 * number of products wanted, batches round up to whole crafts, and the max
 * craftable count is capped by the limiting ingredient.
 */
class CraftMathTest {

    // ── batchesFor ──────────────────────────────────────────────────────

    @Test
    void batchesForSingleProductRecipes() {
        assertEquals(1, CraftMath.batchesFor(1, 1));
        assertEquals(7, CraftMath.batchesFor(7, 1));
    }

    @Test
    void batchesForMultiProductRecipes() {
        // 1 iron block -> 9 ingots: 10 ingots needs 2 crafts, 9 needs exactly 1.
        assertEquals(1, CraftMath.batchesFor(9, 9));
        assertEquals(2, CraftMath.batchesFor(10, 9));
        assertEquals(2, CraftMath.batchesFor(18, 9));
        assertEquals(3, CraftMath.batchesFor(19, 9));
    }

    @Test
    void batchesForLargeOutputs() {
        assertEquals(1, CraftMath.batchesFor(1, 64));
        assertEquals(2, CraftMath.batchesFor(65, 64));
    }

    @Test
    void batchesForClampsDefensiveInputs() {
        assertEquals(0, CraftMath.batchesFor(0, 1));
        assertEquals(0, CraftMath.batchesFor(-5, 1));
        assertEquals(1, CraftMath.batchesFor(1, 0));
    }

    // ── craftTarget ─────────────────────────────────────────────────────

    @Test
    void craftTargetIsBatchesTimesPerCraft() {
        assertEquals(27, CraftMath.craftTarget(3, 9));
        assertEquals(9, CraftMath.craftTarget(1, 9));
        assertEquals(0, CraftMath.craftTarget(0, 9));
        // resultPerCraft is clamped to >= 1 defensively (never 0 in practice).
        assertEquals(2, CraftMath.craftTarget(2, 0));
    }

    // ── needFor ─────────────────────────────────────────────────────────

    @Test
    void needForScalesWithBatches() {
        assertEquals(12, CraftMath.needFor(4, 3));
        assertEquals(0, CraftMath.needFor(4, 0));
        assertEquals(0, CraftMath.needFor(0, 3));
    }

    // ── deficit ─────────────────────────────────────────────────────────

    @Test
    void deficitIsNeedMinusHaveFlooredAtZero() {
        assertEquals(4, CraftMath.deficit(10, 6));
        assertEquals(0, CraftMath.deficit(6, 10));
        assertEquals(10, CraftMath.deficit(10, 0));
        // Negative "have" is clamped to 0 (defensive; inventory counts never go below 0).
        assertEquals(10, CraftMath.deficit(10, -3));
    }

    // ── maxCraftable ────────────────────────────────────────────────────

    @Test
    void maxCraftableCappedByLimitingIngredient() {
        // 10 planks (1/craft) and 16 sticks (4/craft) -> 4 crafts.
        assertEquals(4, CraftMath.maxCraftable(List.of(1, 4), List.of(10, 16)));
        // Exactly enough sticks: 4 crafts exactly.
        assertEquals(4, CraftMath.maxCraftable(List.of(1, 4), List.of(10, 16)));
    }

    @Test
    void maxCraftableIsZeroWhenIngredientMissing() {
        assertEquals(0, CraftMath.maxCraftable(List.of(1, 4), List.of(10, 0)));
        assertEquals(0, CraftMath.maxCraftable(List.of(1, 4), List.of(0, 16)));
    }

    @Test
    void maxCraftableHandlesDegenerateInput() {
        assertEquals(0, CraftMath.maxCraftable(List.of(), List.of()));
        assertEquals(0, CraftMath.maxCraftable(null, List.of(1)));
        assertEquals(0, CraftMath.maxCraftable(List.of(1, 2), List.of(5)));
        assertEquals(0, CraftMath.maxCraftable(List.of(0), List.of(5)));
    }
}
