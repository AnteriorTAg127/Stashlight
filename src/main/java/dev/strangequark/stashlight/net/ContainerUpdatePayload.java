package dev.strangequark.stashlight.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C payload carrying a chunk of incremental container updates.
 *
 * <p>Used by both join-time push and per-scan-request incremental responses.
 * The client applies {@code updated} via {@code repository.updateServer(...)}
 * and {@code removed} via {@code repository.removeServer(...)}; when the final
 * chunk arrives ({@code chunkIndex == chunkTotal - 1}) it refreshes any open
 * search screen and updates the data-freshness timestamp.</p>
 */
public record ContainerUpdatePayload(
        List<ContainerSnapshotPayload> updated,
        List<ContainerRemovedEntry> removed,
        int chunkIndex,
        int chunkTotal
) implements CustomPacketPayload {

    public static final Type<ContainerUpdatePayload> TYPE = new Type<>(StashlightPayloads.CONTAINER_UPDATE);

    public static final StreamCodec<RegistryFriendlyByteBuf, ContainerUpdatePayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.chunkIndex);
                buf.writeVarInt(payload.chunkTotal);

                buf.writeVarInt(payload.updated.size());
                for (ContainerSnapshotPayload container : payload.updated) {
                    ContainerSnapshotPayload.CODEC.encode(buf, container);
                }

                buf.writeVarInt(payload.removed.size());
                for (ContainerRemovedEntry entry : payload.removed) {
                    ContainerRemovedEntry.CODEC.encode(buf, entry);
                }
            },
            buf -> {
                int chunkIndex = buf.readVarInt();
                int chunkTotal = buf.readVarInt();

                int updatedCount = buf.readVarInt();
                List<ContainerSnapshotPayload> updated = new ArrayList<>(updatedCount);
                for (int i = 0; i < updatedCount; i++) {
                    updated.add(ContainerSnapshotPayload.CODEC.decode(buf));
                }

                int removedCount = buf.readVarInt();
                List<ContainerRemovedEntry> removed = new ArrayList<>(removedCount);
                for (int i = 0; i < removedCount; i++) {
                    removed.add(ContainerRemovedEntry.CODEC.decode(buf));
                }

                return new ContainerUpdatePayload(updated, removed, chunkIndex, chunkTotal);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
