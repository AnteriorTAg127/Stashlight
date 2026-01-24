package strangequark.containerlookup.logic.filter;

import strangequark.containerlookup.model.IndexedItem;

public interface FilterStrategy {
    String getLabel();

    boolean matches(IndexedItem item);
}
