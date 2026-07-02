package dev.strangequark.stashlight.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C payload announcing that the server has Stashlight enabled and its limits.
 */
public record HandshakePayload(
        boolean enabled,
        int maxRadius,
        int protocolVersion
) implements CustomPacketPayload {

    public static final Type<HandshakePayload> TYPE = new Type<>(StashlightPayloads.HANDSHAKE);

    public static final StreamCodec<RegistryFriendlyByteBuf, HandshakePayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBoolean(payload.enabled);
                buf.writeVarInt(payload.maxRadius);
                buf.writeVarInt(payload.protocolVersion);
            },
            buf -> new HandshakePayload(
                    buf.readBoolean(),
                    buf.readVarInt(),
                    buf.readVarInt()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
