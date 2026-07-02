package dev.strangequark.stashlight.server;

import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-player container signature cache, keyed by UUID → dimension → BlockPos.
 *
 * <p>Accessed only from the server thread (player join, scan request, dimension
 * change, disconnect). No synchronization.</p>
 *
 * <p>Used to detect container changes between scans: the server compares the
 * current signature of a live container against the stored value. A mismatch
 * (or absence) means the container must be re-pushed; positions present in the
 * store but absent from a loaded-chunk scan are candidates for removal.</p>
 */
public final class ContainerSignatureStore {

    private final Map<UUID, Map<String, Map<BlockPos, Long>>> store = new HashMap<>();

    public Long get(UUID uuid, String dimension, BlockPos pos) {
        Map<String, Map<BlockPos, Long>> byDim = store.get(uuid);
        if (byDim == null) return null;
        Map<BlockPos, Long> byPos = byDim.get(dimension);
        if (byPos == null) return null;
        return byPos.get(pos);
    }

    public void put(UUID uuid, String dimension, BlockPos pos, long signature) {
        store.computeIfAbsent(uuid, k -> new HashMap<>())
                .computeIfAbsent(dimension, k -> new HashMap<>())
                .put(pos, signature);
    }

    public boolean has(UUID uuid, String dimension, BlockPos pos) {
        return get(uuid, dimension, pos) != null;
    }

    public void remove(UUID uuid, String dimension, BlockPos pos) {
        Map<String, Map<BlockPos, Long>> byDim = store.get(uuid);
        if (byDim == null) return;
        Map<BlockPos, Long> byPos = byDim.get(dimension);
        if (byPos == null) return;
        byPos.remove(pos);
        if (byPos.isEmpty()) byDim.remove(dimension);
        if (byDim.isEmpty()) store.remove(uuid);
    }

    /** All positions recorded for this player in this dimension. May be empty. */
    public Set<BlockPos> getPositions(UUID uuid, String dimension) {
        Map<String, Map<BlockPos, Long>> byDim = store.get(uuid);
        if (byDim == null) return Set.of();
        Map<BlockPos, Long> byPos = byDim.get(dimension);
        if (byPos == null) return Set.of();
        return byPos.keySet();
    }

    /** Forget all signatures for a player. Called on disconnect. */
    public void clear(UUID uuid) {
        store.remove(uuid);
    }

    /** Remove every signature for a player in one dimension (e.g. on dimension change). */
    public void clearDimension(UUID uuid, String dimension) {
        Map<String, Map<BlockPos, Long>> byDim = store.get(uuid);
        if (byDim == null) return;
        byDim.remove(dimension);
        if (byDim.isEmpty()) store.remove(uuid);
    }
}
