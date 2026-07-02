package dev.strangequark.stashlight.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S payload requesting a scan around the player.
 */
public record ScanRequestPayload(
        int radius,
        String dimensionScope,
        int nonce
) implements CustomPacketPayload {

    public static final Type<ScanRequestPayload> TYPE = new Type<>(StashlightPayloads.SCAN_REQUEST);

    public static final StreamCodec<RegistryFriendlyByteBuf, ScanRequestPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.radius);
                buf.writeUtf(payload.dimensionScope);
                buf.writeVarInt(payload.nonce);
            },
            buf -> new ScanRequestPayload(
                    buf.readVarInt(),
                    buf.readUtf(),
                    buf.readVarInt()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
