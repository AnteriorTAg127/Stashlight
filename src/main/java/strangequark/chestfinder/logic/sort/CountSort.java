package strangequark.chestfinder.logic.sort;

import strangequark.chestfinder.model.IndexedItem;

import java.util.List;

public class CountSort implements SortStrategy {
    @Override
    public String getLabel() {
        return "#";
    }

    @Override
    public void sort(List<IndexedItem> items) {
        items.sort((a, b) -> Integer.compare(b.stack().getCount(), a.stack().getCount()));
    }
}