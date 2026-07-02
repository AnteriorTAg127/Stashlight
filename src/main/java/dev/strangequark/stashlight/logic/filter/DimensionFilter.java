package dev.strangequark.stashlight.logic.filter;

import dev.strangequark.stashlight.model.IndexedItem;
import net.minecraft.network.chat.Component;

public class DimensionFilter implements FilterStrategy {
    private final Component label;
    private final String dimension;

    public DimensionFilter(Component label, String dimension) {
        this.label = label;
        this.dimension = dimension;
    }

    @Override
    public Component getLabel() {
        return label;
    }

    @Override
    public boolean matches(IndexedItem item) {
        if (dimension == null) return true;
        return item.dimension().equals(this.dimension);
    }
}