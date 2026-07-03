package dev.strangequark.stashlight.scan;

import dev.strangequark.stashlight.config.Config;
import dev.strangequark.stashlight.scan.SilentOpenManager;
import dev.strangequark.stashlight.model.DisplayItem;
import dev.strangequark.stashlight.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import dev.strangequark.stashlight.model.DataSourceMode;
import dev.strangequark.stashlight.model.SlotStack;
import dev.strangequark.stashlight.repository.ContainerRepository;
import dev.strangequark.stashlight.screen.SearchScreen;

/**
 * Vanilla-fallback taker. When the server has no modded take capability,
 * this silently opens containers, verifies the target item by NBT, takes
 * exactly the requested quantity (using SHIFT_LEFT for full stacks and
 * cursor-split for partial stacks), and closes the container — all without
 * showing a screen.
 * <p>
 * Non-blocking state machine (IDLE / OPEN / TAKE / CLOSE / INTERVAL / DONE)
 * driven by the client tick.
 */
public final class VanillaTaker {

    private static final Logger LOGGER = LoggerFactory.getLogger("Stashlight/VanillaTaker");

    private enum State { IDLE, BUILD_CANDIDATES, OPEN, WAIT_CONTENT, TAKE, CLOSE, INTERVAL, DONE }

    private static VanillaTaker INSTANCE;

    private State state = State.IDLE;
    private List<BlockPos> candidates;
    private int candidateIndex;
    private ItemStack targetStack;
    private int remaining;
    private int totalWanted;
    private int takenSoFar;
    private int tickCounter;
    private ContainerRepository repository;
    private int restorePendingTicks = 0;

    public VanillaTaker() {
        INSTANCE = this;
    }

    public void setRepository(ContainerRepository repo) {
        this.repository = repo;
    }

    /**
     * Initiate a vanilla-fallback take operation.
     */
    public static void start(DisplayItem item, int count) {
        if (INSTANCE == null) {
            INSTANCE = new VanillaTaker();
        }
        INSTANCE.begin(item, count);
    }

    private void begin(DisplayItem item, int count) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // Close any open screen
        mc.setScreen(null);

        targetStack = item.stack();
        totalWanted = count;
        remaining = count;
        takenSoFar = 0;
        candidateIndex = 0;
        tickCounter = 0;

