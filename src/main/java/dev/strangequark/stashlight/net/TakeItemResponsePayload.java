package dev.strangequark.stashlight.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C response to a {@link TakeItemRequestPayload}.
 * Introduced in protocol v3.
 * <p>
 * Byte layout (per PRD §6.3):
 * <pre>
 * int nonce   // writeVarInt
 * int result  // writeVarInt (TakeResult ordinal)
 * int taken   // writeVarInt
 * </pre>
 */
public record TakeItemResponsePayload(
        int nonce,
        int result,
        int taken
) implements CustomPacketPayload {

    public static final Type<TakeItemResponsePayload> TYPE =
            new Type<>(StashlightPayloads.TAKE_ITEM_RESPONSE);

    public static final StreamCodec<RegistryFriendlyByteBuf, TakeItemResponsePayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.nonce());
                buf.writeVarInt(p.result());
                buf.writeVarInt(p.taken());
            },
            buf -> new TakeItemResponsePayload(
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
