package strangequark.chestfinder.logic.sort;

import strangequark.chestfinder.model.IndexedItem;

import java.util.List;

public interface SortStrategy {
    String getLabel();

    void sort(List<IndexedItem> items);
}
