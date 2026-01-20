package strangequark.chestfinder.model;

import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * Represents a frozen state of a specific container in the world.
 */
public record ContainerSnapshot(
        String containerName,
        long timestamp,
        List<ItemStack> items) {
}
