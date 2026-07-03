package dev.strangequark.stashlight.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.strangequark.stashlight.Stashlight;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Server-side configuration for Stashlight. Loaded only on the logical server.
 */
public final class ServerConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("stashlight-server.json");

    private static ServerConfig INSTANCE;

    private boolean enabled = true;

    private ScanConfig scan = new ScanConfig();
    private RateLimitConfig rateLimit = new RateLimitConfig();
    private PushConfig push = new PushConfig();
    private PermissionConfig permission = new PermissionConfig();
    private LimitConfig limits = new LimitConfig();
    private TakeConfig take = new TakeConfig();

    public static ServerConfig get() {
        if (INSTANCE == null) load();
        return INSTANCE;
    }

    public static void reload() {
        load();
    }

    private static void load() {
        if (Files.exists(FILE)) {
            try {
                INSTANCE = GSON.fromJson(Files.readString(FILE), ServerConfig.class);
                INSTANCE.validate();
                return;
            } catch (Exception e) {
                Stashlight.LOGGER.error("Failed to load server config, using defaults", e);
            }
        }
        INSTANCE = new ServerConfig();
        save();
    }

    private void validate() {
        if (scan == null) scan = new ScanConfig();
        if (rateLimit == null) rateLimit = new RateLimitConfig();
        if (push == null) push = new PushConfig();
        if (permission == null) permission = new PermissionConfig();
        if (limits == null) limits = new LimitConfig();
        if (take == null) take = new TakeConfig();
    }

    public static void save() {
        try {
            Files.writeString(FILE, GSON.toJson(get()));
        } catch (IOException e) {
            Stashlight.LOGGER.error("Failed to save server config", e);
        }
    }

    public boolean enabled() { return enabled; }
    public ScanConfig scan() { return scan; }
    public RateLimitConfig rateLimit() { return rateLimit; }
    public PushConfig push() { return push; }
    public PermissionConfig permission() { return permission; }
    public LimitConfig limits() { return limits; }
    public TakeConfig take() { return take; }

    public static final class ScanConfig {
        private int maxRadius = 48;
        private String radiusUnit = "blocks";
        private int maxContainersPerRequest = 512;
        private int containersPerTick = 16;
        private int maxMillisPerTick = 5;
        private boolean expandNestedContainers = true;
        private int maxDepth = 3;
        private boolean sameDimensionOnly = true;

        public int maxRadius() { return clamp(maxRadius, 4, 256); }
        public boolean radiusInBlocks() { return !"chunks".equalsIgnoreCase(radiusUnit); }
        public int maxContainersPerRequest() { return clamp(maxContainersPerRequest, 16, 4096); }
        public int containersPerTick() { return clamp(containersPerTick, 1, 256); }
        public int maxMillisPerTick() { return clamp(maxMillisPerTick, 1, 50); }
        public boolean expandNestedContainers() { return expandNestedContainers; }
        public int maxDepth() { return clamp(maxDepth, 1, 8); }
        public boolean sameDimensionOnly() { return sameDimensionOnly; }
    }

    public static final class RateLimitConfig {
        private int minRequestIntervalTicks = 40;
        private int bucketCapacity = 3;
        private int refillTicks = 100;

        public int minRequestIntervalTicks() { return clamp(minRequestIntervalTicks, 1, 1200); }
        public int bucketCapacity() { return clamp(bucketCapacity, 1, 20); }
        public int refillTicks() { return clamp(refillTicks, 1, 1200); }
    }

    public static final class PushConfig {
        private boolean enabled = false;
        private int intervalTicks = 200;
        private int radius = 32;

        public boolean enabled() { return enabled; }
        public int intervalTicks() { return clamp(intervalTicks, 20, 1200); }
        public int radius() { return clamp(radius, 4, 256); }
    }

    public static final class PermissionConfig {
        private boolean requireOp = false;
        private String permissionNode = "";
        private boolean respectClaims = false;

        public boolean requireOp() { return requireOp; }
        public String permissionNode() { return permissionNode; }
        public boolean respectClaims() { return respectClaims; }
    }

    public static final class LimitConfig {
        private int maxResponseBytes = 1024 * 1024;

        public int maxResponseBytes() { return Math.max(maxResponseBytes, 4096); }
    }

    public static final class TakeConfig {
        private boolean enabled = false;
        private int maxRadius = 6;
        private int maxCountPerRequest = 64;
        private int minRequestIntervalTicks = 20;
        private int bucketCapacity = 16;
        private int refillTicks = 40;
        private boolean dropOnFullEnabled = true;

        public boolean enabled() { return enabled; }
        public int maxRadius() { return clamp(maxRadius, 4, 32); }
        public int maxCountPerRequest() { return clamp(maxCountPerRequest, 1, 4096); }
        public int minRequestIntervalTicks() { return clamp(minRequestIntervalTicks, 1, 1200); }
        public int bucketCapacity() { return clamp(bucketCapacity, 1, 64); }
        public int refillTicks() { return clamp(refillTicks, 1, 1200); }
        public boolean dropOnFullEnabled() { return dropOnFullEnabled; }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
