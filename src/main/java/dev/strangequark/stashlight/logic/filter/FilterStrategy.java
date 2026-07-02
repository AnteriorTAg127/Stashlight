package dev.strangequark.stashlight.logic.filter;

import dev.strangequark.stashlight.model.IndexedItem;
import net.minecraft.network.chat.Component;

public interface FilterStrategy {
    Component getLabel();

    boolean matches(IndexedItem item);
}
