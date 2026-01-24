package strangequark.containerlookup.logic.filter;

import strangequark.containerlookup.model.IndexedItem;

public class DimensionFilter implements FilterStrategy {
    private final String label;
    private final String dimension;

    public DimensionFilter(String label, String dimension) {
        this.label = label;
        this.dimension = dimension;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public boolean matches(IndexedItem item) {
        if (dimension == null) return true; // "All"
        return item.dimension().equals(this.dimension);
    }
}