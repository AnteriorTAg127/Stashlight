package strangequark.chestfinder.model;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

/**
 * A 'ready-to-search' entry. We pre-calculate the sums and
 * attach the metadata so the UI doesn't have to do any math.
 */
public record IndexedItem(
        ItemStack stack,
        BlockPos pos,
        String dimension,
        String containerName,
        int containerCapacity,
        long timestamp
) {
}