package strangequark.chestfinder.model;

import net.minecraft.util.math.BlockPos;

public record ItemTile(String dimension,
                       String containerName,
                       String itemId,
                       BlockPos pos,
                       int totalCount,
                       long timestamp
) {
}



