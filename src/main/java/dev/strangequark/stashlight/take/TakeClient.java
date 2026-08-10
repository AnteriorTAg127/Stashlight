package dev.strangequark.stashlight.take;

import dev.strangequark.stashlight.Stashlight;
import dev.strangequark.stashlight.config.Config;
import dev.strangequark.stashlight.model.DisplayItem;
import dev.strangequark.stashlight.model.IndexedItem;
import dev.strangequark.stashlight.model.LocatePath;
import dev.strangequark.stashlight.take.TakeQueueEntry;
import dev.strangequark.stashlight.net.TakeItemRequestPayload;
import dev.strangequark.stashlight.net.TakeItemResponsePayload;
import dev.strangequark.stashlight.net.TakeResult;
import dev.strangequark.stashlight.repository.ContainerRepository;
import dev.strangequark.stashlight.scan.VanillaTaker;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Client-side handler for modded-mode remote item taking.
 * <p>
 * Sends {@link TakeItemRequestPayload} to the server and matches responses
 * via nonce. Falls back to {@link VanillaTaker} when modded take is unavailable.
 */
public final class TakeClient {

    private final AtomicInteger nonceGen = new AtomicInteger(1);
    private final Map<Integer, Consumer<TakeItemResponsePayload>> pending = new ConcurrentHashMap<>();

    private int totalTaken = 0;
    private int totalWanted = 0;
    private int expectedResponses = 0;
    private int completedResponses = 0;

    private final List<TakeQueueEntry> queuedEntries = new ArrayList<>();
    private int queueIndex = -1;
    private Runnable onAllDone;
    private Runnable queueDoneCallback;

    private record PlannedTake(BlockPos pos, int slot, ItemStack target, int requestCount,
                               boolean box, int contentCount) {
    }

    private record BoxKey(BlockPos pos, int slot) {
    }

    private static final class BoxGroup {
        final BlockPos pos;
        final String dimension;
        final int topSlot;
        int contentCount = 0;
        ItemStack topStack = ItemStack.EMPTY;

        BoxGroup(BlockPos pos, String dimension, int topSlot) {
            this.pos = pos;
            this.dimension = dimension;
            this.topSlot = topSlot;
        }
    }

