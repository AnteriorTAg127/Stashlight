package strangequark.chestfinder.logic.sort;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import strangequark.chestfinder.gui.UIStyle;
import strangequark.chestfinder.model.IndexedItem;

import java.util.List;

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
    public String getTooltip() {
        return "Sort by Distance";
    }

    @Override
    public void sort(List<IndexedItem> items) {
        var player = MinecraftClient.getInstance().player;
        if (player == null) return;

        BlockPos playerPos = player.getBlockPos();

        items.sort((a, b) -> {
            double distA = a.pos().getSquaredDistance(playerPos);
            double distB = b.pos().getSquaredDistance(playerPos);
            return Double.compare(distA, distB);
        });
    }
}