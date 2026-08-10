package dev.strangequark.stashlight.crafting;

import dev.strangequark.stashlight.Stashlight;
import dev.strangequark.stashlight.config.Config;
import dev.strangequark.stashlight.scan.SilentOpenManager;
import dev.strangequark.stashlight.screen.SearchPage;
import dev.strangequark.stashlight.screen.SearchScreen;
import dev.strangequark.stashlight.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Non-blocking crafting state machine driven by the client tick.
 * <p>
 * Two paths by {@link Station}:
 * <ul>
 *   <li>CRAFTING_TABLE (3x3): silently open the nearby crafting table, place the
 *       recipe via {@code handlePlaceRecipe} and shift-click the result slot.</li>
 *   <li>INVENTORY (2x2): craft directly in the always-present {@link InventoryMenu}
 *       (containerId 0), no menu needs to be opened.</li>
 * </ul>
 * Optimized: when the inventory holds exactly enough material for the target,
 * {@code useMaxItems} placement fills the grid so a single shift-click crafts the
 * whole target; otherwise each cycle crafts one batch, stopping exactly at target.
 */
public final class CraftExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger("Stashlight/CraftExecutor");

    private enum State { IDLE, RECOVER, OPEN, WAIT_MENU, PLACE, WAIT_SYNC, CRAFT, DONE }

    private static CraftExecutor INSTANCE;

    private State state = State.IDLE;
    private Station station;
    private BlockPos tablePos;
    private RecipeDisplayId displayId;
    private ItemStack resultStack;
    private int resultPerCraft;
    /** Result count already present in the inventory when this craft run began. */
    private int initialResultCount;
    private int craftTarget;
    private int craftedSoFar;
    private int containerId;
    private int craftBatches;
    private int gridFullBatches;
    private int tickCounter;
    private int craftIntervalTicks;
    private int restorePendingTicks = 0;
    private int lastCrafted = 0;
    private int noProgressStreak = 0;

    /** Recovery-wait state (advanced option "recover dropped materials"). */
    private boolean recoveryMode = false;
    private int recoverTicks = 0;
    private List<Ingredient> recoverIngredients = List.of();

    /** True when the requested quantity was capped by backpack capacity. */
    private boolean backpackLimited = false;

    public static CraftExecutor get() {
        if (INSTANCE == null) INSTANCE = new CraftExecutor();
        return INSTANCE;
    }

    /** Start crafting {@code requested} copies of {@code recipe}. */
    public static void start(RecipeDisplayEntry recipe, int requested) {
        get().begin(recipe, requested, false);
    }

    /** Start crafting, first waiting for dropped materials to be picked back up. */
    public static void startWithRecovery(RecipeDisplayEntry recipe, int requested) {
        get().begin(recipe, requested, true);
    }

    private void begin(RecipeDisplayEntry recipe, int requested, boolean recovery) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        station = Station.detect();
        displayId = recipe.id();
        resultStack = RecipeCatalog.resultOf(recipe);
        if (resultStack == null || resultStack.isEmpty()) {
            message("gui.stashlight.message.craftNoResult");
            return;
        }
        initialResultCount = Util.countInInventory(resultStack);
        resultPerCraft = Math.max(1, resultStack.getCount());

        int maxCraft = RecipeCatalog.maxCraftableFromInventory(recipe);
        int requestedBatches = CraftMath.batchesFor(requested, resultPerCraft);
        int batches = Math.min(requestedBatches, maxCraft);
        if (batches <= 0) {
            message("gui.stashlight.message.craftNoMaterials");
            return;
        }

        // Cap the craft target by the backpack capacity: crafted products must
        // fit into the inventory (free slots + merge room into existing stacks).
        // Without this, a big target (e.g. 1728 ingots) fills the backpack mid-
        // craft, the result slot jams and the loop silently stops part-way.
        int freeSlots = freeInventorySlots();
        int mergeRoom = mergeRoomFor(resultStack);
        int maxProductsByBackpack = freeSlots * Math.max(1, resultStack.getMaxStackSize()) + mergeRoom;
        int maxBatchesByBackpack = Math.max(0, maxProductsByBackpack / resultPerCraft);
        if (maxBatchesByBackpack < 1) {
            message("gui.stashlight.message.craftBackpackFull");
            return;
        }
        // The backpack cannot hold the whole target and it already carries some
        // of the result item: drop those existing stacks onto the ground to free
        // capacity. The items have a pickup delay, so they are auto-picked again
        // once the craft consumes materials and frees slots — nothing is lost.
        if (batches > maxBatchesByBackpack) {
            int droppedStacks = dropInventoryStacksOf(resultStack);
            if (droppedStacks > 0) {
                freeSlots = freeInventorySlots();
                mergeRoom = mergeRoomFor(resultStack);
                maxProductsByBackpack = freeSlots * Math.max(1, resultStack.getMaxStackSize()) + mergeRoom;
                maxBatchesByBackpack = Math.max(0, maxProductsByBackpack / resultPerCraft);
                message("gui.stashlight.message.craftBackpackCleared", droppedStacks);
                LOGGER.info("CraftExecutor: dropped {} stack(s) of previous products to free backpack space (new capacity {} batches)",
                        droppedStacks, maxBatchesByBackpack);
            }
        }
        batches = Math.min(batches, maxBatchesByBackpack);
        // Only flag the message when the backpack capacity itself cut the target
        // (a material shortage cuts it too, and must not say "backpack full").
        this.backpackLimited = batches < Math.min(requestedBatches, maxCraft);

        this.craftTarget = CraftMath.craftTarget(batches, resultPerCraft);
        this.craftedSoFar = 0;
        // Batch placement strategy: fill the whole grid (useMaxItems) while the
        // remaining target is at least one full grid, then finish one batch at a
        // time. This makes large crafts run in a handful of shift-clicks instead
        // of hundreds of single-batch rounds (e.g. 700 boards in ~3 rounds + tail).
        int gridFullBatches = Integer.MAX_VALUE;
        for (RecipeCatalog.IngredientGroup group : RecipeCatalog.ingredientGroups(recipe)) {
            gridFullBatches = Math.min(gridFullBatches,
                    Math.max(1, group.stack().getMaxStackSize()) / Math.max(1, group.perCraft()));
        }
        if (gridFullBatches == Integer.MAX_VALUE) gridFullBatches = 1;
        this.gridFullBatches = gridFullBatches;
        this.craftBatches = batches;
        this.craftIntervalTicks = Config.get().crafting().craftIntervalTicks();
        this.tickCounter = 0;
        this.restorePendingTicks = 0;
        this.lastCrafted = 0;
        this.noProgressStreak = 0;
        this.recoveryMode = recovery;
        this.recoverTicks = 0;
        this.recoverIngredients = recipe.craftingRequirements().orElse(List.of());
        String invCounts = RecipeCatalog.ingredientGroups(recipe).stream()
                .map(g -> g.stack().getHoverName().getString() + "=" + Util.countInInventory(g.stack()))
                .collect(Collectors.joining(", "));
        LOGGER.info("Craft begin: station={} maxCraft={} batches={} craftTarget={} resultPerCraft={} initialResultCount={} recovery={} backpackLimited={} gridFullBatches={} inv[{}]",
                station, maxCraft, batches, craftTarget, resultPerCraft, initialResultCount, recovery, backpackLimited, gridFullBatches, invCounts);

        if (station == Station.CRAFTING_TABLE) {
            tablePos = Station.findReachableCraftingTable();
            if (tablePos == null) {
                message("gui.stashlight.message.craftNoTable");
                return;
            }
        }
        if (recoveryMode) {
            // Wait for dropped materials to be picked back up before crafting.
            state = State.RECOVER;
        } else if (station == Station.CRAFTING_TABLE) {
            state = State.OPEN;
        } else {
            this.containerId = InventoryMenu.CONTAINER_ID;
            state = State.PLACE;
        }
    }

    /** Called every client tick. */
    public void tick(Minecraft client) {
        if (state == State.IDLE) {
            if (restorePendingTicks > 0) {
                restorePendingTicks--;
                if (restorePendingTicks == 0) restoreSearchScreen();
            }
            return;
        }

        tickCounter++;
        switch (state) {
            case RECOVER -> {
                // Wait for dropped materials (from the take phase) to be picked
                // back up. The player walks over the items; once all matching
                // drops are gone we proceed with the normal craft flow.
                var level = client.level;
                var player = client.player;
                if (level == null || player == null || recoverIngredients.isEmpty()) {
                    state = State.DONE;
                    break;
                }
                if (player.hurtTime > 0) {
                    finish("gui.stashlight.message.craftInterrupted");
                    return;
                }
                List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class,
                        player.getBoundingBox().inflate(8.0),
                        e -> e.isAlive() && e.getItem() != null
                                && recoverIngredients.stream().anyMatch(ing -> ing.test(e.getItem())));
                if (drops.isEmpty()) {
                    // All dropped materials were picked up (or vanished).
                    LOGGER.info("CraftExecutor: dropped materials recovered, proceeding to craft");
                    state = station == Station.CRAFTING_TABLE ? State.OPEN : State.PLACE;
                    tickCounter = 0;
                    break;
                }
                boolean anyAutoPickup = drops.stream()
                        .anyMatch(e -> e.distanceToSqr(player) <= 4.0 * 4.0);
                recoverTicks++;
                if (anyAutoPickup) {
                    // Waiting for the normal auto-pickup to collect them.
                    if (recoverTicks % 40 == 0) message("gui.stashlight.craft.recoverWait");
                } else if (Config.get().crafting().waitManualPickup()) {
                    // Too far / cannot be picked automatically: ask the player
                    // to walk over, then the craft resumes automatically.
                    if (recoverTicks % 40 == 0) message("gui.stashlight.craft.recoverManual");
                } else {
                    // Fallback: craft with whatever was recovered.
                    message("gui.stashlight.craft.recoverPartial");
                    LOGGER.info("CraftExecutor: {} dropped item entities not recovered, crafting with available materials",
                            drops.size());
                    state = station == Station.CRAFTING_TABLE ? State.OPEN : State.PLACE;
                    tickCounter = 0;
                }
            }
            case OPEN -> { // 3x3: silently open the crafting table
                SilentOpenManager.begin(tablePos, null);
                client.gameMode.useItemOn(
                        client.player,
                        InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(tablePos), Direction.UP, tablePos, false)
                );
                state = State.WAIT_MENU;
                tickCounter = 0;
            }
            case WAIT_MENU -> {
                if (tickCounter < 2) break;
                if (SilentOpenManager.isContentReady()) {
                    SilentOpenManager.finish();
                    this.containerId = client.player.containerMenu.containerId;
                    state = State.PLACE;
                    tickCounter = 0;
                } else if (tickCounter > 40) {
                    LOGGER.warn("crafting table menu did not open in time");
                    finish("gui.stashlight.message.craftTimeout");
                }
            }
            case PLACE -> {
                if (client.player.hurtTime > 0) {
                    finish("gui.stashlight.message.craftInterrupted");
                    return;
                }
                if (craftedSoFar >= craftTarget) {
                    state = State.DONE;
                    break;
                }
                // Fill the grid while the remaining target is >= one full grid;
                // below that, place a single batch at a time to land exactly on target.
                int remainingBatches = Math.max(0, craftBatches - craftedSoFar / resultPerCraft);
                boolean useMax = gridFullBatches > 0 && remainingBatches >= gridFullBatches;
                client.gameMode.handlePlaceRecipe(containerId, displayId, useMax);
                LOGGER.info("CraftExecutor PLACE: craftedSoFar={} remainingBatches={} useMax={} gridFullBatches={} menu={} containerId={}",
                        craftedSoFar, remainingBatches, useMax, gridFullBatches,
                        client.player.containerMenu.getClass().getSimpleName(), containerId);
                state = State.WAIT_SYNC;
                tickCounter = 0;
            }
            case WAIT_SYNC -> {
                if (client.player.hurtTime > 0) {
                    finish("gui.stashlight.message.craftInterrupted");
                    return;
                }
                if (tickCounter < craftIntervalTicks) break;
                var menu = client.player.containerMenu;
                var resultSlot = menu.slots.get(0).getItem();
                if (resultSlot.isEmpty()) {
                    LOGGER.warn("CraftExecutor WAIT_SYNC: result slot empty after {} ticks, menu={} craftedSoFar={} target={} ticks={}",
                            tickCounter, menu.getClass().getSimpleName(), craftedSoFar, craftTarget, tickCounter);
                    state = State.DONE; // materials exhausted (or placement failed)
                    break;
                }
                client.gameMode.handleInventoryMouseClick(containerId, 0, 0, ClickType.QUICK_MOVE, client.player);
                state = State.CRAFT;
                tickCounter = 0;
            }
            case CRAFT -> {
                if (client.player.hurtTime > 0) {
                    finish("gui.stashlight.message.craftInterrupted");
                    return;
                }
                if (tickCounter < craftIntervalTicks) break;
                // Only count the delta over what was already in the inventory at
                // begin(), so craftedSoFar reflects this run's actual output.
                int crafted = Util.countInInventory(resultStack) - initialResultCount;
                if (crafted > craftedSoFar) {
                    craftedSoFar = crafted;
                    showProgress();
                }
                if (crafted >= craftTarget) {
                    state = State.DONE;
                    break;
                }
                // NOTE: the result slot is normally EMPTY here — WAIT_SYNC just
                // shift-clicked the result away. Material exhaustion is detected
                // in WAIT_SYNC (placement produced nothing); the only stall left
                // to guard against here is the crafted count not moving.
                if (crafted == lastCrafted) {
                    noProgressStreak++;
                    if (noProgressStreak >= 2) {
                        finish("gui.stashlight.message.craftDone");
                        break;
                    }
                } else {
                    noProgressStreak = 0;
                }
                lastCrafted = crafted;
                state = State.PLACE;
            }
            case DONE -> finish("gui.stashlight.message.craftDone");
        }
    }

    private void finish(String doneKey) {
        var mc = Minecraft.getInstance();
        if (station == Station.CRAFTING_TABLE && mc.player != null) {
            try {
                mc.player.closeContainer();
            } catch (Exception ignored) {
            }
        }
        if (backpackLimited) {
            message("gui.stashlight.message.craftBackpackLimited", craftedSoFar, craftTarget);
        } else {
            message(doneKey, craftedSoFar, craftTarget);
        }
        LOGGER.info("CraftExecutor done: {} of {}", craftedSoFar, craftTarget);
        state = State.IDLE;
        if (Config.get().crafting().keepScreenOnCraft()) {
            restorePendingTicks = 5;
        }
    }

    private static int freeInventorySlots() {
        var player = Minecraft.getInstance().player;
        if (player == null) return 0;
        int free = 0;
        var inv = player.getInventory();
        for (int i = 0; i < 36; i++) {
            if (inv.getItem(i).isEmpty()) free++;
        }
        return free;
    }

    /** Total free capacity in existing stacks of {@code stack} in the inventory. */
    private static int mergeRoomFor(ItemStack stack) {
        var player = Minecraft.getInstance().player;
        if (player == null) return 0;
        int room = 0;
        var inv = player.getInventory();
        for (int i = 0; i < 36; i++) {
            var s = inv.getItem(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, stack)) {
                room += Math.max(0, s.getMaxStackSize() - s.getCount());
            }
        }
        return room;
    }

    /**
     * Throw every inventory stack of {@code stack} onto the ground (THROW the
     * whole stack per slot) so the freed slots can hold freshly crafted items.
     * Returns how many stacks were dropped. The items keep their pickup delay,
     * so they are picked back up once the craft frees up slots.
     */
    private static int dropInventoryStacksOf(ItemStack stack) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || mc.gameMode == null) return 0;
        var menu = player.containerMenu;
        if (menu == null) return 0;
        int dropped = 0;
        for (int slotIdx = 0; slotIdx < menu.slots.size(); slotIdx++) {
            var slot = menu.slots.get(slotIdx);
            if (slot.container != player.getInventory()) continue;
            if (slot.index >= 36) continue;
            ItemStack s = slot.getItem();
            if (s.isEmpty() || !ItemStack.isSameItemSameComponents(s, stack)) continue;
            mc.gameMode.handleInventoryMouseClick(
                    menu.containerId, slotIdx, 1, ClickType.THROW, player);
            dropped++;
        }
        return dropped;
    }

    private void restoreSearchScreen() {
        var stashlight = Stashlight.getInstance();
        if (stashlight == null || stashlight.getRepository() == null) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.screen == null) {
            mc.setScreen(new SearchScreen(stashlight.getRepository(), SearchPage.CRAFT));
        }
    }

    private void showProgress() {
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(
                    Component.translatable("gui.stashlight.message.craftProgress", craftedSoFar, craftTarget),
                    true);
        }
    }

    private void message(String key, Object... args) {
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable(key, args), true);
        }
    }

    public boolean isRunning() {
        return state != State.IDLE;
    }
}
