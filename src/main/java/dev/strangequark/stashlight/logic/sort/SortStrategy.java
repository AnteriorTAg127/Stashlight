package dev.strangequark.stashlight.logic.sort;

import dev.strangequark.stashlight.model.IndexedItem;

import java.util.List;

public interface SortStrategy {
    SortKey key();

    String getLabel();

    String getTooltip();

    void sort(List<IndexedItem> items);
}
