package strangequark.chestfinder.logic.sort;

import strangequark.chestfinder.gui.UIStyle;
import strangequark.chestfinder.model.IndexedItem;

import java.util.List;

public class CountSort implements SortStrategy {
    @Override
    public String getLabel() {
        return UIStyle.ICON_COUNT;
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