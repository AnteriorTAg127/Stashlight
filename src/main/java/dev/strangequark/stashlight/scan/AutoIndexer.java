package dev.strangequark.stashlight.scan;

import dev.strangequark.stashlight.Stashlight;
import dev.strangequark.stashlight.config.Config;
import dev.strangequark.stashlight.model.SlotStack;
import dev.strangequark.stashlight.repository.ContainerRepository;
import dev.strangequark.stashlight.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side automatic container scanner. Periodically walks loaded chunks
 * around the player, reads searchable container inventories and writes them
 * into the repository as LOCAL_OPEN sources.
 */
public final class AutoIndexer {

    private final ContainerRepository repository;
    private final Map<BlockPos, Long> lastScanTimes = new HashMap<>();

    public AutoIndexer(ContainerRepository repository) {
        this.repository = repository;
    }

    public void tick(Minecraft client) {
        var cfg = Config.get().autoIndex();
        if (!cfg.enabled()) return;
        if (client.level == null || client.player == null) return;

        ClientLevel level = client.level;
        String dimension = Util.getDimensionName(level);
        BlockPos center = client.player.blockPosition();

        int radius = cfg.radius();
        if (!cfg.radiusInBlocks()) {
            radius *= 16;
        }

        int minX = center.getX() - radius;
        int maxX = center.getX() + radius;
        int minY = Math.max(-64, center.getY() - radius);
        int maxY = Math.min(320, center.getY() + radius);
        int minZ = center.getZ() - radius;
        int maxZ = center.getZ() + radius;

        List<BlockPos> candidates = new ArrayList<>();
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) continue;

                for (var entry : chunk.getBlockEntities().entrySet()) {
                    BlockPos pos = entry.getKey();
                    if (pos.getX() < minX || pos.getX() > maxX
                            || pos.getY() < minY || pos.getY() > maxY
                            || pos.getZ() < minZ || pos.getZ() > maxZ) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (Util.isValidSearchableContainer(state)) {
                        candidates.add(pos);
                    }
                }
            }
        }

        long now = System.currentTimeMillis();
        long ttlMillis = cfg.rescanTtlSeconds() * 1000L;
        int maxPerTick = cfg.containersPerTick();
        long deadline = now + cfg.maxMillisPerTick();

        int processed = 0;
        for (BlockPos pos : candidates) {
            if (processed >= maxPerTick) break;
            if (System.currentTimeMillis() > deadline) break;

            BlockPos canonical = Util.getCanonicalPos(level, pos);
            if (!canonical.equals(pos)) continue;

            Long lastScan = lastScanTimes.get(canonical);
            if (lastScan != null && now - lastScan < ttlMillis) continue;

            if (scanContainer(level, dimension, canonical)) {
                lastScanTimes.put(canonical, now);
                processed++;
            }
        }
    }

    private boolean scanContainer(ClientLevel level, String dimension, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!Util.isValidSearchableContainer(state)) return false;

        Container container = null;
        int capacity = 0;

        if (state.getBlock() instanceof ChestBlock chest) {
            var inv = ChestBlock.getContainer(chest, state, level, pos, true);
            if (inv instanceof Container c) {
                container = c;
                capacity = container.getContainerSize();
            }
        } else {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof Container c) {
                container = c;
                capacity = container.getContainerSize();
            }
        }

        if (container == null) return false;

        List<SlotStack> slotStacks = new ArrayList<>();
        for (int i = 0; i < capacity; i++) {
            var stack = container.getItem(i);
            if (stack != null && !stack.isEmpty()) {
                slotStacks.add(new SlotStack(i, stack.copy()));
            }
        }

        try {
            repository.update(
                    dimension,
                    pos,
                    state.getBlock().getName().getString(),
                    capacity,
                    slotStacks
            );
            return true;
        } catch (Exception e) {
            Stashlight.LOGGER.warn("Failed to auto-index container at {}: {}", pos, e.getMessage());
            return false;
        }
    }
}
