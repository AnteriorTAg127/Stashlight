package dev.strangequark.stashlight.take;

import dev.strangequark.stashlight.model.StackKey;
import net.minecraft.world.item.ItemStack;

/**
 * A single entry in the take queue. Stores enough information to render the
 * slot and to re-resolve the live {@link dev.strangequark.stashlight.model.DisplayItem}
 * when the queue is finally processed.
 */
public record TakeQueueEntry(StackKey key, ItemStack displayStack, int quantity) {
}
