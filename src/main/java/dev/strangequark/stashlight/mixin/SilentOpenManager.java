package dev.strangequark.stashlight.mixin;

import dev.strangequark.stashlight.model.SlotStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.AbstractContainerMenu;
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
 *   <li>{@link #begin(BlockPos, Consumer)} — set a pending silent open at {@code pos},
 *       with a callback to receive the captured {@link SlotStack} list.</li>
 *   <li>Mixin in {@link ClientPacketListenerMixin} calls {@link #capture(AbstractContainerMenu)}
 *       when the server responds with {@code OpenScreen}. The mixin creates the menu
 *       but skips {@code setScreen}. The menu is stored for content capture.</li>
 *   <li>{@link #onContentReady()} is called when content arrives (via tick poll or
 *       {@code handleContainerContent}). It reads the menu slots and invokes the callback.</li>
 *   <li>{@link #finish()} clears state.</li>
 *   <li>{@link #timeoutCheck()} is called every tick — if {@code deadlineMs} passes
 *       without completion, the state is force-cleared to prevent leaking into
 *       subsequent manual chest opens.</li>
 * </ol>
 */
public final class SilentOpenManager {

    private SilentOpenManager() {
    }

    // ── state ──────────────────────────────────────────────────────────────
    @Nullable
    private static BlockPos pendingPos = null;

    @Nullable
    private static AbstractContainerMenu pendingMenu = null;

    private static int expectedContainerId = -1;

    private static long deadlineMs = 0L;

    @Nullable
    private static Consumer<List<SlotStack>> callback = null;

    // ── public API ─────────────────────────────────────────────────────────

    /**
     * Returns {@code true} while a silent open sequence is in progress.
     * The mixin checks this flag to decide whether to intercept {@code handleOpenScreen}.
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
        pendingMenu = null;
        expectedContainerId = -1;
        callback = onResult;
        deadlineMs = System.currentTimeMillis() + 3000L; // 3-second timeout
    }

    /**
     * Called by the {@code handleOpenScreen} mixin after it has created the
     * container menu (but skipped {@code setScreen}). Stores the menu for
     * content extraction.
     *
     * @param menu the menu that was created
     */
    public static void capture(AbstractContainerMenu menu) {
        if (!isSilent()) return;
        pendingMenu = menu;
        expectedContainerId = menu.containerId;
        deadlineMs = System.currentTimeMillis() + 3000L;
    }

    /**
     * Called when container content has arrived (e.g. via tick-poll after
     * {@code handleContainerContent}). Reads all non-empty slots from the
     * pending menu and fires the callback.
     * <p>
     * Safe to call even if content hasn't arrived yet — it will fire once
     * and clear the state.
     */
    public static void onContentReady() {
        if (!isSilent() || pendingMenu == null || callback == null) return;

        List<SlotStack> slots = new ArrayList<>();
        for (var slot : pendingMenu.slots) {
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
     * Check whether the content is ready by verifying the pending menu
     * has at least as many slots as the expected container size (any slot
     * with a non-empty item).
     */
    public static boolean isContentReady() {
        if (!isSilent() || pendingMenu == null) return false;
        // Content is "ready" when at least one slot has data.
        // The server always sends the full container content in the
        // ContainerSetContent packet, so any non-empty slot indicates
        // the content has arrived.
        for (var slot : pendingMenu.slots) {
            if (!slot.getItem().isEmpty()) return true;
        }
        return false;
    }

    /**
     * Force-clear all state. Used by timeout check or on disconnect.
     */
    public static void finish() {
        pendingPos = null;
        pendingMenu = null;
        expectedContainerId = -1;
        callback = null;
        deadlineMs = 0L;
    }

    /**
     * Called every client tick. If a silent open sequence has exceeded its
     * deadline, the state is force-cleared (and the container close packet
     * should be sent by the caller).
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
     * Used by the VanillaScanner/VanillaTaker to close the correct container.
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
