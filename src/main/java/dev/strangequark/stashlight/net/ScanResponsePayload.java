package dev.strangequark.stashlight.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C payload carrying a chunk of scan results.
 */
public record ScanResponsePayload(
        int nonce,
        int chunkIndex,
        int chunkTotal,
        List<ContainerSnapshotPayload> containers,
        boolean truncated
) implements CustomPacketPayload {

    public static final Type<ScanResponsePayload> TYPE = new Type<>(StashlightPayloads.SCAN_RESPONSE);

    public static final StreamCodec<RegistryFriendlyByteBuf, ScanResponsePayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.nonce);
                buf.writeVarInt(payload.chunkIndex);
                buf.writeVarInt(payload.chunkTotal);
                buf.writeBoolean(payload.truncated);
                buf.writeVarInt(payload.containers.size());
                for (ContainerSnapshotPayload container : payload.containers) {
                    ContainerSnapshotPayload.CODEC.encode(buf, container);
                }
            },
            buf -> {
                int nonce = buf.readVarInt();
                int chunkIndex = buf.readVarInt();
                int chunkTotal = buf.readVarInt();
                boolean truncated = buf.readBoolean();
                int count = buf.readVarInt();
                List<ContainerSnapshotPayload> containers = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    containers.add(ContainerSnapshotPayload.CODEC.decode(buf));
                }
                return new ScanResponsePayload(nonce, chunkIndex, chunkTotal, containers, truncated);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