    /**
     * Initiate a take operation for the given item and quantity.
     * Falls back to {@link VanillaTaker} when modded take is unavailable.
     */
    public void startTake(DisplayItem item, int count) {
        var stashlight = Stashlight.getInstance();
        if (stashlight == null) return;

        // Vanilla fallback
        if (!stashlight.isModdedTakeAvailable()) {
            VanillaTaker.start(item, count);
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Close any open screen unless the user wants to keep the search UI.
        // modded take is pure network packets, so keeping the screen is safe.
        if (!Config.get().remoteTake().keepScreenOnTake()) {
            mc.setScreen(null);
        }

        executeTake(item, count, () -> {
            var st = Stashlight.getInstance();
            if (st != null) st.requestServerScan();
        });
    }

    /**
     * Execute a single take operation. {@code onDone} is called once all pending
     * responses for this operation have been received.
     */
    private void executeTake(DisplayItem item, int count, Runnable onDone) {
        this.onAllDone = onDone;
        totalTaken = 0;
        totalWanted = count;
        expectedResponses = 0;
        completedResponses = 0;

        List<PlannedTake> plan = planTakes(item, count);
        if (plan.isEmpty()) {
            onDone.run();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        for (PlannedTake op : plan) {
            int nonce = nonceGen.getAndIncrement();
            expectedResponses++;

            pending.put(nonce, response -> {
                if (response.result() == TakeResult.SUCCESS.ordinal()) {
                    int gained = op.box()
                            ? op.contentCount() * response.taken() / Math.max(1, op.requestCount())
                            : response.taken();
                    totalTaken += gained;
                    showProgress(totalTaken, totalWanted);
                } else if (mc.player != null) {
                    mc.player.displayClientMessage(
                            Component.translatable("gui.stashlight.message.takeFailed",
                                    TakeResult.values()[response.result()].name()),
                            true);
                }
                completedResponses++;
                if (completedResponses >= expectedResponses && expectedResponses > 0) {
                    if (onAllDone != null) onAllDone.run();
                }
            });

            ClientPlayNetworking.send(new TakeItemRequestPayload(
                    op.pos(), op.slot(), op.target(), op.requestCount(), nonce
            ));
        }
    }

    /**
     * Plan the take sequence for the requested quantity.
     * <p>
     * Strategy (when "take containing box" is enabled):
     * <ol>
     *   <li>Take whole nested boxes whose content fits in the remaining count.</li>
     *   <li>Fill the remainder with loose items.</li>
     *   <li>If loose items are insufficient, take the smallest remaining whole box
     *       (allowing a slight over-take).</li>
     * </ol>
     */
    private List<PlannedTake> planTakes(DisplayItem item, int requested) {
        List<PlannedTake> plan = new ArrayList<>();
        if (requested <= 0) return plan;

        var stashlight = Stashlight.getInstance();
        ContainerRepository repository = stashlight != null ? stashlight.getRepository() : null;

        if (!Config.get().remoteTake().takeContainingBoxEnabled() || repository == null) {
            for (IndexedItem source : item.sources()) {
                int have = source.stack().getCount();
                if (have <= 0) continue;
                int want = Math.min(requested - alreadyPlanned(plan), have);
                if (want <= 0) break;
                plan.add(new PlannedTake(source.pos(), topSlotOf(source), source.stack(), want, false, want));
            }
            return plan;
        }

        Map<BoxKey, BoxGroup> boxGroups = new LinkedHashMap<>();
        List<IndexedItem> loose = new ArrayList<>();

        for (IndexedItem source : item.sources()) {
            LocatePath path = source.path();
            if (path == null || path.slots().size() <= 1) {
                loose.add(source);
                continue;
            }
            int topSlot = path.topSlot();
            if (topSlot < 0) {
                loose.add(source);
                continue;
            }
            BoxKey key = new BoxKey(source.pos(), topSlot);
            BoxGroup group = boxGroups.computeIfAbsent(key, k -> new BoxGroup(source.pos(), source.dimension(), topSlot));
            group.contentCount += source.stack().getCount();
        }

        List<BoxGroup> boxes = new ArrayList<>();
        for (BoxGroup group : boxGroups.values()) {
            if (group.contentCount <= 0) continue;
            ItemStack top = repository.getTopLevelStack(group.dimension, group.pos, group.topSlot);
            if (top == null || top.isEmpty()) continue;
            group.topStack = top;
            boxes.add(group);
        }

        int remaining = requested;

        // 1. Whole boxes that fit, largest first to minimize the number of boxes.
        boxes.sort(Comparator.comparingInt((BoxGroup b) -> b.contentCount).reversed());
        List<BoxGroup> skipped = new ArrayList<>();
        for (BoxGroup box : boxes) {
            if (remaining <= 0) break;
            if (box.contentCount <= remaining) {
                plan.add(new PlannedTake(box.pos, box.topSlot, box.topStack,
                        box.topStack.getCount(), true, box.contentCount));
                remaining -= box.contentCount;
            } else {
                skipped.add(box);
            }
        }

        // 2. Loose items for whatever is left.
        if (remaining > 0) {
            for (IndexedItem source : loose) {
                int have = source.stack().getCount();
                if (have <= 0) continue;
                int want = Math.min(remaining, have);
                plan.add(new PlannedTake(source.pos(), topSlotOf(source), source.stack(), want, false, want));
                remaining -= want;
                if (remaining <= 0) break;
            }
        }

        // 3. Still short and we have a whole box left? Take the smallest one.
        if (remaining > 0 && !skipped.isEmpty()) {
            skipped.sort(Comparator.comparingInt(b -> b.contentCount));
            BoxGroup box = skipped.get(0);
            plan.add(new PlannedTake(box.pos, box.topSlot, box.topStack,
                    box.topStack.getCount(), true, box.contentCount));
        }

        return plan;
    }

    private static int alreadyPlanned(List<PlannedTake> plan) {
        int sum = 0;
        for (PlannedTake op : plan) {
            sum += op.box() ? op.contentCount() : op.requestCount();
        }
        return sum;
    }

    private static int topSlotOf(IndexedItem source) {
        LocatePath path = source.path();
        return path == null ? -1 : path.topSlot();
    }

    /**
     * Called from {@code Stashlight.handleTakeItemResponse} when a response arrives.
     */
    public void handleResponse(TakeItemResponsePayload payload) {
        Consumer<TakeItemResponsePayload> cb = pending.remove(payload.nonce());
        if (cb != null) {
            cb.accept(payload);
        }
    }

    private static void showProgress(int taken, int wanted) {
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(
                    Component.translatable("gui.stashlight.message.takingProgress", taken, wanted),
                    true);
        }
    }

    /**
     * Process a list of queue entries sequentially. Unreachable or resolved-empty
     * entries are skipped without stopping the queue.
     */
    public void startQueue(List<TakeQueueEntry> entries) {
        var stashlight = Stashlight.getInstance();
        if (stashlight == null) return;

        if (!stashlight.isModdedTakeAvailable()) {
            VanillaTaker.startQueue(entries);
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (!Config.get().remoteTake().keepScreenOnTake()) {
            mc.setScreen(null);
        }

        queuedEntries.clear();
        TakeQueue queue = stashlight.getTakeQueue();
        for (TakeQueueEntry entry : entries) {
            if (queue.resolveReachable(entry).isPresent()) {
                queuedEntries.add(entry);
            }
        }

        if (queuedEntries.isEmpty()) {
            if (mc.player != null) {
                mc.player.displayClientMessage(
                        Component.translatable("gui.stashlight.message.queueNothingReachable"), true);
            }
            // One-shot consume, same as onQueueFinished: let the chained callback
            // (e.g. take-then-craft) proceed and report missing materials.
            Runnable cb = queueDoneCallback;
            queueDoneCallback = null;
            if (cb != null) cb.run();
            return;
        }

        queueIndex = 0;
        processNextQueueEntry();
    }

    private void processNextQueueEntry() {
        var stashlight = Stashlight.getInstance();
        if (stashlight == null || queueIndex >= queuedEntries.size()) {
            onQueueFinished();
            return;
        }

        TakeQueue queue = stashlight.getTakeQueue();
        TakeQueueEntry entry = queuedEntries.get(queueIndex);
        var resolved = queue.resolveReachable(entry);
        if (resolved.isEmpty()) {
            queueIndex++;
            processNextQueueEntry();
            return;
        }

        executeTake(resolved.get(), entry.quantity(), this::onQueueEntryDone);
    }

    private void onQueueEntryDone() {
        queueIndex++;
        processNextQueueEntry();
    }

    private void onQueueFinished() {
        queuedEntries.clear();
        queueIndex = -1;
        var stashlight = Stashlight.getInstance();
        if (stashlight != null) stashlight.requestServerScan();
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(
                    Component.translatable("gui.stashlight.message.queueDone"), true);
        }
        Runnable cb = queueDoneCallback;
        queueDoneCallback = null;
        if (cb != null) cb.run();
    }

    /**
     * Set a one-shot callback invoked once the current queue take finishes.
     * Used to chain "take then craft".
     */
    public void setQueueDoneCallback(Runnable callback) {
        this.queueDoneCallback = callback;
    }

    /**
     * Reset state (e.g. on disconnect).
     */
    public void reset() {
        pending.clear();
        totalTaken = 0;
        totalWanted = 0;
        expectedResponses = 0;
        completedResponses = 0;
        queuedEntries.clear();
        queueIndex = -1;
        onAllDone = null;
        queueDoneCallback = null;
    }
}
