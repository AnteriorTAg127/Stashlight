package strangequark.chestfinder.logic.sort;

import strangequark.chestfinder.gui.UIStyle;
import strangequark.chestfinder.model.IndexedItem;

import java.util.List;

public class AlphabeticalSort implements SortStrategy {
    @Override
    public String getLabel() {
        return UIStyle.ICON_NAME;
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