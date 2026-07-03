package dev.strangequark.stashlight.server;

import dev.strangequark.stashlight.config.ServerConfig;
import dev.strangequark.stashlight.net.ContainerRemovedEntry;
import dev.strangequark.stashlight.net.ContainerSnapshotPayload;
import dev.strangequark.stashlight.util.SignatureUtil;
import dev.strangequark.stashlight.util.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side scanner that reads container inventories around a player and
 * returns them as network payloads.
 */
public final class ServerScanner {

    private ServerScanner() {
    }

    public static ScanResult scanAround(ServerPlayer player) {
        var cfg = ServerConfig.get();
        if (!cfg.enabled()) return ScanResult.empty();

        ServerLevel level = (ServerLevel) player.level();
        String dimension = Util.getDimensionName(level);
        BlockPos center = player.blockPosition();

        int radius = cfg.scan().maxRadius();
        if (!cfg.scan().radiusInBlocks()) radius *= 16;

        int minX = center.getX() - radius;
        int maxX = center.getX() + radius;
        int minY = Math.max(-64, center.getY() - radius);
        int maxY = Math.min(320, center.getY() + radius);
        int minZ = center.getZ() - radius;
        int maxZ = center.getZ() + radius;

        int maxContainers = cfg.scan().maxContainersPerRequest();
        int maxPerTick = cfg.scan().containersPerTick();
        long deadline = System.currentTimeMillis() + cfg.scan().maxMillisPerTick();

        List<ContainerSnapshotPayload> containers = new ArrayList<>();
        boolean truncated = false;
        int processed = 0;

        outer:
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) continue;

