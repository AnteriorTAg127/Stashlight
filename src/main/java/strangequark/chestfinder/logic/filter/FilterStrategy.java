package strangequark.chestfinder.logic.filter;

import strangequark.chestfinder.model.IndexedItem;

public interface FilterStrategy {
    String getLabel();

    boolean matches(IndexedItem item);
}
