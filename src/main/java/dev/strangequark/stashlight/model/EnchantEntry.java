package dev.strangequark.stashlight.model;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * A single enchantment found on an indexed item or enchanted book.
 */
public record EnchantEntry(
        ResourceLocation id,
        Component displayName,
        int level
) {
}
