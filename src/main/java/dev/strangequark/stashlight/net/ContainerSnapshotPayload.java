package dev.strangequark.stashlight.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A single container snapshot encoded for network transfer.
 */
public record ContainerSnapshotPayload(
        String dimension,
        BlockPos pos,
        String containerName,
        int capacity,
        List<SlotStackSnapshot> slotStacks
) {
    public static final StreamCodec<RegistryFriendlyByteBuf, ContainerSnapshotPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.dimension);
                buf.writeBlockPos(payload.pos);
                buf.writeUtf(payload.containerName);
                buf.writeVarInt(payload.capacity);
                buf.writeVarInt(payload.slotStacks.size());
                for (SlotStackSnapshot slot : payload.slotStacks) {
                    buf.writeVarInt(slot.slot());
                    ItemStack.STREAM_CODEC.encode(buf, slot.stack());
                }
            },
            buf -> {
                String dimension = buf.readUtf();
                BlockPos pos = buf.readBlockPos();
                String containerName = buf.readUtf();
                int capacity = buf.readVarInt();
                int count = buf.readVarInt();
                List<SlotStackSnapshot> slots = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    int slot = buf.readVarInt();
                    ItemStack stack = ItemStack.STREAM_CODEC.decode(buf);
                    slots.add(new SlotStackSnapshot(slot, stack));
                }
                return new ContainerSnapshotPayload(dimension, pos, containerName, capacity, slots);
            }
    );

    public record SlotStackSnapshot(int slot, ItemStack stack) {
    }
}
