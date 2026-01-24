package dev.strangequark.stashlight.logic.filter;

import dev.strangequark.stashlight.model.IndexedItem;

public interface FilterStrategy {
    String getLabel();

    boolean matches(IndexedItem item);
}
