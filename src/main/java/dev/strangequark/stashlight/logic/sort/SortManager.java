package dev.strangequark.stashlight.logic.sort;

import java.util.Map;

public class SortManager {

    private static final Map<SortKey, SortStrategy> STRATEGIES = Map.of(
            SortKey.ALPHABETICAL, new AlphabeticalSort(),
            SortKey.COUNT, new CountSort(),
            SortKey.DISTANCE, new DistanceSort()
    );

    private SortKey current;

    public SortManager(SortKey initial) {
        this.current = initial;
    }

    public void cycle() {
        SortKey[] keys = SortKey.values();
        int nextIndex = (current.ordinal() + 1) % keys.length;
        current = keys[nextIndex];
    }

    public SortStrategy getCurrent() {
        return STRATEGIES.get(current);
    }
}
