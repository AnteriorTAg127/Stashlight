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
import dev.strangequark.stashlight.model.IndexedItem;
import dev.strangequark.stashlight.repository.ContainerRepository;

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
    private StackKey targetKey;
    private int remaining;
    private int totalWanted;
    private int takenSoFar;
    private int tickCounter;
    private ContainerRepository repository;

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
        targetKey = new StackKey(targetStack);
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
        if (state == State.IDLE) return;

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
            }
        }
    }

    private int takeFromOpenContainer(Minecraft client, ItemStack target, int maxCount) {
        var player = client.player;
        if (player == null) return 0;

        var menu = player.containerMenu;
        int taken = 0;

        for (var slot : menu.slots) {
            if (taken >= maxCount) break;
            if (slot.container == player.getInventory()) continue;

            ItemStack slotStack = slot.getItem();
            if (slotStack.isEmpty()) continue;
            if (!ItemStack.isSameItemSameComponents(slotStack, target)) continue;

            int have = slotStack.getCount();
            int want = maxCount - taken;

            if (want >= have) {
                // Take entire stack via shift-click
                client.gameMode.handleInventoryMouseClick(
                        menu.containerId, slot.index, 0, ClickType.QUICK_MOVE, player);
                taken += have;
            } else {
                // Partial stack: cursor-split
                // 1. Pick up entire stack
                client.gameMode.handleInventoryMouseClick(
                        menu.containerId, slot.index, 0, ClickType.PICKUP, player);
                // 2. Click `want` times into empty inventory slots using right-click (places 1 each)
                int placed = 0;
                for (var invSlot : menu.slots) {
                    if (placed >= want) break;
                    if (invSlot.container != player.getInventory()) continue;
                    // Only target main inventory slots (0..35: hotbar + main),
                    // excluding armor (36-39), offhand (40), and crafting slots.
                    if (invSlot.index >= 36) continue;
                    if (!invSlot.getItem().isEmpty()) continue;
                    client.gameMode.handleInventoryMouseClick(
                            menu.containerId, invSlot.index, 1, ClickType.PICKUP, player);
                    placed++;
                }
                // 3. Put the remainder back
                client.gameMode.handleInventoryMouseClick(
                        menu.containerId, slot.index, 0, ClickType.PICKUP, player);
                taken += want;
            }
        }

        return taken;
    }

    private List<BlockPos> buildReachableContainers(Minecraft client) {
        if (client.level == null || client.player == null) return List.of();

        // Step 1: Collect indexed positions that have the target item
        Set<BlockPos> indexedPositions = new LinkedHashSet<>();
        if (repository != null && targetStack != null && targetKey != null) {
            String currentDim = Util.getDimensionName(client.level);
            List<IndexedItem> searchIndex = repository.getSearchIndex(DataSourceMode.MERGED);
            for (IndexedItem item : searchIndex) {
                if (!item.dimension().equals(currentDim)) continue;
                if (targetKey.equals(new StackKey(item.stack()))) {
                    indexedPositions.add(item.pos());
                }
            }
        }

        // Step 2: Start with indexed positions (tried first)
        List<BlockPos> result = new ArrayList<>(indexedPositions);
        Set<BlockPos> seen = new HashSet<>(indexedPositions);

        // If only indexed containers are wanted, skip brute-force scan
        if (Config.get().vanillaFallback().takeOnlyIndexed()) {
            return result;
        }

        // Step 3: Brute-force scan remaining containers not in the index
        BlockPos center = client.player.blockPosition();
        double reachSq = 4.5 * 4.5;
        int radius = 5;

        int maxScanSlots = Config.get().vanillaFallback().maxContainersPerLoop() - result.size();
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
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.translatable("gui.stashlight.message.takeStop", reason), true);
        }
    }

    /**
     * Simple stack-key for matching item type + components.
     */
    private record StackKey(ItemStack stack) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof StackKey k)) return false;
            return ItemStack.isSameItemSameComponents(stack, k.stack);
        }
    }

    public boolean isRunning() {
        return state != State.IDLE;
    }
}
