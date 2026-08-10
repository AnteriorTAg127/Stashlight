package dev.strangequark.stashlight.crafting;

import dev.strangequark.stashlight.Stashlight;
import dev.strangequark.stashlight.config.Config;
import dev.strangequark.stashlight.model.DataSourceMode;
import dev.strangequark.stashlight.model.DisplayItem;
import dev.strangequark.stashlight.model.IndexedItem;
import dev.strangequark.stashlight.model.StackKey;
import dev.strangequark.stashlight.take.TakeQueueEntry;
import dev.strangequark.stashlight.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates the "take then craft" flow: computes the missing ingredient
 * quantities, feeds them through the existing take queue, and once the take
 * finishes hands off to {@link CraftExecutor}.
 */
public final class CraftCoordinator {

    private static final Logger LOGGER = LoggerFactory.getLogger("Stashlight/CraftCoordinator");

    private CraftCoordinator() {
    }

    public static void start(RecipeDisplayEntry recipe, int requested) {
        var mc = Minecraft.getInstance();
        var stashlight = Stashlight.getInstance();
        if (stashlight == null || mc.player == null) return;

        ItemStack result = RecipeCatalog.resultOf(recipe);
        if (result == null || result.isEmpty()) return;
        int resultPerCraft = Math.max(1, result.getCount());
        int batches = CraftMath.batchesFor(requested, resultPerCraft);

        if (!Config.get().crafting().takeBeforeCraft()) {
            CraftExecutor.start(recipe, requested);
            return;
        }

        // Compute ingredient deficits and gather take sources.
        List<TakeQueueEntry> toTake = new ArrayList<>();
        boolean sourceScanRequested = false;
        for (RecipeCatalog.IngredientGroup group : RecipeCatalog.ingredientGroups(recipe)) {
            int need = CraftMath.needFor(group.perCraft(), batches);
            int have = Util.countInInventory(group.stack());
            int deficit = CraftMath.deficit(need, have);
            boolean sourceFound = false;
            if (deficit > 0) {
                Optional<DisplayItem> source = resolveMaterialSource(group.ingredient(), group.stack());
                sourceFound = source.isPresent();
                if (source.isPresent()) {
                    ItemStack target = source.get().stack().copy();
                    target.setCount(deficit);
                    toTake.add(new TakeQueueEntry(new StackKey(target), target, deficit));
                } else {
                    // Diagnose the failure and refresh the repository so the next
                    // attempt has a better chance of resolving the source.
                    logMissingSource(group.ingredient(), group.stack());
                    if (!sourceScanRequested) {
                        stashlight.requestServerScan();
                        sourceScanRequested = true;
                    }
                }
            }
            LOGGER.info("CraftCoordinator ingredient '{}': need={} have={} deficit={} sourceFound={}",
                    group.stack().getHoverName().getString(), need, have, deficit, sourceFound);
        }

        if (toTake.isEmpty()) {
            LOGGER.info("CraftCoordinator: no items to take, handing straight to craft");
            CraftExecutor.start(recipe, requested);
            return;
        }

        boolean modded = stashlight.isModdedTakeAvailable();
        LOGGER.info("CraftCoordinator: queueing {} take entries via {} take",
                toTake.size(), modded ? "modded" : "vanilla");
        if (modded) {
            var takeClient = stashlight.getTakeClient();
            takeClient.setQueueDoneCallback(() -> CraftExecutor.start(recipe, requested));
            if (!Config.get().crafting().keepScreenOnCraft()) {
                mc.setScreen(null);
            }
            takeClient.startQueue(toTake);
        } else {
            var vanillaTaker = stashlight.getVanillaTaker();
            // Craft-triggered takes: by default overflow stays in the chest
            // (never drop materials). With the "recover dropped materials"
            // advanced option, overflow may be dropped and CraftExecutor will
            // wait for the items to be picked back up before crafting.
            boolean recover = Config.get().crafting().recoverDroppedMaterials();
            vanillaTaker.setSuppressDropOnFull(!recover);
            vanillaTaker.setQueueDoneCallback(() -> {
                if (recover && vanillaTaker.droppedDuringLastQueue()) {
                    CraftExecutor.startWithRecovery(recipe, requested);
                } else {
                    CraftExecutor.start(recipe, requested);
                }
            });
            vanillaTaker.startQueue(toTake);
        }
    }

    /**
     * Resolve a reachable {@link DisplayItem} matching the recipe {@link Ingredient}.
     * Uses item-level, tag-aware {@link Ingredient#test} instead of exact
     * stack+components matching so tag-based ingredients (e.g. any planks) and
     * component differences no longer fall through. {@code want} is only used for
     * display/logging.
     */
    private static Optional<DisplayItem> resolveMaterialSource(Ingredient ing, ItemStack want) {
        var stashlight = Stashlight.getInstance();
        if (stashlight == null) return Optional.empty();
        var repository = stashlight.getRepository();
        if (repository == null) return Optional.empty();

        List<IndexedItem> matches = new ArrayList<>();
        for (IndexedItem item : repository.getSearchIndex(DataSourceMode.MERGED)) {
            if (item.pos() != null && Util.isWithinReach(item.pos())
                    && ing.test(item.stack())) {
                matches.add(item);
            }
        }
        if (matches.isEmpty()) {
            LOGGER.debug("No reachable source matching '{}'", want.getHoverName().getString());
            return Optional.empty();
        }
        ItemStack merged = matches.get(0).stack().copy();
        merged.setCount(matches.stream().mapToInt(i -> i.stack().getCount()).sum());
        return Optional.of(new DisplayItem(merged, matches));
    }

    /**
     * When no reachable source is found, tell apart "indexed but out of reach"
     * from "not indexed at all" by scanning the repository without the reach
     * filter, and log a concrete hint so the player knows what to do.
     */
    private static void logMissingSource(Ingredient ing, ItemStack want) {
        var stashlight = Stashlight.getInstance();
        if (stashlight == null) return;
        var repository = stashlight.getRepository();
        if (repository == null) return;
        var player = Minecraft.getInstance().player;
        if (player == null) return;

        double nearestDist = Double.MAX_VALUE;
        for (IndexedItem item : repository.getSearchIndex(DataSourceMode.MERGED)) {
            if (item.pos() == null || !ing.test(item.stack())) continue;
            double d = player.position().distanceTo(Vec3.atCenterOf(item.pos()));
            if (d < nearestDist) nearestDist = d;
        }

        String name = want.getHoverName().getString();
        if (nearestDist == Double.MAX_VALUE) {
            LOGGER.info("Material '{}' is not in the index (open the search or scan the chests first)", name);
        } else {
            double rounded = Math.round(nearestDist * 10.0) / 10.0;
            LOGGER.info("Material '{}' is in the repository but out of reach (nearest {} blocks); get closer and retry", name, rounded);
        }
    }
}
