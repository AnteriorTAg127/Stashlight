package dev.strangequark.containerlookup.logic.sort;

import dev.strangequark.containerlookup.gui.UIStyle;
import dev.strangequark.containerlookup.model.IndexedItem;

import java.util.List;

public class CountSort implements SortStrategy {
    @Override
    public SortKey key() {
        return SortKey.COUNT;
    }

    @Override
    public String getLabel() {
        return UIStyle.SORT_COUNT;
    }

    @Override
    public String getTooltip() {
        return "Sort by Count";
    }

    @Override
    public void sort(List<IndexedItem> items) {
        items.sort((a, b) -> Integer.compare(b.stack().getCount(), a.stack().getCount()));
    }
}