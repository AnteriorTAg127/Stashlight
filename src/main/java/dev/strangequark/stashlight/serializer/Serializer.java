package dev.strangequark.stashlight.serializer;

import dev.strangequark.stashlight.Stashlight;
import dev.strangequark.stashlight.model.ContainerSnapshot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Serializer {
    private final Path file;
    private final RegistryWrapper.WrapperLookup lookup;

    private static final String NBT_CONTAINER_NAME_KEY = "name";
    private static final String NBT_CONTAINER_CAPACITY_KEY = "capacity";
    private static final String NBT_STACK_LIST_KEY = "items";
    private static final String NBT_TIMESTAMP_KEY = "time";

    public Serializer(Path file, RegistryWrapper.WrapperLookup lookup) {
        this.file = file;
        this.lookup = lookup;
    }

    public Map<String, Map<BlockPos, ContainerSnapshot>> read() {
        Map<String, Map<BlockPos, ContainerSnapshot>> database = new HashMap<>();
        if (file == null || !Files.exists(file)) return database;

        try {
            NbtCompound root = NbtIo.readCompressed(file, NbtSizeTracker.ofUnlimitedBytes());

            for (String dim : root.getKeys()) {
                root.getCompound(dim).ifPresent(dimTag -> {
                    Map<BlockPos, ContainerSnapshot> posMap = new HashMap<>();
                    for (String key : dimTag.getKeys()) {
                        BlockPos pos = BlockPos.fromLong(Long.parseLong(key));
                        dimTag.getCompound(key).ifPresent(snapNbt -> posMap.put(pos, deserializeSnapshot(snapNbt)));
                    }
                    database.put(dim, posMap);
                });
            }
        } catch (Exception e) {
            Stashlight.LOGGER.error("Load failed", e);
        }
        return database;
    }

    public void write(Map<String, Map<BlockPos, ContainerSnapshot>> database) {
        if (file == null) return;

        NbtCompound root = new NbtCompound();
        database.forEach((dim, posMap) -> {
            NbtCompound dimTag = new NbtCompound();
            posMap.forEach((pos, snap) -> dimTag.put(String.valueOf(pos.asLong()), serializeSnapshot(snap)));
            root.put(dim, dimTag);
        });

        try {
            NbtIo.writeCompressed(root, file);
        } catch (Exception e) {
            Stashlight.LOGGER.error("Save failed", e);
        }
    }

    private NbtCompound serializeSnapshot(ContainerSnapshot snap) {
        NbtCompound nbt = new NbtCompound();
        nbt.putString(NBT_CONTAINER_NAME_KEY, snap.containerName());
        nbt.putInt(NBT_CONTAINER_CAPACITY_KEY, snap.containerCapacity());
        nbt.putLong(NBT_TIMESTAMP_KEY, snap.timestamp());

        NbtList itemList = new NbtList();
        for (ItemStack stack : snap.items()) {
            itemList.add(serializeStack(stack));
        }
        nbt.put(NBT_STACK_LIST_KEY, itemList);
        return nbt;
    }

    private ContainerSnapshot deserializeSnapshot(NbtCompound nbt) {
        String name = nbt.getString(NBT_CONTAINER_NAME_KEY).orElse("");
        int capacity = nbt.getInt(NBT_CONTAINER_CAPACITY_KEY).orElse(0);
        long timestamp = nbt.getLong(NBT_TIMESTAMP_KEY).orElse(0L);

        List<ItemStack> items = new ArrayList<>();
        nbt.getList(NBT_STACK_LIST_KEY).ifPresent(itemList -> {
            for (int i = 0; i < itemList.size(); i++) {
                itemList.getCompound(i)
                        .map(this::deserializeStack)
                        .ifPresent(items::add);
            }
        });
        return new ContainerSnapshot(name, capacity, items, timestamp);
    }

    private NbtElement serializeStack(ItemStack stack) {
        try {
            var ops = RegistryOps.of(NbtOps.INSTANCE, lookup);
            return ItemStack.CODEC.encodeStart(ops, stack)
                    .resultOrPartial(error -> Stashlight.LOGGER.warn("Failed to serialize item: {}", error))
                    .orElse(new NbtCompound());
        } catch (Exception e) {
            Stashlight.LOGGER.warn("ItemStack serialization failed for {}", stack.getItem(), e);
            return new NbtCompound();
        }
    }

    private ItemStack deserializeStack(NbtCompound nbt) {
        try {
            var ops = RegistryOps.of(NbtOps.INSTANCE, lookup);
            return ItemStack.CODEC.parse(ops, nbt)
                    .resultOrPartial(error -> Stashlight.LOGGER.warn("Failed to deserialize item: {}", error))
                    .orElse(ItemStack.EMPTY);
        } catch (Exception e) {
            Stashlight.LOGGER.warn("ItemStack deserialization failed", e);
            return ItemStack.EMPTY;
        }
    }

}