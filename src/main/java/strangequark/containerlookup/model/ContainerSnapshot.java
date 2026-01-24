package strangequark.containerlookup.model;

import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * Represents a frozen state of a specific container in the world.
 */
public record ContainerSnapshot(
        String containerName,
        int containerCapacity,
        List<ItemStack> items,
        long timestamp
) {
}
