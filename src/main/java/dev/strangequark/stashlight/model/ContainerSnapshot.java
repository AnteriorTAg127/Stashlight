package dev.strangequark.stashlight.model;


import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a frozen state of a specific container in the world.
 */
public record ContainerSnapshot(
        String containerName,
        int containerCapacity,
        List<SlotStack> slotStacks,
        long timestamp
) {
    /**
     * Backwards-compatible view: returns the non-empty item stacks only,
     * without their slot indices. Used by legacy call sites.
     */
    public List<ItemStack> items() {
        List<ItemStack> items = new ArrayList<>();
        for (SlotStack slotStack : slotStacks) {
            if (slotStack.stack() != null && !slotStack.stack().isEmpty()) {
                items.add(slotStack.stack());
            }
        }
        return items;
    }
}
