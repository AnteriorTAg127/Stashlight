package dev.strangequark.stashlight.util;

import dev.strangequark.stashlight.model.LocatePath;
import dev.strangequark.stashlight.model.SlotStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Flattens nested containers (shulker boxes, bundles, etc.) into a list of
 * slot stacks together with their locate path.
 */
public final class NestedContainerExpander {
    private NestedContainerExpander() {
    }

    /**
     * Expands a top-level slot stack. The returned list always contains the
     * original stack first, followed by any items found inside nested containers.
     */
    public static List<SlotPath> expand(SlotStack top, int maxDepth) {
        List<SlotPath> result = new ArrayList<>();
        result.add(new SlotPath(top, new LocatePath(top.slot())));
        expand(top.stack(), new LocatePath(top.slot()), 1, maxDepth, result);
        return result;
    }

    private static void expand(ItemStack stack, LocatePath path, int depth, int maxDepth, List<SlotPath> result) {
        if (depth > maxDepth || stack == null || stack.isEmpty()) return;

        var container = stack.get(DataComponents.CONTAINER);
        if (container != null) {
            int slot = 0;
            for (ItemStack inner : container.nonEmptyItems()) {
                LocatePath innerPath = path.withNestedSlot(slot);
                result.add(new SlotPath(new SlotStack(slot, inner), innerPath));
                expand(inner, innerPath, depth + 1, maxDepth, result);
                slot++;
            }
        }

        var bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) {
            int slot = 0;
            for (ItemStack inner : bundle.items()) {
                if (inner.isEmpty()) {
                    slot++;
                    continue;
                }
                LocatePath innerPath = path.withNestedSlot(slot);
                result.add(new SlotPath(new SlotStack(slot, inner), innerPath));
                expand(inner, innerPath, depth + 1, maxDepth, result);
                slot++;
            }
        }
    }

    /**
     * A slot stack together with its locate path inside the outer container.
     */
    public record SlotPath(SlotStack slotStack, LocatePath path) {
    }
}
