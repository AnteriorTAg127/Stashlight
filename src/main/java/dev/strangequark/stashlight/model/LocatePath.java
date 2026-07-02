package dev.strangequark.stashlight.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Path from a container block to a specific (possibly nested) item.
 * The first element is the top-level container slot; subsequent elements
 * are slots inside nested containers such as shulker boxes or bundles.
 */
public record LocatePath(List<Integer> slots) {
    public LocatePath {
        slots = List.copyOf(slots);
    }

    public LocatePath(int containerSlot) {
        this(Collections.singletonList(containerSlot));
    }

    public LocatePath withNestedSlot(int slot) {
        List<Integer> next = new ArrayList<>(slots);
        next.add(slot);
        return new LocatePath(next);
    }

    /**
     * Returns the top-level slot inside the outer container.
     */
    public int topSlot() {
        return slots.isEmpty() ? -1 : slots.get(0);
    }
}
