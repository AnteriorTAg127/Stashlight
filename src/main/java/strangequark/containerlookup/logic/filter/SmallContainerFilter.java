package strangequark.containerlookup.logic.filter;

import strangequark.containerlookup.config.Config;
import strangequark.containerlookup.model.IndexedItem;

public final class SmallContainerFilter implements FilterStrategy {

    private static final int THRESHOLD = 9;

    @Override
    public String getLabel() {
        return "";
    }

    @Override
    public boolean matches(IndexedItem item) {
        if (Config.get().showSmallContainers()) {
            return true;
        }

        return item.containerCapacity() >= THRESHOLD;
    }
}
