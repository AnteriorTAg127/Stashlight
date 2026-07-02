package dev.strangequark.stashlight.logic.sort;

import dev.strangequark.stashlight.gui.UIStyle;
import dev.strangequark.stashlight.model.DisplayItem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class DistanceSort implements SortStrategy {
    @Override
    public SortKey key() {
        return SortKey.DISTANCE;
    }

    @Override
    public String getLabel() {
        return UIStyle.SORT_DIST;
    }

    @Override
    public Component getTooltip() {
        return Component.translatable("gui.stashlight.sort.distance.tooltip");
    }

    @Override
    public void sort(List<DisplayItem> items) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;

        BlockPos playerPos = player.getOnPos();

        Map<DisplayItem, Double> distCache = new IdentityHashMap<>(items.size());
        for (DisplayItem item : items) {
            distCache.put(item, item.pos().distSqr(playerPos));
        }

        items.sort(Comparator.comparingDouble(distCache::get));
    }
}