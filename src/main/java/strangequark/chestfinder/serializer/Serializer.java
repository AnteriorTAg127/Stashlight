package strangequark.chestfinder.serializer;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import strangequark.chestfinder.ChestFinder;
import strangequark.chestfinder.model.ContainerSnapshot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Serializer {
    private final Path file;
    private final RegistryWrapper.WrapperLookup lookup;

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
                        dimTag.getCompound(key).ifPresent(snapNbt ->
                                posMap.put(pos, deserializeSnapshot(snapNbt))
                        );
                    }
                    database.put(dim, posMap);
                });
            }
        } catch (Exception e) {
            ChestFinder.LOGGER.error("Load failed", e);
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
            ChestFinder.LOGGER.error("Save failed", e);
        }
    }

    private NbtCompound serializeSnapshot(ContainerSnapshot snap) {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("name", snap.containerName());
        nbt.putInt("capacity", snap.containerCapacity());
        nbt.putLong("time", snap.timestamp());

        NbtList itemList = new NbtList();
        for (ItemStack stack : snap.items()) {
            itemList.add(serializeStack(stack));
        }
        nbt.put("items", itemList);
        return nbt;
    }

    private ContainerSnapshot deserializeSnapshot(NbtCompound nbt) {
        String name = nbt.getString("name").orElse("");
        int capacity = nbt.getInt("capacity").orElse(0);
        long timestamp = nbt.getLong("time").orElse(0L);

        List<ItemStack> items = new ArrayList<>();
        nbt.getList("items").ifPresent(itemList -> {
            for (int i = 0; i < itemList.size(); i++) {
                // Safe access to the compound inside the list
                itemList.getCompound(i)
                        .map(this::deserializeStack)
                        .ifPresent(items::add);
            }
        });
        return new ContainerSnapshot(name, capacity, items, timestamp);
    }

    private NbtElement serializeStack(ItemStack stack) {
        var ops = RegistryOps.of(NbtOps.INSTANCE, lookup);
        return ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
    }

    private ItemStack deserializeStack(NbtCompound nbt) {
        var ops = RegistryOps.of(NbtOps.INSTANCE, lookup);
        return ItemStack.CODEC.parse(ops, nbt).getOrThrow();
    }

}