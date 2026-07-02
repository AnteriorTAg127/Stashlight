package dev.strangequark.stashlight.server;

import dev.strangequark.stashlight.config.ServerConfig;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player rate limiter combining a minimum request interval with a token bucket.
 */
public final class RateLimiter {

    private final Map<UUID, PlayerState> states = new HashMap<>();

    public RateLimitResult allowRequest(ServerPlayer player, int serverTickCount) {
        var cfg = ServerConfig.get().rateLimit();
        PlayerState state = states.computeIfAbsent(player.getUUID(), u -> new PlayerState(serverTickCount));

        // Refill tokens
        int ticksSinceLastRefill = serverTickCount - state.lastRefillTick;
        if (ticksSinceLastRefill >= cfg.refillTicks() && state.tokens < cfg.bucketCapacity()) {
            int refills = ticksSinceLastRefill / cfg.refillTicks();
            state.tokens = Math.min(cfg.bucketCapacity(), state.tokens + refills);
            state.lastRefillTick += refills * cfg.refillTicks();
        }

        // Minimum interval
        if (serverTickCount - state.lastRequestTick < cfg.minRequestIntervalTicks()) {
            return RateLimitResult.DENY_RATE_LIMITED;
        }

        if (state.tokens <= 0) {
            return RateLimitResult.DENY_RATE_LIMITED;
        }

        state.tokens--;
        state.lastRequestTick = serverTickCount;
        return RateLimitResult.ALLOW;
    }

    private static final class PlayerState {
        int tokens;
        int lastRefillTick;
        int lastRequestTick;

        PlayerState(int serverTickCount) {
            this.tokens = ServerConfig.get().rateLimit().bucketCapacity();
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
