package strangequark.chestfinder.serializer;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import strangequark.chestfinder.ChestFinder;
import strangequark.chestfinder.model.ContainerSnapshot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class Serializer {
    private final Path file;
    private final RegistryWrapper.WrapperLookup lookup;

    public Serializer(Path file, RegistryWrapper.WrapperLookup lookup) {
        this.file = file;
        this.lookup = lookup;
    }


    public void write(Map<String, Map<BlockPos, ContainerSnapshot>> database) {
        if (file == null) return;

        NbtCompound root = new NbtCompound();
        database.forEach((dim, posMap) -> {
            NbtCompound dimTag = new NbtCompound();
            posMap.forEach((pos, snap) -> dimTag.put(String.valueOf(pos.asLong()), snap.serialize(this.lookup)));
            root.put(dim, dimTag);
        });

        try {
            NbtIo.writeCompressed(root, file);
        } catch (Exception e) {
            ChestFinder.LOGGER.error("Save failed", e);
        }
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
                                posMap.put(pos, ContainerSnapshot.deserialize(this.lookup, snapNbt))
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
}