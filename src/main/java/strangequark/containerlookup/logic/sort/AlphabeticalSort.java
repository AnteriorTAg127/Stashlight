package strangequark.containerlookup.logic.sort;

import strangequark.containerlookup.gui.UIStyle;
import strangequark.containerlookup.model.IndexedItem;

import java.util.List;

public class AlphabeticalSort implements SortStrategy {
    @Override
    public SortKey key() {
        return SortKey.ALPHABETICAL;
    }

    @Override
    public String getLabel() {
        return UIStyle.SORT_NAME;
    }

    @Override
    public String getTooltip() {
        return "Sort by Name";
    }

    @Override
    public void sort(List<IndexedItem> items) {
        items.sort((a, b) -> a.stack().getName().getString().compareToIgnoreCase(b.stack().getName().getString()));
    }
}