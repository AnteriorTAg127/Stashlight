package dev.strangequark.stashlight.scan;

import dev.strangequark.stashlight.Stashlight;
import dev.strangequark.stashlight.config.Config;
import dev.strangequark.stashlight.net.ScanRequestPayload;
import dev.strangequark.stashlight.util.Util;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Modded-mode proximity scanner. Runs every client tick when the player is near
 * enough searchable containers, sending periodic {@code scan_request} payloads
 * to keep the {@code SERVER_MAP} fresh.
 * <p>
 * Only counts container block-entity positions (does <em>not</em> read inventory
 * content), so it works on multiplayer servers where the client-side inventory
 * of BlockEntities is empty.
 */
public final class ProximityScanner {

    private final AtomicInteger nonceGenerator = new AtomicInteger(1);

    private long lastScanMs = 0L;
    private long backoffUntil = 0L;
    private int consecutiveRateLimited = 0;

    private boolean scanning = false;

    public void tick(Minecraft client) {
        var cfg = Config.get().proximityScan();
        var stashlight = Stashlight.getInstance();
        if (stashlight == null) return;

        // Guard: only active when enabled AND modded scan is available
        if (!cfg.enabled() || !stashlight.isModdedScanAvailable()) {
            scanning = false;
            return;
        }

        if (client.level == null || client.player == null) {
            scanning = false;
            return;
        }

        long now = System.currentTimeMillis();

        // Backoff check (rate-limited response doubles the interval)
        if (now < backoffUntil) return;

        // Count searchable containers in range (positions only, no content read)
        int count = countSearchableContainersInRange(client.level, client.player.blockPosition(), cfg.radius());
        if (count < cfg.containerThreshold()) {
            scanning = false;
            return;
        }

        // Interval check
        if (now - lastScanMs < cfg.scanIntervalSeconds() * 1000L) return;

        // Send scan request
        int nonce = nonceGenerator.getAndIncrement();
        ClientPlayNetworking.send(new ScanRequestPayload(cfg.radius(), "same", nonce));
        lastScanMs = now;
        scanning = true;
    }

    /**
     * Called when a {@code scan_error(RATE_LIMITED)} is received. Doubles the
     * backoff interval (capped at 30 seconds). The next successful response
     * should clear the backoff via {@link #clearBackoff()}.
     */
    public void onRateLimited() {
        long baseInterval = Config.get().proximityScan().scanIntervalSeconds() * 1000L;
        long backoff = Math.min(baseInterval * (1L << Math.min(consecutiveRateLimited, 4)), 30_000L);
        backoffUntil = System.currentTimeMillis() + backoff;
        consecutiveRateLimited++;
    }

    /**
     * Clear backoff after a successful scan response.
     */
    public void clearBackoff() {
        backoffUntil = 0L;
        consecutiveRateLimited = 0;
    }

    /**
     * Count searchable containers within {@code radius} blocks of the player.
     * Only checks positions (does not read inventory content), so this works
     * on any server type.
     */
    private static int countSearchableContainersInRange(ClientLevel level, BlockPos center, int radius) {
        int minX = center.getX() - radius;
        int maxX = center.getX() + radius;
        int minZ = center.getZ() - radius;
        int maxZ = center.getZ() + radius;

        int count = 0;
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) continue;
                if (chunk.isEmpty()) continue;

                for (var entry : chunk.getBlockEntities().entrySet()) {
                    BlockPos pos = entry.getKey();
                    if (pos.getX() < minX || pos.getX() > maxX
                            || pos.getZ() < minZ || pos.getZ() > maxZ) {
                        continue;
                    }
                    if (!level.isLoaded(pos)) continue;

                    BlockState state = level.getBlockState(pos);
                    if (Util.isValidSearchableContainer(state)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }
}