                for (var entry : chunk.getBlockEntities().entrySet()) {
                    if (processed >= maxPerTick || System.currentTimeMillis() > deadline) {
                        truncated = true;
                        break outer;
                    }

                    BlockPos pos = entry.getKey();
                    if (pos.getX() < minX || pos.getX() > maxX
                            || pos.getY() < minY || pos.getY() > maxY
                            || pos.getZ() < minZ || pos.getZ() > maxZ) {
                        continue;
                    }

                    BlockState state = level.getBlockState(pos);
                    if (!Util.isValidSearchableContainer(state)) continue;

                    BlockPos canonical = Util.getCanonicalPos(level, pos);
                    if (!canonical.equals(pos)) continue;

                    if (containers.size() >= maxContainers) {
                        truncated = true;
                        break outer;
                    }

                    ContainerSnapshotPayload payload = readContainer(level, dimension, pos, state);
                    if (payload != null) {
                        containers.add(payload);
                    }
                    processed++;
                }
            }
        }

        return new ScanResult(containers, truncated);
    }

    private static ContainerSnapshotPayload readContainer(ServerLevel level, String dimension, BlockPos pos, BlockState state) {
        Container container = getContainer(level, pos, state);
        if (container == null) return null;
        return readContainer(level, dimension, pos, state, container);
    }

    private static ContainerSnapshotPayload readContainer(ServerLevel level, String dimension, BlockPos pos, BlockState state, Container container) {
        int capacity = container.getContainerSize();

        List<ContainerSnapshotPayload.SlotStackSnapshot> slots = new ArrayList<>();
        for (int i = 0; i < capacity; i++) {
            ItemStack stack = container.getItem(i);
            if (stack != null && !stack.isEmpty()) {
                slots.add(new ContainerSnapshotPayload.SlotStackSnapshot(i, stack.copy()));
            }
        }

        return new ContainerSnapshotPayload(
                dimension,
                pos,
                state.getBlock().getName().getString(),
                capacity,
                slots
        );
    }

    /**
     * Get the {@link Container} at the given position, or null if none exists.
     * Made public for v1.3 take-item handling.
     */
    public static Container getContainer(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof ChestBlock chest) {
            var inv = ChestBlock.getContainer(chest, state, level, pos, true);
            if (inv instanceof Container c) return c;
            return null;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof Container c) return c;
        return null;
    }

    /**
     * Incremental scan: compute signatures for all containers around the player,
     * compare against the stored signatures, and return only changed/new containers
     * as {@code updated} plus any positions in loaded chunks that no longer have a
     * container as {@code removed}.
     *
     * @param forceFull when true, push every current container as {@code updated}
     *                  and reset the signature store (F8 manual refresh)
     */
    public static IncrementalScanResult incrementalScanAround(
            ServerPlayer player, ContainerSignatureStore store, boolean forceFull) {
        var cfg = ServerConfig.get();
        if (!cfg.enabled()) return IncrementalScanResult.empty();

        ServerLevel level = (ServerLevel) player.level();
        String dimension = Util.getDimensionName(level);
        BlockPos center = player.blockPosition();
        UUID uuid = player.getUUID();

        int radius = cfg.scan().maxRadius();
        if (!cfg.scan().radiusInBlocks()) radius *= 16;

        int minX = center.getX() - radius;
        int maxX = center.getX() + radius;
        int minY = Math.max(-64, center.getY() - radius);
        int maxY = Math.min(320, center.getY() + radius);
        int minZ = center.getZ() - radius;
        int maxZ = center.getZ() + radius;

        int maxContainers = cfg.scan().maxContainersPerRequest();
        int maxPerTick = cfg.scan().containersPerTick();
        long deadline = System.currentTimeMillis() + cfg.scan().maxMillisPerTick();

        List<ContainerSnapshotPayload> updated = new ArrayList<>();
        Set<BlockPos> scannedPositions = new HashSet<>();
        boolean truncated = false;
        int processed = 0;

        outer:
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) continue;

                for (var entry : chunk.getBlockEntities().entrySet()) {
                    if (processed >= maxPerTick || System.currentTimeMillis() > deadline) {
                        truncated = true;
                        break outer;
                    }

                    BlockPos pos = entry.getKey();
                    if (pos.getX() < minX || pos.getX() > maxX
                            || pos.getY() < minY || pos.getY() > maxY
                            || pos.getZ() < minZ || pos.getZ() > maxZ) {
                        continue;
                    }

                    BlockState state = level.getBlockState(pos);
                    if (!Util.isValidSearchableContainer(state)) continue;

                    BlockPos canonical = Util.getCanonicalPos(level, pos);
                    if (!canonical.equals(pos)) continue;

                    if (updated.size() >= maxContainers) {
                        truncated = true;
                        break outer;
                    }

                    Container container = getContainer(level, canonical, state);
                    if (container == null) continue;

                    long currentSig = SignatureUtil.computeSignature(container);
                    scannedPositions.add(canonical);

                    boolean push;
                    if (forceFull) {
                        push = true;
                    } else {
                        Long lastSig = store.get(uuid, dimension, canonical);
                        push = lastSig == null || lastSig != currentSig;
                    }

                    if (push) {
                        ContainerSnapshotPayload payload = readContainer(level, dimension, canonical, state, container);
                        if (payload != null) updated.add(payload);
                    }
                    store.put(uuid, dimension, canonical, currentSig);
                    processed++;
                }
            }
        }

        // Removal detection: positions in the store (scan range, loaded chunk) that
        // weren't visited this scan. Containers in unloaded chunks are left alone.
        List<ContainerRemovedEntry> removed = new ArrayList<>();
        List<BlockPos> toRemoveFromStore = new ArrayList<>();
        for (BlockPos storedPos : store.getPositions(uuid, dimension)) {
            if (scannedPositions.contains(storedPos)) continue;
            if (storedPos.getX() < minX || storedPos.getX() > maxX
                    || storedPos.getY() < minY || storedPos.getY() > maxY
                    || storedPos.getZ() < minZ || storedPos.getZ() > maxZ) {
                continue;
            }
            if (level.getChunkSource().hasChunk(storedPos.getX() >> 4, storedPos.getZ() >> 4)) {
                removed.add(new ContainerRemovedEntry(dimension, storedPos));
                toRemoveFromStore.add(storedPos);
            }
        }
        for (BlockPos pos : toRemoveFromStore) {
            store.remove(uuid, dimension, pos);
        }

        return new IncrementalScanResult(updated, removed, truncated);
    }

    public record ScanResult(List<ContainerSnapshotPayload> containers, boolean truncated) {
        public static ScanResult empty() {
            return new ScanResult(List.of(), false);
        }
    }

    public record IncrementalScanResult(
            List<ContainerSnapshotPayload> updated,
            List<ContainerRemovedEntry> removed,
            boolean truncated
    ) {
        public static IncrementalScanResult empty() {
            return new IncrementalScanResult(List.of(), List.of(), false);
        }
    }
}
