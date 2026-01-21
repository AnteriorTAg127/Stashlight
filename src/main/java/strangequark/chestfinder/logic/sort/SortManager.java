package strangequark.chestfinder.logic.sort;

import java.util.List;

public class SortManager {

    private static final List<SortStrategy> DEFAULT_STRATEGIES = List.of(
            new AlphabeticalSort(),
            new CountSort(),
            new DistanceSort()
    );

    private final List<SortStrategy> strategies;
    private int currentIndex = 0;

    public SortManager() {
        this.strategies = DEFAULT_STRATEGIES;
    }
    
    public void cycle() {
        currentIndex = (currentIndex + 1) % strategies.size();
    }

    public SortStrategy getCurrent() {
        return strategies.get(currentIndex);
    }
}
