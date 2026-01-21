package strangequark.chestfinder.logic.sort;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import strangequark.chestfinder.model.IndexedItem;

import java.util.List;

public class DistanceSort implements SortStrategy {
    @Override
    public String getLabel() {
        return "D";
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