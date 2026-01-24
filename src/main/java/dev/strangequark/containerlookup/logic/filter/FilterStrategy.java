package dev.strangequark.containerlookup.logic.filter;

import dev.strangequark.containerlookup.model.IndexedItem;

public interface FilterStrategy {
    String getLabel();

    boolean matches(IndexedItem item);
}
