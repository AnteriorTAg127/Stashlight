package dev.strangequark.stashlight.server;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntSupplier;

/**
 * Per-player rate limiter combining a minimum request interval with a token bucket.
 * All timing/sizing parameters are injected as {@link IntSupplier}s so the same
 * class can be reused for scan and take rate limiting with different config sources.
 */
public final class RateLimiter {

    private final Map<UUID, PlayerState> states = new HashMap<>();
    private final IntSupplier minInterval;
    private final IntSupplier bucketCap;
    private final IntSupplier refill;

    public RateLimiter(IntSupplier minInterval, IntSupplier bucketCap, IntSupplier refill) {
        this.minInterval = minInterval;
        this.bucketCap = bucketCap;
        this.refill = refill;
    }

    public RateLimitResult allowRequest(ServerPlayer player, int serverTickCount) {
        PlayerState state = states.computeIfAbsent(player.getUUID(), u -> new PlayerState(serverTickCount));

        int bucketCapacity = bucketCap.getAsInt();
        int refillTicks = refill.getAsInt();

        // Refill tokens
        int ticksSinceLastRefill = serverTickCount - state.lastRefillTick;
        if (ticksSinceLastRefill >= refillTicks && state.tokens < bucketCapacity) {
            int refills = ticksSinceLastRefill / refillTicks;
            state.tokens = Math.min(bucketCapacity, state.tokens + refills);
            state.lastRefillTick += refills * refillTicks;
        }

        // Minimum interval
        if (serverTickCount - state.lastRequestTick < minInterval.getAsInt()) {
            return RateLimitResult.DENY_RATE_LIMITED;
        }

        if (state.tokens <= 0) {
            return RateLimitResult.DENY_RATE_LIMITED;
        }

        state.tokens--;
        state.lastRequestTick = serverTickCount;
        return RateLimitResult.ALLOW;
    }

    private final class PlayerState {
        int tokens;
        int lastRefillTick;
        int lastRequestTick;

        PlayerState(int serverTickCount) {
            this.tokens = bucketCap.getAsInt();
            this.lastRefillTick = serverTickCount;
            // 0 is safely below any real tick count without risking the integer
            // overflow that -Integer.MAX_VALUE causes (serverTickCount - (-MAX_VALUE)
            // wraps negative and falsely trips the min-interval guard).
            this.lastRequestTick = 0;
        }
    }

    public enum RateLimitResult {
        ALLOW,
        DENY_RATE_LIMITED
    }
}
