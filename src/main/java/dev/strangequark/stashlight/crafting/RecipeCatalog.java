package dev.strangequark.stashlight.crafting;

import dev.strangequark.stashlight.model.IndexedItem;
import dev.strangequark.stashlight.model.StackKey;
import dev.strangequark.stashlight.repository.ContainerRepository;
import dev.strangequark.stashlight.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds the craftable-recipe list from the client recipe book (the only full
 * recipe data the 1.21.10 client has) and computes craftability / ingredient
 * consumption against the player inventory + reachable chests.
 */
public final class RecipeCatalog {

    private static final List<RecipeBookCategory> CRAFTING_CATEGORIES = List.of(
            RecipeBookCategories.CRAFTING_BUILDING_BLOCKS,
            RecipeBookCategories.CRAFTING_EQUIPMENT,
            RecipeBookCategories.CRAFTING_REDSTONE,
            RecipeBookCategories.CRAFTING_MISC
    );

    private final ContainerRepository repository;

    private Station station = Station.INVENTORY;
    private List<RecipeList> lists = List.of();
    private Set<StackKey> craftable = Set.of();

    public RecipeCatalog(ContainerRepository repository) {
        this.repository = repository;
    }

    /** Rebuild the recipe list from the client recipe book, filtered by station. */
    public void refresh(Station station) {
        this.station = station;
        this.lists = List.of();
        this.craftable = Set.of();

        var mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.getRecipeBook() instanceof ClientRecipeBook book)) {
            return;
        }

        Map<StackKey, List<RecipeDisplayEntry>> groups = new LinkedHashMap<>();
        for (RecipeBookCategory cat : CRAFTING_CATEGORIES) {
            for (RecipeCollection collection : book.getCollection(cat)) {
                for (RecipeDisplayEntry entry : collection.getRecipes()) {
                    if (entry.craftingRequirements().isEmpty()) continue;
                    if (station == Station.INVENTORY && !fits2x2(entry)) continue;
                    ItemStack result = resultOf(entry);
                    if (result == null || result.isEmpty()) continue;
                    groups.computeIfAbsent(new StackKey(result), k -> new ArrayList<>()).add(entry);
                }
            }
        }

        List<RecipeList> built = new ArrayList<>();
        for (var group : groups.entrySet()) {
            built.add(new RecipeList(group.getKey().stack().copy(), group.getValue()));
        }
        this.lists = built;
        refreshCraftability();
    }

    /** Recompute which results are craftable from the current inventory + reachable chests. */
    public void refreshCraftability() {
        StackedItemContents contents = buildContents();
        Set<StackKey> craftable = new LinkedHashSet<>();
        for (RecipeList list : lists) {
            for (RecipeDisplayEntry entry : list.variants()) {
                if (entry.canCraft(contents)) {
                    craftable.add(new StackKey(list.result()));
                    break;
                }
            }
        }
        this.craftable = craftable;
    }

    public List<RecipeList> lists() {
        return lists;
    }

    public Set<StackKey> craftable() {
        return craftable;
    }

    public Station station() {
        return station;
    }

    public boolean isCraftable(RecipeList list) {
        return craftable.contains(new StackKey(list.result()));
    }

    // ── contents & counting ─────────────────────────────────────────────

    /** Inventory + reachable chest items, as stacked crafting contents. */
    public StackedItemContents buildContents() {
        StackedItemContents contents = new StackedItemContents();
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.getInventory().fillStackedContents(contents);
        }
        for (IndexedItem item : Util.reachableItems(repository)) {
            contents.accountStack(item.stack());
        }
        return contents;
    }

    /** Maximum number of times {@code entry} can be crafted with current materials. */
    public int maxCraftable(RecipeDisplayEntry entry) {
        if (entry.craftingRequirements().isEmpty()) return 0;
        return buildContents().getBiggestCraftableStack(syntheticRecipe(entry), Integer.MAX_VALUE, null);
    }

    /** Maximum craftable using the player inventory only (what recipe placement can use). */
    public static int maxCraftableFromInventory(RecipeDisplayEntry entry) {
        if (entry.craftingRequirements().isEmpty()) return 0;
        StackedItemContents contents = new StackedItemContents();
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.getInventory().fillStackedContents(contents);
        }
        return contents.getBiggestCraftableStack(syntheticRecipe(entry), Integer.MAX_VALUE, null);
    }

    /** Maximum craftable from inventory + reachable chests (what take-then-craft can bring in). */
    public static int maxCraftableInReach(RecipeDisplayEntry entry) {
        if (entry.craftingRequirements().isEmpty()) return 0;
        StackedItemContents contents = new StackedItemContents();
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.getInventory().fillStackedContents(contents);
        }
        var stashlight = dev.strangequark.stashlight.Stashlight.getInstance();
        ContainerRepository repo = stashlight != null ? stashlight.getRepository() : null;
        for (IndexedItem item : Util.reachableItems(repo)) {
            contents.accountStack(item.stack());
        }
        return contents.getBiggestCraftableStack(syntheticRecipe(entry), Integer.MAX_VALUE, null);
    }

    /**
     * Wrap the entry's ingredient list in a throwaway {@link ShapedRecipe} so the
     * real {@link StackedItemContents} craft-count logic can be reused (1.21.10 only
     * exposes getBiggestCraftableStack via a {@link Recipe}). The grid is padded to a
     * full 3x3 — empty slots contribute nothing to placement and it keeps
     * {@code Util.isSymmetrical} (which indexes the slot list) in bounds.
     */
    private static Recipe<?> syntheticRecipe(RecipeDisplayEntry entry) {
        List<Ingredient> ingredients = entry.craftingRequirements().orElse(List.of());
        ItemStack result = resultOf(entry);
        List<Optional<Ingredient>> slots = new ArrayList<>(ingredients.stream().map(Optional::ofNullable).toList());
        while (slots.size() < 9) slots.add(Optional.empty());
        ShapedRecipePattern pattern = new ShapedRecipePattern(3, 3, slots, Optional.empty());
        return new ShapedRecipe("", CraftingBookCategory.MISC, pattern,
                result == null ? ItemStack.EMPTY : result, true);
    }

    // ── static helpers ──────────────────────────────────────────────────

    public static ItemStack resultOf(RecipeDisplayEntry entry) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        ContextMap ctx = SlotDisplayContext.fromLevel(mc.level);
        List<ItemStack> items = entry.resultItems(ctx);
        return items.isEmpty() ? null : items.get(0);
    }

    public static boolean fits2x2(RecipeDisplayEntry entry) {
        RecipeDisplay display = entry.display();
        if (display instanceof ShapedCraftingRecipeDisplay shaped) {
            return shaped.width() <= 2 && shaped.height() <= 2;
        }
        if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
            return shapeless.ingredients().size() <= 4;
        }
        return false;
    }

    /**
     * A consumed ingredient aggregated for display: the raw recipe {@link Ingredient}
     * (item-level / tag-aware matching), the representative stack and how many units
     * a single craft consumes of it.
     */
    public record IngredientGroup(Ingredient ingredient, ItemStack stack, int perCraft) {
    }

    /** Per-slot ingredient representatives grouped by item, for the consumption table. */
    public static List<IngredientGroup> ingredientGroups(RecipeDisplayEntry entry) {
        List<Ingredient> ingredients = entry.craftingRequirements().orElse(List.of());
        Map<StackKey, Integer> counts = new LinkedHashMap<>();
        Map<StackKey, ItemStack> reps = new LinkedHashMap<>();
        Map<StackKey, Ingredient> ingReps = new LinkedHashMap<>();
        for (Ingredient ing : ingredients) {
            ItemStack rep = pickRepresentative(ing);
            if (rep.isEmpty()) continue;
            StackKey key = new StackKey(rep);
            counts.merge(key, 1, Integer::sum);
            reps.putIfAbsent(key, rep);
            // All ingredients in the same group represent the same material, so any
            // one of them can be used for Ingredient-based (tag-aware) matching.
            ingReps.putIfAbsent(key, ing);
        }
        List<IngredientGroup> groups = new ArrayList<>();
        for (var e : counts.entrySet()) {
            groups.add(new IngredientGroup(ingReps.get(e.getKey()), reps.get(e.getKey()), e.getValue()));
        }
        return groups;
    }

    private static ItemStack pickRepresentative(Ingredient ing) {
        List<ItemStack> candidates = ing.items().map(ItemStack::new).toList();
        for (ItemStack candidate : candidates) {
            if (Util.countInInventory(candidate) + Util.countInReach(candidate) > 0) return candidate;
        }
        return candidates.isEmpty() ? ItemStack.EMPTY : candidates.get(0);
    }
}
