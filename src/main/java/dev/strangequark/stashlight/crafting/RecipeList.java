package dev.strangequark.stashlight.crafting;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;

import java.util.List;

/**
 * A craftable output grouped by result item. Multiple recipe variants can
 * produce the same result (e.g. different shapes); clicking the result cycles
 * through them.
 */
public record RecipeList(ItemStack result, List<RecipeDisplayEntry> variants) {
    public RecipeList {
        variants = List.copyOf(variants);
    }

    public RecipeDisplayEntry selected() {
        return variants.get(0);
    }
}
