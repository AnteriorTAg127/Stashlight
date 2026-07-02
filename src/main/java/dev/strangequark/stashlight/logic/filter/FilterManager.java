package dev.strangequark.stashlight.logic.filter;

import dev.strangequark.stashlight.model.IndexedItem;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class FilterManager {

    private final List<FilterStrategy> cycling = new ArrayList<>();
    private final List<FilterStrategy> alwaysOn = new ArrayList<>();
    private int currentIndex;

    public void setCyclingStrategies(List<FilterStrategy> strategies) {
        cycling.clear();
        cycling.addAll(strategies);
        currentIndex = 0;
    }

    public void addAlwaysOn(FilterStrategy strategy) {
        alwaysOn.add(strategy);
    }

    public void cycle() {
        if (cycling.isEmpty()) return;
        currentIndex = (currentIndex + 1) % cycling.size();
    }

    public boolean matches(IndexedItem item) {
        for (var s : alwaysOn) {
            if (!s.matches(item)) return false;
        }

        if (cycling.isEmpty()) return true;
        return cycling.get(currentIndex).matches(item);
    }

    public Component getCurrentLabel() {
        return cycling.isEmpty() ? Component.empty() : cycling.get(currentIndex).getLabel();
    }
}
