package dev.strangequark.stashlight.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * C2S payload requesting to take items from a server-side container.
 * Introduced in protocol v3.
 * <p>
 * Byte layout (per PRD §6.2):
 * <pre>
 * BlockPos pos        // writeBlockPos
 * int slot            // writeVarInt
 * ItemStack target    // ItemStack.STREAM_CODEC
 * int count           // writeVarInt
 * int nonce           // writeVarInt
 * </pre>
 */
public record TakeItemRequestPayload(
        BlockPos pos,
        int slot,
        ItemStack target,
        int count,
        int nonce
) implements CustomPacketPayload {

    public static final Type<TakeItemRequestPayload> TYPE =
            new Type<>(StashlightPayloads.TAKE_ITEM_REQUEST);

    public static final StreamCodec<RegistryFriendlyByteBuf, TakeItemRequestPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.pos());
                buf.writeVarInt(p.slot());
                ItemStack.STREAM_CODEC.encode(buf, p.target());
                buf.writeVarInt(p.count());
                buf.writeVarInt(p.nonce());
            },
            buf -> new TakeItemRequestPayload(
                    buf.readBlockPos(),
                    buf.readVarInt(),
                    ItemStack.STREAM_CODEC.decode(buf),
                    buf.readVarInt(),
                    buf.readVarInt()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
