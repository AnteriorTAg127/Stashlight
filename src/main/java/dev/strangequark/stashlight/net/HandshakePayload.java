package dev.strangequark.stashlight.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C payload announcing that the server has Stashlight enabled and its limits.
 * <p>
 * Protocol v3 adds an optional {@code takeEnabled} field, only encoded/decoded
 * when {@code protocolVersion >= 3}. This ensures v2↔v3 bidirectional
 * compatibility:
 * <ul>
 *   <li>v3 server → v2 client: client reads only the first 3 fields (the 4th
 *       byte remains in the buffer, silently ignored).</li>
 *   <li>v2 server → v3 client: {@code protocolVersion=2}, decoder does NOT
 *       call {@code readBoolean()} for the 4th field — no underflow.</li>
 * </ul>
 */
public record HandshakePayload(
        boolean enabled,
        int maxRadius,
        int protocolVersion,
        boolean takeEnabled
) implements CustomPacketPayload {

    public static final Type<HandshakePayload> TYPE = new Type<>(StashlightPayloads.HANDSHAKE);

    public static final StreamCodec<RegistryFriendlyByteBuf, HandshakePayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBoolean(payload.enabled);
                buf.writeVarInt(payload.maxRadius);
                buf.writeVarInt(payload.protocolVersion);
                if (payload.protocolVersion() >= 3) {
                    buf.writeBoolean(payload.takeEnabled());
                }
            },
            buf -> {
                boolean enabled = buf.readBoolean();
                int maxRadius = buf.readVarInt();
                int protocolVersion = buf.readVarInt();
                boolean takeEnabled = protocolVersion >= 3 && buf.readBoolean();
                return new HandshakePayload(enabled, maxRadius, protocolVersion, takeEnabled);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
