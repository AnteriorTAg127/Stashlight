package dev.strangequark.stashlight.take;

import dev.strangequark.stashlight.Stashlight;
import dev.strangequark.stashlight.config.Config;
import dev.strangequark.stashlight.model.DataSourceMode;
import dev.strangequark.stashlight.model.DisplayItem;
import dev.strangequark.stashlight.model.IndexedItem;
import dev.strangequark.stashlight.model.StackKey;
import dev.strangequark.stashlight.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Session-level take queue. Survives opening/closing the search screen but is
 * cleared when the player disconnects from the world.
 */
public final class TakeQueue {

    private static final double VANILLA_REACH = 4.5;

    private final List<TakeQueueEntry> entries = new ArrayList<>();

    public List<TakeQueueEntry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public boolean add(DisplayItem item, int quantity) {
        StackKey key = new StackKey(item.stack());
        int qty = Math.max(1, quantity);

        for (int i = 0; i < entries.size(); i++) {
            TakeQueueEntry existing = entries.get(i);
            if (existing.key().equals(key)) {
                int mergedQty = existing.quantity() + qty;
                ItemStack mergedStack = existing.displayStack().copy();
                mergedStack.setCount(mergedQty);
                entries.set(i, new TakeQueueEntry(key, mergedStack, mergedQty));
                return true;
            }
        }

        if (isFull()) return false;
        ItemStack stack = item.stack().copy();
        stack.setCount(qty);
        entries.add(new TakeQueueEntry(key, stack, qty));
        return true;
    }

    public void remove(int index) {
        if (index >= 0 && index < entries.size()) {
            entries.remove(index);
        }
    }

    public void clear() {
        entries.clear();
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public boolean isFull() {
        return entries.size() >= Config.get().takeQueue().capacity();
    }

    /**
     * Re-resolve this entry against the current repository so we take from the
     * most up-to-date container positions. Returns empty if the item no longer
     * exists in the index.
     */
    public Optional<DisplayItem> resolve(TakeQueueEntry entry) {
        var stashlight = Stashlight.getInstance();
        if (stashlight == null) return Optional.empty();
        var repository = stashlight.getRepository();
        if (repository == null) return Optional.empty();

        List<IndexedItem> matches = new ArrayList<>();
        for (IndexedItem item : repository.getSearchIndex(DataSourceMode.MERGED)) {
            if (entry.key().equals(new StackKey(item.stack()))) {
                matches.add(item);
            }
        }
        if (matches.isEmpty()) return Optional.empty();

        ItemStack merged = matches.get(0).stack().copy();
        merged.setCount(matches.stream().mapToInt(i -> i.stack().getCount()).sum());
        return Optional.of(new DisplayItem(merged, matches));
    }

    /**
     * Whether the resolved item has at least one source currently within take
     * range. Out-of-range entries are shown gray in the queue UI and skipped
     * when processing.
     */
    public boolean isReachable(TakeQueueEntry entry) {
        Optional<DisplayItem> resolved = resolve(entry);
        if (resolved.isEmpty()) return false;
        return isReachable(resolved.get());
    }

    public boolean isReachable(DisplayItem item) {
        var player = Minecraft.getInstance().player;
        if (player == null || item.sources() == null || item.sources().isEmpty()) return false;

        double maxReach = getMaxReach();
        if (maxReach <= 0) return true;

        double reachSq = maxReach * maxReach;
        for (IndexedItem source : item.sources()) {
            if (source.pos() == null) continue;
            if (player.position().distanceToSqr(Vec3.atCenterOf(source.pos())) <= reachSq) {
                return true;
            }
        }
        return false;
    }

    /**
     * Re-resolve the entry and keep only sources that are currently within take
     * range. This avoids sending take requests to far-away containers.
     */
    public Optional<DisplayItem> resolveReachable(TakeQueueEntry entry) {
        Optional<DisplayItem> resolved = resolve(entry);
        if (resolved.isEmpty()) return Optional.empty();

        var player = Minecraft.getInstance().player;
        if (player == null) return Optional.empty();

        double maxReach = getMaxReach();
        if (maxReach <= 0) return resolved;

        double reachSq = maxReach * maxReach;
        List<IndexedItem> reachable = new ArrayList<>();
        for (IndexedItem source : resolved.get().sources()) {
            if (source.pos() == null) continue;
            if (player.position().distanceToSqr(Vec3.atCenterOf(source.pos())) <= reachSq) {
                reachable.add(source);
            }
        }
        if (reachable.isEmpty()) return Optional.empty();

        ItemStack merged = reachable.get(0).stack().copy();
        merged.setCount(reachable.stream().mapToInt(i -> i.stack().getCount()).sum());
        return Optional.of(new DisplayItem(merged, reachable));
    }

    private double getMaxReach() {
        var stashlight = Stashlight.getInstance();
        if (stashlight != null && stashlight.isModdedTakeAvailable()) {
            return stashlight.getServerMaxRadius();
        }
        return VANILLA_REACH;
    }
}
