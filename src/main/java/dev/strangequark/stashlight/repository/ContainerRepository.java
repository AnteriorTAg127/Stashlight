package dev.strangequark.stashlight.repository;

import dev.strangequark.stashlight.Stashlight;
import dev.strangequark.stashlight.model.ContainerSnapshot;
import dev.strangequark.stashlight.model.DataSourceMode;
import dev.strangequark.stashlight.model.EnchantEntry;
import dev.strangequark.stashlight.model.IndexedItem;
import dev.strangequark.stashlight.model.LocatePath;
import dev.strangequark.stashlight.model.SlotStack;
import dev.strangequark.stashlight.model.Source;
import dev.strangequark.stashlight.serializer.Serializer;
import dev.strangequark.stashlight.util.EnchantmentExtractor;
import dev.strangequark.stashlight.util.NestedContainerExpander;
import dev.strangequark.stashlight.util.SignatureUtil;
import dev.strangequark.stashlight.util.Util;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ContainerRepository {

    private final Serializer localSerializer;
    private final Serializer serverSerializer;

    private volatile boolean localDirty = false;
    private volatile boolean serverDirty = false;

    private final Map<String, Map<BlockPos, ContainerSnapshot>> LOCAL_MAP;
    private final Map<String, Map<BlockPos, ContainerSnapshot>> SERVER_MAP;

    private List<IndexedItem> SEARCH_INDEX = new ArrayList<>();

    private final Map<String, Map<BlockPos, List<IndexedItem>>> LOCAL_LOOKUP = new HashMap<>();
    private final Map<String, Map<BlockPos, List<IndexedItem>>> SERVER_LOOKUP = new HashMap<>();

    // Enchantment id -> indexed items that carry this enchantment.
    private final Map<ResourceLocation, List<IndexedItem>> ENCHANTMENT_INDEX = new HashMap<>();

    private final ExecutorService cleanupExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Stashlight-Cleanup");
        t.setDaemon(true);
        return t;
    });

    // Prevents submitting duplicate save tasks when saveIfDirty() is called
    // rapidly (e.g. every 3000 ticks) before a previous save has finished.
    private volatile boolean isSavePending = false;

    public ContainerRepository(Serializer localSerializer, @Nullable Serializer serverSerializer) {
        this.localSerializer = localSerializer;
        this.serverSerializer = serverSerializer;
        this.LOCAL_MAP = localSerializer != null ? localSerializer.read() : new HashMap<>();
        this.SERVER_MAP = serverSerializer != null ? serverSerializer.read() : new HashMap<>();
        rebuildIndex();
    }

    public void runCleanup(ClientLevel world) {
        String dimension = Util.getDimensionName(world);
        Map<BlockPos, ContainerSnapshot> dataMap = LOCAL_MAP.get(dimension);

        if (dataMap == null || dataMap.isEmpty()) return;

        cleanupExecutor.submit(() -> {
            List<BlockPos> toCheck;
            synchronized (lock()) {
                toCheck = new ArrayList<>(dataMap.keySet());
            }

            List<BlockPos> toRemove = new ArrayList<>();
            for (BlockPos pos : toCheck) {
                try {
                    if (world.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                        var state = world.getBlockState(pos);
                        if (!Util.isValidSearchableContainer(state)) {
                            toRemove.add(pos);
                        }
                    }
                } catch (Exception e) {
                    // Skip this position if world access fails
                }
            }

            if (!toRemove.isEmpty()) {
                synchronized (lock()) {
                    for (BlockPos pos : toRemove) {
                        dataMap.remove(pos);
                        removeFromIndex(dimension, pos, LOCAL_LOOKUP);
                    }
                    this.localDirty = true;
                }
                saveIfDirty();
            }
        });
    }

    public void update(String dimension, BlockPos pos, String blockName, int capacity, List<SlotStack> slotStacks) {
        update(dimension, pos, blockName, capacity, slotStacks, System.currentTimeMillis(), Source.LOCAL_OPEN, LOCAL_MAP, LOCAL_LOOKUP);
        this.localDirty = true;
    }

    public void updateServer(String dimension, BlockPos pos, String blockName, int capacity, List<SlotStack> slotStacks, long timestamp) {
        update(dimension, pos, blockName, capacity, slotStacks, timestamp, Source.SERVER_PUSH, SERVER_MAP, SERVER_LOOKUP);
        this.serverDirty = true;
    }

    private void update(String dimension, BlockPos pos, String blockName, int capacity, List<SlotStack> slotStacks,
                        long timestamp, Source source,
                        Map<String, Map<BlockPos, ContainerSnapshot>> map,
                        Map<String, Map<BlockPos, List<IndexedItem>>> lookup) {
        List<SlotStack> copiedStacks = new ArrayList<>();
        for (SlotStack original : slotStacks) {
            if (original.stack() != null && !original.stack().isEmpty()) {
                copiedStacks.add(new SlotStack(original.slot(), original.stack().copy()));
            }
        }

        ContainerSnapshot snapshot = new ContainerSnapshot(blockName, capacity, copiedStacks, timestamp);

        synchronized (lock()) {
            map.computeIfAbsent(dimension, k -> new HashMap<>()).put(pos, snapshot);
            removeFromIndex(dimension, pos, lookup);
            addToIndex(dimension, pos, snapshot, source, lookup);
        }
    }

    public void remove(String dimension, BlockPos pos) {
        synchronized (lock()) {
            Map<BlockPos, ContainerSnapshot> dimMap = LOCAL_MAP.get(dimension);
            if (dimMap != null && dimMap.remove(pos) != null) {
                this.localDirty = true;
                removeFromIndex(dimension, pos, LOCAL_LOOKUP);
            }
        }
    }

    /**
     * Remove a server-cached container (used when the server signals it no longer
     * exists). Mirrors {@link #remove} but operates on {@code SERVER_MAP} /
     * {@code SERVER_LOOKUP} and marks {@code serverDirty}.
     */
    public void removeServer(String dimension, BlockPos pos) {
        synchronized (lock()) {
            Map<BlockPos, ContainerSnapshot> dimMap = SERVER_MAP.get(dimension);
            if (dimMap != null && dimMap.remove(pos) != null) {
                this.serverDirty = true;
                removeFromIndex(dimension, pos, SERVER_LOOKUP);
            }
        }
    }

    public void clearServerData() {
        synchronized (lock()) {
            if (SERVER_MAP.isEmpty()) return;
            for (String dimension : SERVER_MAP.keySet()) {
                Map<BlockPos, List<IndexedItem>> dimLookup = SERVER_LOOKUP.get(dimension);
                if (dimLookup != null) {
                    for (BlockPos pos : new ArrayList<>(dimLookup.keySet())) {
                        removeFromIndex(dimension, pos, SERVER_LOOKUP);
                    }
                }
            }
            SERVER_MAP.clear();
            SERVER_LOOKUP.clear();
            this.serverDirty = true;
        }
        saveIfDirty();
    }

    /**
     * Compute a per-dimension, per-position signature view of the cached
     * {@code SERVER_MAP}. Used by the client to populate {@code ClientReadyPayload}
     * so the server can prime its signature store and answer with an incremental
     * (rather than full) join push.
     */
    public Map<String, Map<BlockPos, Long>> computeServerSignatures() {
        Map<String, Map<BlockPos, Long>> result = new HashMap<>();
        synchronized (lock()) {
            for (var dimEntry : SERVER_MAP.entrySet()) {
                Map<BlockPos, Long> dimSigs = new HashMap<>();
                for (var posEntry : dimEntry.getValue().entrySet()) {
                    ContainerSnapshot snap = posEntry.getValue();
                    long sig = SignatureUtil.computeSignature(snap.slotStacks(), snap.containerCapacity());
                    dimSigs.put(posEntry.getKey(), sig);
                }
                result.put(dimEntry.getKey(), dimSigs);
            }
        }
        return result;
    }

    private void removeFromIndex(String dimension, BlockPos pos, Map<String, Map<BlockPos, List<IndexedItem>>> lookup) {
        Map<BlockPos, List<IndexedItem>> dimLookup = lookup.get(dimension);
        if (dimLookup != null) {
            List<IndexedItem> oldItems = dimLookup.remove(pos);

            if (oldItems != null && !oldItems.isEmpty()) {
                Set<IndexedItem> itemsToRemove = new HashSet<>(oldItems);

                List<IndexedItem> newIndex = new ArrayList<>(SEARCH_INDEX.size());
                for (IndexedItem item : SEARCH_INDEX) {
                    if (!itemsToRemove.contains(item)) {
                        newIndex.add(item);
                    } else {
                        removeFromEnchantmentIndex(item);
                    }
                }
                SEARCH_INDEX = newIndex;
            }
        }
    }

    private void addToIndex(String dimension, BlockPos pos, ContainerSnapshot snapshot, Source source,
                            Map<String, Map<BlockPos, List<IndexedItem>>> lookup) {
        List<IndexedItem> newItems = new ArrayList<>();

        for (SlotStack slotStack : snapshot.slotStacks()) {
            if (slotStack.stack() == null || slotStack.stack().isEmpty()) continue;

            for (NestedContainerExpander.SlotPath slotPath : NestedContainerExpander.expand(slotStack, 3)) {
                ItemStack stack = slotPath.slotStack().stack();
                LocatePath path = slotPath.path();

                IndexedItem indexedItem = new IndexedItem(
                        stack,
                        pos,
                        dimension,
                        snapshot.containerName(),
                        snapshot.containerCapacity(),
                        snapshot.timestamp(),
                        stack.getHoverName().getString().toLowerCase(),
                        source,
                        path,
                        EnchantmentExtractor.extract(stack)
                );
                newItems.add(indexedItem);
                SEARCH_INDEX.add(indexedItem);
                addToEnchantmentIndex(indexedItem);
            }
        }

        lookup.computeIfAbsent(dimension, k -> new HashMap<>()).put(pos, newItems);
    }

    private void addToEnchantmentIndex(IndexedItem item) {
        for (EnchantEntry enchant : item.enchantments()) {
            ENCHANTMENT_INDEX.computeIfAbsent(enchant.id(), k -> new ArrayList<>()).add(item);
        }
    }

    private void removeFromEnchantmentIndex(IndexedItem item) {
        for (EnchantEntry enchant : item.enchantments()) {
            List<IndexedItem> list = ENCHANTMENT_INDEX.get(enchant.id());
            if (list != null) {
                list.remove(item);
                if (list.isEmpty()) {
                    ENCHANTMENT_INDEX.remove(enchant.id());
                }
            }
        }
    }

    public void saveIfDirty() {
        if ((!localDirty && !serverDirty) || isSavePending) return;
        isSavePending = true;
        cleanupExecutor.submit(() -> {
            try {
                synchronized (lock()) {
                    if (localDirty && localSerializer != null) {
                        localSerializer.write(LOCAL_MAP);
                        localDirty = false;
                    }
                    if (serverDirty && serverSerializer != null) {
                        serverSerializer.write(SERVER_MAP);
                        serverDirty = false;
                    }
                }
            } finally {
                isSavePending = false;
            }
        });
    }

    public void rebuildIndex() {
        synchronized (lock()) {
            SEARCH_INDEX = new ArrayList<>();
            LOCAL_LOOKUP.clear();
            SERVER_LOOKUP.clear();
            ENCHANTMENT_INDEX.clear();

            for (var dimEntry : LOCAL_MAP.entrySet()) {
                String dimension = dimEntry.getKey();
                for (var posEntry : dimEntry.getValue().entrySet()) {
                    addToIndex(dimension, posEntry.getKey(), posEntry.getValue(), Source.LOCAL_OPEN, LOCAL_LOOKUP);
                }
            }

            for (var dimEntry : SERVER_MAP.entrySet()) {
                String dimension = dimEntry.getKey();
                for (var posEntry : dimEntry.getValue().entrySet()) {
                    addToIndex(dimension, posEntry.getKey(), posEntry.getValue(), Source.SERVER_PUSH, SERVER_LOOKUP);
                }
            }
        }
    }

    public Set<String> getDimensions() {
        synchronized (lock()) {
            Set<String> dims = new HashSet<>(LOCAL_MAP.keySet());
            dims.addAll(SERVER_MAP.keySet());
            return dims;
        }
    }

    public List<IndexedItem> getSearchIndex(DataSourceMode mode) {
        synchronized (lock()) {
            if (mode == DataSourceMode.MERGED) {
                return new ArrayList<>(SEARCH_INDEX);
            }
            Source wanted = mode == DataSourceMode.SERVER ? Source.SERVER_PUSH : Source.LOCAL_OPEN;
            return SEARCH_INDEX.stream()
                    .filter(item -> item.source() == wanted)
                    .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        }
    }

    public Map<ResourceLocation, List<IndexedItem>> getEnchantmentIndex(DataSourceMode mode) {
        synchronized (lock()) {
            Source wanted = mode == DataSourceMode.MERGED ? null
                    : (mode == DataSourceMode.SERVER ? Source.SERVER_PUSH : Source.LOCAL_OPEN);
            Map<ResourceLocation, List<IndexedItem>> copy = new HashMap<>();
            ENCHANTMENT_INDEX.forEach((id, items) -> {
                List<IndexedItem> filtered = new ArrayList<>();
                for (IndexedItem item : items) {
                    if (wanted == null || item.source() == wanted) {
                        filtered.add(item);
                    }
                }
                if (!filtered.isEmpty()) {
                    copy.put(id, filtered);
                }
            });
            return copy;
        }
    }

    public void shutdown() {
        saveIfDirty();
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                Stashlight.LOGGER.warn("Stashlight cleanup executor did not finish in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Object lock() {
        return LOCAL_MAP;
    }
}
