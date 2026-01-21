package strangequark.chestfinder.logic.sort;

import strangequark.chestfinder.model.IndexedItem;

import java.util.List;

public class AlphabeticalSort implements SortStrategy {
    @Override
    public String getLabel() {
        return "Aa";
    }

    @Override
    public void sort(List<IndexedItem> items) {
        items.sort((a, b) -> a.stack().getName().getString().compareToIgnoreCase(b.stack().getName().getString()));
    }
}