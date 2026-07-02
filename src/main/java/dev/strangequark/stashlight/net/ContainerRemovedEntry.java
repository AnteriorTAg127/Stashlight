package dev.strangequark.stashlight.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Identifies a container that no longer exists (e.g. block was broken) and
 * should be removed from the client's server-cache.
 */
public record ContainerRemovedEntry(
        String dimension,
        BlockPos pos
) {
    public static final StreamCodec<RegistryFriendlyByteBuf, ContainerRemovedEntry> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.dimension);
                buf.writeBlockPos(payload.pos);
            },
            buf -> new ContainerRemovedEntry(
                    buf.readUtf(),
                    buf.readBlockPos()
            )
    );
}
