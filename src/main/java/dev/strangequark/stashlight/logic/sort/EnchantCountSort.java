package dev.strangequark.stashlight.logic.sort;

import dev.strangequark.stashlight.gui.UIStyle;
import dev.strangequark.stashlight.model.DisplayItem;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.List;

public final class EnchantCountSort implements SortStrategy {

    @Override
    public SortKey key() {
        return SortKey.ENCHANT_COUNT;
    }

    @Override
    public String getLabel() {
        return UIStyle.SORT_ENCHANT_COUNT;
    }

    @Override
    public Component getTooltip() {
        return Component.translatable("gui.stashlight.sort.enchantCount.tooltip");
    }

    @Override
    public void sort(List<DisplayItem> items) {
        items.sort(Comparator
                .<DisplayItem>comparingInt(item -> item.enchantments().size())
                .reversed()
                .thenComparing(a -> a.stack().getHoverName().getString().toLowerCase()));
    }
}
