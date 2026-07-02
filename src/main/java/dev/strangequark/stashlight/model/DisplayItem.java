package dev.strangequark.stashlight.model;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * An aggregated view of one or more indexed items that share the same
 * StackKey. Used by the UI to show combined counts while keeping the
 * underlying source items available for precise highlighting.
 */
public record DisplayItem(ItemStack stack, List<IndexedItem> sources) {
    public DisplayItem {
        sources = List.copyOf(sources);
    }

    public BlockPos pos() {
        return sources.isEmpty() ? BlockPos.ZERO : sources.get(0).pos();
    }

    public String dimension() {
        return sources.isEmpty() ? "" : sources.get(0).dimension();
    }

    public String containerName() {
        return sources.isEmpty() ? "" : sources.get(0).containerName();
    }

    public int containerCapacity() {
        return sources.isEmpty() ? 0 : sources.get(0).containerCapacity();
    }

    public long timestamp() {
        return sources.isEmpty() ? 0L : sources.get(0).timestamp();
    }

    public Source source() {
        return sources.isEmpty() ? Source.LOCAL_OPEN : sources.get(0).source();
    }

    /**
     * Combined enchantments from all source items, deduplicated by id and level.
     */
    public List<EnchantEntry> enchantments() {
        Set<String> seen = new LinkedHashSet<>();
        List<EnchantEntry> result = new ArrayList<>();
        for (IndexedItem item : sources) {
            for (EnchantEntry entry : item.enchantments()) {
                String key = entry.id() + "@" + entry.level();
                if (seen.add(key)) {
                    result.add(entry);
                }
            }
        }
        return result;
    }

    /**
     * Total count across all sources.
     */
    public int totalCount() {
        int count = 0;
        for (IndexedItem item : sources) {
            count += item.stack().getCount();
        }
        return count;
    }
}
