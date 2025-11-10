package strangequark.chestfinder.model;


import net.minecraft.util.math.BlockPos;

public record ContainerEntity(String itemId,
                              String containerName,
                              String dimension,
                              BlockPos pos,
                              int count,
                              long timestamp
) {
}

