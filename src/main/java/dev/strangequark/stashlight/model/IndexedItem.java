package dev.strangequark.stashlight.model;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.List;

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
        long timestamp,
        String searchKey,
        Source source,
        LocatePath path,
        List<EnchantEntry> enchantments
) {
    public IndexedItem(ItemStack stack, BlockPos pos, String dimension, String containerName, int containerCapacity, long timestamp) {
        this(
                stack,
                pos,
                dimension,
                containerName,
                containerCapacity,
                timestamp,
                stack.getHoverName().getString().toLowerCase(),
                Source.LOCAL_OPEN,
                new LocatePath(-1),
                Collections.emptyList()
        );
    }

    public IndexedItem(ItemStack stack, BlockPos pos, String dimension, String containerName, int containerCapacity, long timestamp, Source source, LocatePath path) {
        this(
                stack,
                pos,
                dimension,
                containerName,
                containerCapacity,
                timestamp,
                stack.getHoverName().getString().toLowerCase(),
                source,
                path,
                Collections.emptyList()
        );
    }
}