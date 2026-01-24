package strangequark.containerlookup.logic.sort;

import strangequark.containerlookup.model.IndexedItem;

import java.util.List;

public interface SortStrategy {
    SortKey key();

    String getLabel();

    String getTooltip();

    void sort(List<IndexedItem> items);
}
