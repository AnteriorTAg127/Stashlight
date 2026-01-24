package strangequark.chestfinder.repository;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import strangequark.chestfinder.model.ContainerSnapshot;
import strangequark.chestfinder.model.IndexedItem;
import strangequark.chestfinder.model.StackKey;
import strangequark.chestfinder.serializer.Serializer;
import strangequark.chestfinder.util.Util;

import java.util.*;

public class ContainerRepository {

    private final Serializer serializer;
    private boolean isDirty = false;

    private final Map<String, Map<BlockPos, ContainerSnapshot>> CONTAINER_ENTRIES_MAP;
    private final List<IndexedItem> SEARCH_INDEX = new ArrayList<>();

    public ContainerRepository(Serializer serializer) {
        this.serializer = serializer;
        this.CONTAINER_ENTRIES_MAP = serializer.read();
        rebuildIndex();
    }

    public void runCleanup(ClientWorld world) {
        String dimension = Util.getDimensionName(world);
        Map<BlockPos, ContainerSnapshot> dataMap = CONTAINER_ENTRIES_MAP.get(dimension);

        if (dataMap == null || dataMap.isEmpty()) return;

        new Thread(() -> {
            boolean changed = false;

            synchronized (CONTAINER_ENTRIES_MAP) {
                Iterator<Map.Entry<BlockPos, ContainerSnapshot>> iterator = dataMap.entrySet().iterator();

                while (iterator.hasNext()) {
                    Map.Entry<BlockPos, ContainerSnapshot> entry = iterator.next();
                    BlockPos pos = entry.getKey();

                    if (world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
                        var state = world.getBlockState(pos);
                        if (!Util.isValidSearchableContainer(state)) {
                            iterator.remove();
                            changed = true;
                        }
                    }
                }

                if (changed) {
                    this.isDirty = true;
                    rebuildIndex();
                }
            }

            if (changed) {
                saveIfDirty();
            }
        }, "ChestFinder-Cleanup").start();
    }

    public void update(String dimension, BlockPos pos, String blockName, int capacity, List<ItemStack> stacks) {
        List<ItemStack> copiedStacks = new ArrayList<>();
        for (ItemStack original : stacks) {
            if (original != null && !original.isEmpty()) {
                copiedStacks.add(original.copy());
            }
        }

        ContainerSnapshot snapshot = new ContainerSnapshot(blockName, capacity, copiedStacks, System.currentTimeMillis());

        synchronized (CONTAINER_ENTRIES_MAP) {
            CONTAINER_ENTRIES_MAP.computeIfAbsent(dimension, k -> new HashMap<>()).put(pos, snapshot);
            this.isDirty = true;
            rebuildIndex();
        }
    }

    public void remove(String dimension, BlockPos pos) {
        synchronized (CONTAINER_ENTRIES_MAP) {
            Map<BlockPos, ContainerSnapshot> dimMap = CONTAINER_ENTRIES_MAP.get(dimension);
            if (dimMap != null && dimMap.remove(pos) != null) {
                this.isDirty = true;
                rebuildIndex();
            }
        }
    }

    public void saveIfDirty() {
        if (this.isDirty) {
            synchronized (CONTAINER_ENTRIES_MAP) {
                serializer.write(CONTAINER_ENTRIES_MAP);
                this.isDirty = false;
            }
        }
    }

    public void rebuildIndex() {
        synchronized (CONTAINER_ENTRIES_MAP) {
            SEARCH_INDEX.clear();

            for (var dimEntry : CONTAINER_ENTRIES_MAP.entrySet()) {
                String dimension = dimEntry.getKey();
                for (var posEntry : dimEntry.getValue().entrySet()) {
                    BlockPos pos = posEntry.getKey();
                    ContainerSnapshot snapshot = posEntry.getValue();

                    Map<StackKey, ItemStack> localMap = new LinkedHashMap<>();
                    for (ItemStack stack : snapshot.items()) {
                        if (stack == null || stack.isEmpty()) continue;
                        StackKey key = new StackKey(stack);
                        if (localMap.containsKey(key)) {
                            localMap.get(key).increment(stack.getCount());
                        } else {
                            localMap.put(key, stack.copy());
                        }
                    }

                    for (ItemStack summedStack : localMap.values()) {
                        SEARCH_INDEX.add(new IndexedItem(
                                summedStack,
                                pos,
                                dimension,
                                snapshot.containerName(),
                                snapshot.containerCapacity(),
                                snapshot.timestamp()
                        ));
                    }
                }
            }
        }
    }

    public Map<String, Map<BlockPos, ContainerSnapshot>> getContainerEntriesMap() {
        return CONTAINER_ENTRIES_MAP;
    }

    /**
     * Now Thread-Safe for the SearchScreen to use!
     */
    public List<IndexedItem> getSearchIndex() {
        synchronized (CONTAINER_ENTRIES_MAP) {
            return new ArrayList<>(SEARCH_INDEX);
        }
    }
}