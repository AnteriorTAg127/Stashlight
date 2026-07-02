package dev.strangequark.stashlight.util;

import dev.strangequark.stashlight.model.EnchantEntry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;

public final class EnchantmentExtractor {
    private EnchantmentExtractor() {
    }

    /**
     * Extracts all relevant enchantments from an item stack.
     * Covers both stored enchantments (enchanted books) and active item enchantments.
     */
    public static List<EnchantEntry> extract(ItemStack stack) {
        List<EnchantEntry> entries = new ArrayList<>();
        if (stack == null || stack.isEmpty()) return entries;

        var stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored != null) {
            appendEnchantments(stored, entries);
        }

        var active = stack.get(DataComponents.ENCHANTMENTS);
        if (active != null) {
            appendEnchantments(active, entries);
        }

        return entries;
    }

    private static void appendEnchantments(ItemEnchantments enchantments, List<EnchantEntry> entries) {
        for (var entry : enchantments.entrySet()) {
            Enchantment enchantment = entry.getKey().value();
            ResourceLocation id = entry.getKey().unwrapKey()
                    .map(key -> key.location())
                    .orElse(null);
            if (id == null) continue;

            Component displayName = enchantment.description();
            int level = entry.getIntValue();
            entries.add(new EnchantEntry(id, displayName, level));
        }
    }
}
