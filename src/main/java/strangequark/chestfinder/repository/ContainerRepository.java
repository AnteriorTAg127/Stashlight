package strangequark.chestfinder.repository;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import strangequark.chestfinder.model.ContainerSnapshot;
import strangequark.chestfinder.model.IndexedItem;
import strangequark.chestfinder.model.StackKey;
import strangequark.chestfinder.serializer.Serializer;

import java.util.*;

public class ContainerRepository {

    private final Serializer serializer;

    // The Source of Truth (For NBT Serialization)
    private final Map<String, Map<BlockPos, ContainerSnapshot>> CONTAINER_ENTRIES_MAP;

    // The Flattened UI Index (Pre-computed for search performance)
    private final List<IndexedItem> SEARCH_INDEX = new ArrayList<>();

    public ContainerRepository(Serializer serializer) {
        this.serializer = serializer;
        CONTAINER_ENTRIES_MAP = serializer.read();
        rebuildIndex();
    }

    /**
     * Updates a container and triggers an index rebuild.
     */
    public void update(String dimension, BlockPos pos, String blockName, int capacity, List<ItemStack> stacks) {
        List<ItemStack> copiedStacks = new ArrayList<>();
        for (ItemStack original : stacks) {
            if (original != null && !original.isEmpty()) {
                copiedStacks.add(original.copy());
            }
        }

        ContainerSnapshot snapshot = new ContainerSnapshot(blockName, capacity, copiedStacks, System.currentTimeMillis());
        CONTAINER_ENTRIES_MAP.computeIfAbsent(dimension, k -> new HashMap<>()).put(pos, snapshot);

        serializer.write(CONTAINER_ENTRIES_MAP);
        rebuildIndex();
    }

    public void remove(String dimension, BlockPos pos) {
        if (CONTAINER_ENTRIES_MAP.containsKey(dimension)) {
            CONTAINER_ENTRIES_MAP.get(dimension).remove(pos);
            serializer.write(CONTAINER_ENTRIES_MAP);
            rebuildIndex();
        }
    }

    /**
     * Turns the nested DATABASE into a flat SEARCH_INDEX.
     * This moves the O(N) computation out of the UI render loop.
     */
    public void rebuildIndex() {
        SEARCH_INDEX.clear();

        for (var dimEntry : CONTAINER_ENTRIES_MAP.entrySet()) {
            String dimension = dimEntry.getKey();

            for (var posEntry : dimEntry.getValue().entrySet()) {
                BlockPos pos = posEntry.getKey();
                ContainerSnapshot snapshot = posEntry.getValue();

                // Group items within this specific container
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

                // Convert grouped ItemStacks into IndexedItem discoveries
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

    /**
     * Used by SearchScreenOwo to get the pre-computed items.
     */
    public List<IndexedItem> getSearchIndex() {
        return SEARCH_INDEX;
    }

    /**
     * Used by NbtPersistence to save the raw data.
     */
    public Map<String, Map<BlockPos, ContainerSnapshot>> getDatabase() {
        return CONTAINER_ENTRIES_MAP;
    }

}