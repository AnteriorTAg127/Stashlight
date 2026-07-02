package dev.strangequark.stashlight.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * C2S payload sent by a v2 client right after receiving the handshake.
 *
 * <p>Carries the client's locally-cached {@code SERVER_MAP} signatures so the
 * server can prime its {@code ContainerSignatureStore} and respond with an
 * incremental push (only changed / new containers). When the list is empty
 * (no persisted server data, first join), the server pushes everything.</p>
 */
public record ClientReadyPayload(
        List<ClientDimensionSignatures> cachedSignatures
) implements CustomPacketPayload {

    public static final Type<ClientReadyPayload> TYPE = new Type<>(StashlightPayloads.CLIENT_READY);

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientReadyPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.cachedSignatures.size());
                for (ClientDimensionSignatures dim : payload.cachedSignatures) {
                    buf.writeUtf(dim.dimension());
                    buf.writeVarInt(dim.entries().size());
                    for (ClientPositionSignature entry : dim.entries()) {
                        buf.writeLong(entry.posAsLong());
                        buf.writeLong(entry.signature());
                    }
                }
            },
            buf -> {
                int dimCount = buf.readVarInt();
                List<ClientDimensionSignatures> cached = new ArrayList<>(dimCount);
                for (int i = 0; i < dimCount; i++) {
                    String dimension = buf.readUtf();
                    int entryCount = buf.readVarInt();
                    List<ClientPositionSignature> entries = new ArrayList<>(entryCount);
                    for (int j = 0; j < entryCount; j++) {
                        long posAsLong = buf.readLong();
                        long signature = buf.readLong();
                        entries.add(new ClientPositionSignature(posAsLong, signature));
                    }
                    cached.add(new ClientDimensionSignatures(dimension, entries));
                }
                return new ClientReadyPayload(cached);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Per-dimension bucket of cached signatures. */
    public record ClientDimensionSignatures(
            String dimension,
            List<ClientPositionSignature> entries
    ) {
    }

    /** A single cached position-signature pair. Position packed as long to save bandwidth. */
    public record ClientPositionSignature(
            long posAsLong,
            long signature
    ) {
    }
}
