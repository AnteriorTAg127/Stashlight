package strangequark.chestfinder.logic.filter;

import java.util.ArrayList;
import java.util.List;

public class FilterManager {
    private final List<FilterStrategy> strategies = new ArrayList<>();
    private int currentIndex;

    public void setStrategies(List<FilterStrategy> newStrategies) {
        this.strategies.clear();
        this.strategies.addAll(newStrategies);
        this.currentIndex = 0;
    }

    public void cycle() {
        if (strategies.isEmpty()) return;
        currentIndex = (currentIndex + 1) % strategies.size();
    }

    public FilterStrategy getCurrent() {
        return strategies.isEmpty() ? null : strategies.get(currentIndex);
    }
}