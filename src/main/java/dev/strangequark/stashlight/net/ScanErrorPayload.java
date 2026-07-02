package dev.strangequark.stashlight.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C payload reporting that a scan request was rejected.
 */
public record ScanErrorPayload(
        int nonce,
        String reason
) implements CustomPacketPayload {

    public static final Type<ScanErrorPayload> TYPE = new Type<>(StashlightPayloads.SCAN_ERROR);

    public static final StreamCodec<RegistryFriendlyByteBuf, ScanErrorPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.nonce);
                buf.writeUtf(payload.reason);
            },
            buf -> new ScanErrorPayload(
                    buf.readVarInt(),
                    buf.readUtf()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
