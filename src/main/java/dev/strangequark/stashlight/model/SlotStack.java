package dev.strangequark.stashlight.model;

import net.minecraft.world.item.ItemStack;

/**
 * An item stack together with its original slot index inside a container.
 * A slot of -1 means the original slot is unknown (legacy data).
 */
public record SlotStack(int slot, ItemStack stack) {
}
