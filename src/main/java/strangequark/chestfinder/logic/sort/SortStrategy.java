package strangequark.chestfinder.logic.sort;

import strangequark.chestfinder.model.IndexedItem;

import java.util.List;

public interface SortStrategy {
    SortKey key();

    String getLabel();

    String getTooltip();

    void sort(List<IndexedItem> items);
}
