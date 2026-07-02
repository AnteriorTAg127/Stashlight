package dev.strangequark.stashlight.logic.filter;

import dev.strangequark.stashlight.config.Config;
import dev.strangequark.stashlight.model.IndexedItem;
import net.minecraft.network.chat.Component;

public final class SmallContainerFilter implements FilterStrategy {

    private static final int THRESHOLD = 9;

    @Override
    public Component getLabel() {
        return Component.empty();
    }

    @Override
    public boolean matches(IndexedItem item) {
        if (Config.get().showSmallContainers()) {
            return true;
        }

        return item.containerCapacity() >= THRESHOLD;
    }
}
