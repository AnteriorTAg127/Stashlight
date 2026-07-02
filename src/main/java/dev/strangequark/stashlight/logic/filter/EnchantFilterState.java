package dev.strangequark.stashlight.logic.filter;

import dev.strangequark.stashlight.model.EnchantEntry;
import dev.strangequark.stashlight.model.IndexedItem;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Holds the user's current enchantment filter selection.
 */
public final class EnchantFilterState {
    private final Map<ResourceLocation, LevelRange> selected = new HashMap<>();
    private boolean andMode = true;

    public void clear() {
        selected.clear();
    }

    public void apply(Map<ResourceLocation, LevelRange> selection) {
        selected.clear();
        selected.putAll(selection);
    }

    public void setAndMode(boolean andMode) {
        this.andMode = andMode;
    }

    public boolean isAndMode() {
        return andMode;
    }

    public Map<ResourceLocation, LevelRange> getSelected() {
        return new HashMap<>(selected);
    }

    public void toggle(ResourceLocation id, int defaultMin, int defaultMax) {
        if (selected.containsKey(id)) {
            selected.remove(id);
        } else {
            selected.put(id, new LevelRange(defaultMin, defaultMax));
        }
    }

    public void setRange(ResourceLocation id, int min, int max) {
        if (!selected.containsKey(id)) return;
        selected.put(id, new LevelRange(min, max));
    }

    public boolean isEmpty() {
        return selected.isEmpty();
    }

    /**
     * Returns true if the item satisfies the selected enchantment constraints.
     */
    public boolean matches(IndexedItem item) {
        if (selected.isEmpty()) return true;

        int matched = 0;
        for (var entry : selected.entrySet()) {
            ResourceLocation id = entry.getKey();
            LevelRange range = entry.getValue();
            boolean found = item.enchantments().stream()
                    .anyMatch(e -> e.id().equals(id) && range.contains(e.level()));
            if (found) {
                if (!andMode) return true;
                matched++;
            } else if (andMode) {
                return false;
            }
        }
        return andMode && matched == selected.size();
    }

    public record LevelRange(int min, int max) {
        public LevelRange {
            if (min > max) {
                int tmp = min;
                min = max;
                max = tmp;
            }
            min = Math.max(1, min);
            max = Math.min(255, Math.max(min, max));
        }

        public boolean contains(int level) {
            return level >= min && level <= max;
        }
    }
}
