package strangequark.chestfinder.repository;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import strangequark.chestfinder.model.ContainerSnapshot;
import strangequark.chestfinder.model.IndexedItem;
import strangequark.chestfinder.model.StackKey;
import strangequark.chestfinder.serializer.Serializer;

import java.util.*;

public class ContainerRepository {

    private final Serializer serializer;
    private final RegistryWrapper.WrapperLookup lookup;

    // The Source of Truth (For NBT Serialization)
    private final Map<String, Map<BlockPos, ContainerSnapshot>> DATABASE;

    // The Flattened UI Index (Pre-computed for search performance)
    private final List<IndexedItem> SEARCH_INDEX = new ArrayList<>();

    public ContainerRepository(Serializer serializer, RegistryWrapper.WrapperLookup lookup) {
        this.serializer = serializer;
        this.lookup = lookup;
        DATABASE = serializer.read(lookup);
        rebuildIndex();
    }

    /**
     * Updates a container and triggers an index rebuild.
     */
    public void update(String dimension, BlockPos pos, String blockName, List<ItemStack> stacks) {
        List<ItemStack> copiedStacks = new ArrayList<>();
        for (ItemStack original : stacks) {
            if (original != null && !original.isEmpty()) {
                copiedStacks.add(original.copy());
            }
        }

        ContainerSnapshot snapshot = new ContainerSnapshot(blockName, System.currentTimeMillis(), copiedStacks);
        DATABASE.computeIfAbsent(dimension, k -> new HashMap<>()).put(pos, snapshot);

        serializer.write(DATABASE, this.lookup);
        rebuildIndex();
    }

    public void remove(String dimension, BlockPos pos) {
        if (DATABASE.containsKey(dimension)) {
            DATABASE.get(dimension).remove(pos);
            serializer.write(DATABASE, this.lookup);
            rebuildIndex();
        }
    }

    /**
     * Turns the nested DATABASE into a flat SEARCH_INDEX.
     * This moves the O(N) computation out of the UI render loop.
     */
    public void rebuildIndex() {
        SEARCH_INDEX.clear();

        for (var dimEntry : DATABASE.entrySet()) {
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
        return DATABASE;
    }

}