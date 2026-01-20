package strangequark.chestfinder.model;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Represents a frozen state of a specific container in the world.
 */
public record ContainerSnapshot(String containerName, long timestamp, List<ItemStack> items) {

    public static NbtCompound serializeStack(ItemStack stack, RegistryWrapper.WrapperLookup lookup) {
        RegistryOps<NbtElement> ops = RegistryOps.of(NbtOps.INSTANCE, lookup);
        return (NbtCompound) ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
    }

    public static ItemStack deserializeStack(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        RegistryOps<NbtElement> ops = RegistryOps.of(NbtOps.INSTANCE, lookup);
        return ItemStack.CODEC.parse(ops, nbt).getOrThrow();
    }

    public NbtCompound serialize(RegistryWrapper.WrapperLookup lookup) {
        NbtCompound nbt = new NbtCompound();
        RegistryOps<NbtElement> ops = RegistryOps.of(NbtOps.INSTANCE, lookup);

        nbt.putString("name", this.containerName);
        nbt.putLong("time", this.timestamp);

        NbtList itemList = new NbtList();
        for (ItemStack stack : items) {
            itemList.add(serializeStack(stack, lookup));
        }
        nbt.put("items", itemList);
        return nbt;
    }

    public static ContainerSnapshot deserialize(RegistryWrapper.WrapperLookup lookup, NbtCompound nbt) {
        String name = nbt.getString("name").orElse("");
        long timestamp = nbt.getLong("time").orElse(0L);
        Optional<NbtList> itemList = nbt.getList("items");

        List<ItemStack> items = new ArrayList<>();
        itemList.ifPresent(list -> {
            for (NbtElement element : list) {
                if (element instanceof NbtCompound stackNbt) {
                    items.add(deserializeStack(stackNbt, lookup));
                }
            }
        });

        return new ContainerSnapshot(name, timestamp, items);
    }
}
