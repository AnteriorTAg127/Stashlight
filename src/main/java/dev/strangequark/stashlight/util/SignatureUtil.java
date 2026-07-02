package dev.strangequark.stashlight.util;

import dev.strangequark.stashlight.model.SlotStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Cross-side container signature computation.
 *
 * <p>The server reads live {@link Container} contents, the client reads a frozen
 * {@code ContainerSnapshot}; both must produce identical signatures for the same
 * logical contents. Keeping both formulas in one file prevents drift.</p>
 *
 * <p>Hash inputs are registry IDs and component maps (content-based), never JVM
 * identity — so the same container produces the same signature on both sides.</p>
 */
public final class SignatureUtil {

    private SignatureUtil() {
    }

    /**
     * Server-side signature from a live container. Does not call {@code stack.copy()}.
     */
    public static long computeSignature(Container container) {
        int size = container.getContainerSize();
        long sig = size;
        for (int i = 0; i < size; i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            sig = sig * 31L + (i + 1);
            sig = sig * 7L + stack.getCount();
            sig = sig * 13L + itemKeyHash(stack);
            sig = sig * 17L + stack.getComponents().hashCode();
        }
        return sig;
    }

    /**
     * Client-side signature from a snapshot. Must produce the same value as
     * {@link #computeSignature(Container)} for equivalent contents.
     */
    public static long computeSignature(List<SlotStack> slots, int capacity) {
        long sig = capacity;
        for (SlotStack ss : slots) {
            ItemStack stack = ss.stack();
            if (stack == null || stack.isEmpty()) continue;
            sig = sig * 31L + (ss.slot() + 1);
            sig = sig * 7L + stack.getCount();
            sig = sig * 13L + itemKeyHash(stack);
            sig = sig * 17L + stack.getComponents().hashCode();
        }
        return sig;
    }

    private static int itemKeyHash(ItemStack stack) {
        // Registry ID is identical across client/server JVMs — do NOT use identityHashCode.
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).hashCode();
    }
}