        state = State.BUILD_CANDIDATES;
    }

    /**
     * Called every client tick. Drives the non-blocking state machine.
     */
    public void tick(Minecraft client) {
        if (state == State.IDLE) {
            // Deferred search-screen restore: wait a few ticks after the take
            // finishes so the server's async ContainerClose packet (which would
            // setScreen(null)) has been processed before we reopen SearchScreen.
            if (restorePendingTicks > 0) {
                restorePendingTicks--;
                if (restorePendingTicks == 0) {
                    restoreSearchScreen();
                }
            }
            return;
        }

        var cfg = Config.get().vanillaFallback();
        tickCounter++;

        switch (state) {
            case BUILD_CANDIDATES -> {
                candidates = buildReachableContainers(client);
                if (candidates.isEmpty()) {
                    finish("No containers in range");
                    return;
                }
                candidateIndex = 0;
                state = State.OPEN;
                tickCounter = 0;
            }
            case OPEN -> {
                if (tickCounter < 2) break; // wait before opening
                if (candidateIndex >= candidates.size() || remaining <= 0) {
                    state = State.DONE;
                    tickCounter = 0;
                    break;
                }
                if (interruptCheck(client)) {
                    finish("Interrupted");
                    return;
                }
                BlockPos pos = candidates.get(candidateIndex);
                openContainer(client, pos);
                state = State.WAIT_CONTENT;
                tickCounter = 0;
            }
            case WAIT_CONTENT -> {
                if (tickCounter < 3) break; // wait for content
                if (SilentOpenManager.isContentReady()) {
                    state = State.TAKE;
                    tickCounter = 0;
                } else if (tickCounter > 40) {
                    // timeout — skip this container
                    closeContainer(client, SilentOpenManager.getExpectedContainerId());
                    SilentOpenManager.finish();
                    candidateIndex++;
                    state = State.INTERVAL;
                    tickCounter = 0;
                }
            }
            case TAKE -> {
                if (tickCounter < 1) break;
                if (remaining <= 0) {
                    SilentOpenManager.finish();
                    state = State.DONE;
                    break;
                }
                int taken = takeFromOpenContainer(client, targetStack, remaining);
                boolean hadItem = taken > 0;
                if (hadItem) {
                    remaining -= taken;
                    takenSoFar += taken;
                    showProgress();
                } else {
                    // Item not found in this container — skip
                    LOGGER.debug("Item not found in container {}, skipping", candidateIndex);
                }
                // Refresh this container's cached inventory before closing — the
                // container is already open, so reading slots is free and keeps
                // the repository in sync without an extra scan pass.
                if (candidateIndex < candidates.size()) {
                    refreshRepositoryContainer(client, candidates.get(candidateIndex));
                }
                closeContainer(client, SilentOpenManager.getExpectedContainerId());
                SilentOpenManager.finish();
                candidateIndex++;
                // Only wait the interval if we actually took items; empty containers skip ahead
                state = hadItem ? State.INTERVAL : State.OPEN;
                tickCounter = 0;
            }
            case INTERVAL -> {
                int intervalTicks = Math.max(1, cfg.takeIntervalMillis() / 50);
                if (tickCounter >= intervalTicks) {
                    state = State.OPEN;
                    tickCounter = 0;
                }
            }
            case DONE -> {
                if (client.player != null) {
                    Component msg;
                    if (remaining <= 0) {
                        msg = Component.translatable("gui.stashlight.message.takeDone", takenSoFar);
                    } else {
                        msg = Component.translatable("gui.stashlight.message.takePartial", takenSoFar, totalWanted);
                    }
                    client.player.displayClientMessage(msg, true);
                }
                LOGGER.info("VanillaTaker done: {} taken of {} wanted", takenSoFar, totalWanted);
                state = State.IDLE;
                restorePendingTicks = 5;
            }
        }
    }

    private int takeFromOpenContainer(Minecraft client, ItemStack target, int maxCount) {
        var player = client.player;
        if (player == null) return 0;

        var menu = player.containerMenu;
        var slots = menu.slots;
        boolean dropOnFull = Config.get().remoteTake().dropOnFullEnabled();
        // Measure actual take by inventory delta, so the reported count stays
        // correct even when shift-click transfers partially or the server
        // rejects a click. Decouples counting from click-success assumptions.
        int before = countInInventory(player, target);
        int dropped = 0;

        for (int i = 0; i < slots.size(); i++) {
            var slot = slots.get(i);
            if (slot.container == player.getInventory()) continue;

            ItemStack slotStack = slot.getItem();
            if (slotStack.isEmpty()) continue;
            if (!ItemStack.isSameItemSameComponents(slotStack, target)) continue;

            int inInv = countInInventory(player, target) - before;
            int stillNeed = maxCount - inInv - dropped;
            if (stillNeed <= 0) break;
            int have = slotStack.getCount();

            if (stillNeed >= have) {
                // Take entire stack via shift-click
                int invBefore = countInInventory(player, target);
                client.gameMode.handleInventoryMouseClick(
                        menu.containerId, i, 0, ClickType.QUICK_MOVE, player);
                int transferred = countInInventory(player, target) - invBefore;
                int remain = have - transferred;
                if (remain > 0 && dropOnFull) {
                    int needDrop = stillNeed - transferred;
                    int toDrop = Math.min(remain, needDrop);
                    if (toDrop > 0) {
                        dropFromSlot(client, menu.containerId, i, toDrop, remain);
                        dropped += toDrop;
                    }
                }
            } else {
                // Partial stack: cursor-split
                // 1. Pick up entire stack
                client.gameMode.handleInventoryMouseClick(
                        menu.containerId, i, 0, ClickType.PICKUP, player);
                // 2. Right-click `stillNeed` times into empty main-inventory slots (places 1 each)
                int placed = 0;
                for (int j = 0; j < slots.size() && placed < stillNeed; j++) {
                    var invSlot = slots.get(j);
                    if (invSlot.container != player.getInventory()) continue;
                    // Only target main inventory + hotbar (Inventory index 0..35),
                    // excluding armor (36-39), offhand (40), and crafting slots.
                    if (invSlot.index >= 36) continue;
                    if (!invSlot.getItem().isEmpty()) continue;
                    client.gameMode.handleInventoryMouseClick(
                            menu.containerId, j, 1, ClickType.PICKUP, player);
                    placed++;
                }
                // 3. Put the remainder back
                client.gameMode.handleInventoryMouseClick(
                        menu.containerId, i, 0, ClickType.PICKUP, player);
                // 4. If inventory was full and drop is enabled, drop the unplaced portion
                if (placed < stillNeed && dropOnFull) {
                    int toDrop = stillNeed - placed;
                    dropFromSlot(client, menu.containerId, i, toDrop, have - placed);
                    dropped += toDrop;
                }
            }
        }

        int after = countInInventory(player, target);
        return Math.max(0, after - before) + dropped;
    }

    /**
     * Drop {@code toDrop} items from container slot {@code slotId} onto the ground
     * in front of the player. {@code remainInSlot} is the item count currently in
     * the slot (used to decide between THROW-all and THROW-one loops).
     */
    private static void dropFromSlot(Minecraft client, int containerId, int slotId, int toDrop, int remainInSlot) {
        var player = client.player;
        if (player == null) return;
        if (toDrop <= 0 || remainInSlot <= 0) return;
        if (toDrop >= remainInSlot) {
            // THROW button=1 drops the whole stack
            client.gameMode.handleInventoryMouseClick(
                    containerId, slotId, 1, ClickType.THROW, player);
        } else {
            // THROW button=0 drops 1 item; repeat for the needed count
            for (int k = 0; k < toDrop; k++) {
                client.gameMode.handleInventoryMouseClick(
                        containerId, slotId, 0, ClickType.THROW, player);
            }
        }
    }

    /**
     * Count how many of {@code target} (matching item + components) the player
     * currently holds in main inventory + hotbar (slots 0..35). Used to measure
     * the actual take delta around click operations.
     */
    private static int countInInventory(LocalPlayer player, ItemStack target) {
        int count = 0;
        var inv = player.getInventory();
        for (int i = 0; i < 36; i++) {
            var stack = inv.getItem(i);
            if (ItemStack.isSameItemSameComponents(stack, target)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private List<BlockPos> buildReachableContainers(Minecraft client) {
        if (client.level == null || client.player == null) return List.of();

        String currentDim = Util.getDimensionName(client.level);
        double reachSq = 4.5 * 4.5;
        BlockPos center = client.player.blockPosition();
        var cfg = Config.get().vanillaFallback();

        // Step 1: Collect indexed positions that have the target item (deduplicated to block-level)
        Set<BlockPos> indexedPositions = new LinkedHashSet<>();
        if (repository != null && targetStack != null) {
            indexedPositions = repository.findContainerPositions(targetStack, currentDim, DataSourceMode.MERGED);
        }

        // Step 2: Filter indexed positions to reachable range
        List<BlockPos> result = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos pos : indexedPositions) {
            if (pos.distSqr(center) <= reachSq) {
                result.add(pos);
                seen.add(pos);
            }
        }

        // If only indexed containers are wanted, skip brute-force scan
        if (cfg.takeOnlyIndexed()) {
            return result;
        }

        // Step 3: Brute-force scan remaining containers not in the index
        int radius = 5;
        int maxScanSlots = cfg.maxContainersPerLoop() - result.size();
        if (maxScanSlots > 0) {
            for (int dx = -radius; dx <= radius && result.size() < maxScanSlots; dx++) {
                for (int dz = -radius; dz <= radius && result.size() < maxScanSlots; dz++) {
                    for (int dy = -2; dy <= 3 && result.size() < maxScanSlots; dy++) {
                        BlockPos pos = center.offset(dx, dy, dz);
                        if (pos.distSqr(center) > reachSq) continue;
                        if (!client.level.isLoaded(pos)) continue;
                        var state = client.level.getBlockState(pos);
                        if (Util.isValidSearchableContainer(state)) {
                            BlockPos canonical = Util.getCanonicalPos(client.level, pos);
                            if (seen.add(canonical)) {
                                result.add(canonical);
                            }
                        }
                    }
                }
            }
        }

        return result;
    }

    private boolean interruptCheck(Minecraft client) {
        if (client.player != null && client.player.hurtTime > 0) return true;
        return false;
    }

    private void openContainer(Minecraft client, BlockPos pos) {
        SilentOpenManager.begin(pos, null);
        client.gameMode.useItemOn(
                client.player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)
        );
    }

    private void closeContainer(Minecraft client, int containerId) {
        if (containerId < 0) return;
        if (client.player == null) return;
        try {
            client.player.closeContainer();
        } catch (Exception e) {
            LOGGER.warn("Failed to close container {}", containerId, e);
        }
    }

    private void showProgress() {
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(
                    Component.translatable("gui.stashlight.message.takingProgress", takenSoFar, totalWanted),
                    true);
        }
    }

    private void finish(String reason) {
        LOGGER.info("VanillaTaker stopped: {}", reason);
        int menuId = SilentOpenManager.getExpectedContainerId();
        if (menuId >= 0) closeContainer(Minecraft.getInstance(), menuId);
        SilentOpenManager.finish();
        state = State.IDLE;
        restorePendingTicks = 5;
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.translatable("gui.stashlight.message.takeStop", reason), true);
        }
    }


    /**
     * Read the currently-open container's slots and write them back into the
     * repository. Called after taking items (the container is still open) so
     * the cached inventory reflects the post-take state without an extra scan.
     */
    private void refreshRepositoryContainer(Minecraft client, BlockPos pos) {
        if (repository == null || pos == null || client.level == null || client.player == null) return;
        var menu = client.player.containerMenu;
        if (menu == null) return;

        String dim = Util.getDimensionName(client.level);
        BlockPos canonical = Util.getCanonicalPos(client.level, pos);
        BlockState blockState = client.level.getBlockState(canonical);
        String name = blockState.getBlock().getName().getString();

        List<SlotStack> slots = new ArrayList<>();
        int containerSize = 0;
        for (var slot : menu.slots) {
            if (slot.container == client.player.getInventory()) continue;
            containerSize++;
            var stack = slot.getItem();
            if (!stack.isEmpty()) {
                slots.add(new SlotStack(slot.index, stack.copy()));
            }
        }
        repository.remove(dim, canonical);
        repository.update(dim, canonical, name, containerSize, slots);
    }

    /**
     * Reopen the search screen after a vanilla-fallback take completes, when
     * {@code keepScreenOnTake} is enabled. Called from the tick loop after the
     * deferred-restore countdown reaches zero.
     */
    private void restoreSearchScreen() {
        if (!Config.get().remoteTake().keepScreenOnTake()) return;
        if (repository == null) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.setScreen(new SearchScreen(repository));
    }

    public boolean isRunning() {
        return state != State.IDLE;
    }
}
