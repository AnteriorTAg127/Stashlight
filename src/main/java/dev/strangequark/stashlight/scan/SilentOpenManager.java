package dev.strangequark.stashlight.scan;

import dev.strangequark.stashlight.model.SlotStack;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Manages the state machine for silent (screen-less) container openings.
 * <p>
 * Lifecycle:
 * <ol>
 *   <li>{@link #begin(BlockPos, Consumer)} — set a pending silent open at
 *       {@code pos}, with a callback to receive captured {@link SlotStack}.</li>
 *   <li>The {@link dev.strangequark.stashlight.mixin.ClientPacketListenerMixin}
 *       cancels {@code Minecraft.setScreen} while the normal packet handler
 *       creates the menu and sets {@code player.containerMenu}.</li>
 *   <li>{@link #isContentReady()} polls {@code player.containerMenu.slots}
 *       to detect when {@code ContainerSetContent} has arrived.</li>
 *   <li>{@link #onContentReady()} reads slots and fires the callback.</li>
 *   <li>{@link #timeoutCheck()} — clears state if deadline exceeded.</li>
 * </ol>
 */
public final class SilentOpenManager {

    private SilentOpenManager() {
    }

    // ── state ──────────────────────────────────────────────────────────────
    @Nullable
    private static BlockPos pendingPos = null;

    private static int expectedContainerId = -1;

    private static long deadlineMs = 0L;

    @Nullable
    private static Consumer<List<SlotStack>> callback = null;

    // ── public API ─────────────────────────────────────────────────────────

    /**
     * Returns {@code true} while a silent open sequence is in progress.
     */
    public static boolean isSilent() {
        return pendingPos != null;
    }

    /**
     * Begin a silent open sequence. Should be called just before
     * {@code mc.gameMode.useItemOn(...)} for the target container.
     *
     * @param pos      the container position we expect the server to open
     * @param onResult callback receiving the captured slot contents; called once
     *                 when {@link #onContentReady()} is invoked
     */
    public static void begin(BlockPos pos, Consumer<List<SlotStack>> onResult) {
        pendingPos = pos;
        expectedContainerId = -1;
        callback = onResult;
        deadlineMs = System.currentTimeMillis() + 3000L; // 3-second timeout
    }

    /**
     * Check whether the content is ready by verifying the player's current
     * container menu has at least one non-empty slot.
     */
    public static boolean isContentReady() {
        if (!isSilent()) return false;
        var mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        var menu = mc.player.containerMenu;
        if (menu == null) return false;
        expectedContainerId = menu.containerId;
        for (var slot : menu.slots) {
            if (!slot.getItem().isEmpty()) return true;
        }
        return false;
    }

    /**
     * Called when container content has arrived. Reads all non-empty slots
     * from the player's {@code containerMenu} and fires the callback.
     * Safe to call even if content hasn't arrived yet — it fires once
     * and clears the state.
     */
    public static void onContentReady() {
        if (!isSilent() || callback == null) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.containerMenu == null) return;

        List<SlotStack> slots = new ArrayList<>();
        for (var slot : mc.player.containerMenu.slots) {
            ItemStack stack = slot.getItem();
            if (stack != null && !stack.isEmpty()) {
                slots.add(new SlotStack(slot.index, stack.copy()));
            }
        }

        Consumer<List<SlotStack>> cb = callback;
        finish();
        cb.accept(slots);
    }

    /**
     * Force-clear all state. Used by timeout check or on disconnect.
     */
    public static void finish() {
        pendingPos = null;
        expectedContainerId = -1;
        callback = null;
        deadlineMs = 0L;
    }

    /**
     * Called every client tick. If a silent open sequence has exceeded its
     * deadline, the state is force-cleared.
     *
     * @return the expected container id if timed out, or -1 if nothing to close
     */
    public static int timeoutCheck() {
        if (!isSilent()) return -1;
        if (System.currentTimeMillis() < deadlineMs) return -1;

        int containerId = expectedContainerId;
        finish();
        return containerId >= 0 ? containerId : -1;
    }

    /**
     * Returns the expected container id for the current silent open, or -1.
     */
    public static int getExpectedContainerId() {
        return expectedContainerId;
    }

    /**
     * Returns the pending position, or null.
     */
    @Nullable
    public static BlockPos getPendingPos() {
        return pendingPos;
    }
}
