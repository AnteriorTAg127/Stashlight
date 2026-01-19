package strangequark.chestfinder.repository;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import strangequark.chestfinder.model.StackKey;

import java.util.*;

public class ContainerRepository {
    private static final Map<String, Map<BlockPos, List<ItemStack>>> CONTAINERS = new HashMap<>();

    public void update(String dimension, BlockPos pos, List<ItemStack> stacks) {
        List<ItemStack> copiedStacks = new ArrayList<>();

        for (ItemStack original : stacks) {
            if (original != null && !original.isEmpty()) {
                copiedStacks.add(original.copy());
            }
        }

        CONTAINERS.computeIfAbsent(dimension, k -> new HashMap<>()).put(pos, copiedStacks);
    }

    public void remove(String dimension, BlockPos pos) {
        if (CONTAINERS.containsKey(dimension)) {
            CONTAINERS.get(dimension).remove(pos);
        }
    }

    public List<ItemStack> getAllStacks() {
        List<ItemStack> flatList = new ArrayList<>();

        for (Map<BlockPos, List<ItemStack>> posMap : CONTAINERS.values()) {
            for (List<ItemStack> stacks : posMap.values()) {
                for (ItemStack stack : stacks) {
                    if (stack != null && !stack.isEmpty()) {
                        flatList.add(stack);
                    }
                }
            }
        }
        return flatList;
    }

    public List<ItemStack> getSummary() {
        List<ItemStack> result = new ArrayList<>();

        for (Map<BlockPos, List<ItemStack>> posMap : CONTAINERS.values()) {
            for (List<ItemStack> chestContents : posMap.values()) {
                Map<StackKey, ItemStack> localMap = new LinkedHashMap<>();

                for (ItemStack stack : chestContents) {
                    if (stack == null || stack.isEmpty()) continue;

                    StackKey key = new StackKey(stack);
                    if (localMap.containsKey(key)) {
                        localMap.get(key).increment(stack.getCount());
                    } else {
                        localMap.put(key, stack.copy());
                    }
                }
                result.addAll(localMap.values());
            }
        }
        return result;
    }
}
