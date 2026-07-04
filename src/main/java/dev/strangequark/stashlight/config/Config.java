package dev.strangequark.stashlight.config;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.strangequark.stashlight.logic.sort.SortKey;
import dev.strangequark.stashlight.model.DataSourceMode;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;


public final class Config {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Path FILE =
            FabricLoader.getInstance()
                    .getConfigDir()
                    .resolve("stashlight.json");

    private static Config INSTANCE;

    /* ---------------- persisted preferences ---------------- */

    private boolean lookAtTarget = false;
    private boolean showSmallContainers = false;
    private int searchRadiusIndex = 2;
    private HighlightConfig highlight = new HighlightConfig();
    private AutoIndexConfig autoIndex = new AutoIndexConfig();
    private DataSourceConfig dataSource = new DataSourceConfig();
    private ProximityScanConfig proximityScan = new ProximityScanConfig();
    private VanillaFallbackConfig vanillaFallback = new VanillaFallbackConfig();
    private LiveSlotHighlightConfig liveSlotHighlight = new LiveSlotHighlightConfig();
    private SearchInventoryBarConfig searchInventoryBar = new SearchInventoryBarConfig();
    private TakeQueueConfig takeQueue = new TakeQueueConfig();
    private RemoteTakeConfig remoteTake = new RemoteTakeConfig();

    /* ---------------- runtime-only state ---------------- */

    private transient String searchQuery = "";
    private transient SortKey sortKey = SortKey.ALPHABETICAL;

    /* ---------------- lifecycle ---------------- */

    private Config() {
    }

    public static Config get() {
        if (INSTANCE == null) load();
        return INSTANCE;
    }

    public boolean lookAtTarget() {
        return lookAtTarget;
    }

    public boolean showSmallContainers() {
        return showSmallContainers;
    }

    public String searchQuery() {
        return searchQuery;
    }

    public SortKey sortKey() {
        return sortKey;
    }

    public int searchRadiusIndex() {
        if (searchRadiusIndex < 0 || searchRadiusIndex > 5) {
            searchRadiusIndex = 2;
        }
        return searchRadiusIndex;
    }

    public HighlightConfig highlight() {
        if (highlight == null) highlight = new HighlightConfig();
        return highlight;
    }

    public AutoIndexConfig autoIndex() {
        if (autoIndex == null) autoIndex = new AutoIndexConfig();
        return autoIndex;
    }

    public DataSourceConfig dataSource() {
        if (dataSource == null) dataSource = new DataSourceConfig();
        return dataSource;
    }

    public ProximityScanConfig proximityScan() {
        if (proximityScan == null) proximityScan = new ProximityScanConfig();
        return proximityScan;
    }

    public VanillaFallbackConfig vanillaFallback() {
        if (vanillaFallback == null) vanillaFallback = new VanillaFallbackConfig();
        return vanillaFallback;
    }

    public LiveSlotHighlightConfig liveSlotHighlight() {
        if (liveSlotHighlight == null) liveSlotHighlight = new LiveSlotHighlightConfig();
        return liveSlotHighlight;
    }

    public SearchInventoryBarConfig searchInventoryBar() {
        if (searchInventoryBar == null) searchInventoryBar = new SearchInventoryBarConfig();
        return searchInventoryBar;
    }

    public TakeQueueConfig takeQueue() {
        if (takeQueue == null) takeQueue = new TakeQueueConfig();
        return takeQueue;
    }

    public RemoteTakeConfig remoteTake() {
        if (remoteTake == null) remoteTake = new RemoteTakeConfig();
        return remoteTake;
    }

    public void setLookAtTarget(boolean value) {
        if (this.lookAtTarget == value) return;
        this.lookAtTarget = value;
        save();
    }

    public void setShowSmallContainers(boolean value) {
        if (this.showSmallContainers == value) return;
        this.showSmallContainers = value;
        save();
    }

    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery;
    }

    public void setSortKey(SortKey key) {
        this.sortKey = key;
    }

    public void setSearchRadiusIndex(int index) {
        // Clamp before saving
        this.searchRadiusIndex = Math.max(0, Math.min(index, 5));
        save();
    }

    public static void load() {
        if (Files.exists(FILE)) {
            try {
                INSTANCE = GSON.fromJson(Files.readString(FILE), Config.class);
                INSTANCE.validate();
                return;
            } catch (Exception ignored) {
            }
        }
        INSTANCE = new Config();
    }

    private void validate() {
        this.searchRadiusIndex = Math.max(0, Math.min(this.searchRadiusIndex, 5));
        if (this.highlight == null) this.highlight = new HighlightConfig();
        if (this.autoIndex == null) this.autoIndex = new AutoIndexConfig();
        if (this.dataSource == null) this.dataSource = new DataSourceConfig();
        if (this.proximityScan == null) this.proximityScan = new ProximityScanConfig();
        if (this.vanillaFallback == null) this.vanillaFallback = new VanillaFallbackConfig();
        if (this.liveSlotHighlight == null) this.liveSlotHighlight = new LiveSlotHighlightConfig();
        if (this.searchInventoryBar == null) this.searchInventoryBar = new SearchInventoryBarConfig();
        if (this.takeQueue == null) this.takeQueue = new TakeQueueConfig();
        if (this.remoteTake == null) this.remoteTake = new RemoteTakeConfig();
        this.dataSource.validate();
    }

    public static void save() {
        try {
            Files.writeString(FILE, GSON.toJson(get()));
        } catch (IOException ignored) {
        }
    }

    public static final class HighlightConfig {
        private boolean blockOutlineEnabled = true;
        private String blockOutlineColor = "#FFFFFFFF";
        private int blockOutlineCycles = 5;

        private boolean guiSlotEnabled = true;
        private String guiSlotColor = "#FF0066AA";
        private boolean guiSlotPulse = true;
        private int guiSlotDisplayTimeSeconds = 5;

        private boolean nestedBoxEnabled = true;
        private String nestedBoxColor = "#FF006688";

        private boolean worldMarkerBeamEnabled = true;
        private String worldMarkerBeamColor = "#FFCC8800";
        private boolean worldMarkerBeamThroughWalls = true;

        private boolean worldMarkerBoxEnabled = true;
        private String worldMarkerBoxColor = "#FFAAAAAA";
        private int worldMarkerBoxCycles = 5;

        private int worldMarkerMaxMarkers = 16;
        private int worldMarkerMaxDistance = 128;

        private boolean highlightAllOnSearch = false;
        private boolean sourceColorCoding = true;

        public boolean blockOutlineEnabled() { return blockOutlineEnabled; }
        public int blockOutlineColor() { return parseColor(blockOutlineColor, 0xFFFFFFFF); }
        public int blockOutlineCycles() { return clamp(blockOutlineCycles, 1, 20); }
        public void setBlockOutlineEnabled(boolean enabled) { this.blockOutlineEnabled = enabled; }
        public void setBlockOutlineColor(String color) { this.blockOutlineColor = color; }

        public boolean guiSlotEnabled() { return guiSlotEnabled; }
        public int guiSlotColor() { return parseColor(guiSlotColor, 0xFF0066AA); }
        public boolean guiSlotPulse() { return guiSlotPulse; }
        public int guiSlotDisplayTimeSeconds() { return clamp(guiSlotDisplayTimeSeconds, 1, 60); }
        public void setGuiSlotDisplayTimeSeconds(int seconds) {
            this.guiSlotDisplayTimeSeconds = clamp(seconds, 1, 60);
        }
        public void setGuiSlotPulse(boolean pulse) {
            this.guiSlotPulse = pulse;
        }
        public void setGuiSlotEnabled(boolean enabled) { this.guiSlotEnabled = enabled; }
        public void setGuiSlotColor(String color) { this.guiSlotColor = color; }

        public boolean nestedBoxEnabled() { return nestedBoxEnabled; }
        public int nestedBoxColor() { return parseColor(nestedBoxColor, 0xFF006688); }
        public void setNestedBoxEnabled(boolean enabled) { this.nestedBoxEnabled = enabled; }
        public void setNestedBoxColor(String color) { this.nestedBoxColor = color; }

        public boolean worldMarkerBeamEnabled() { return worldMarkerBeamEnabled; }
        public int worldMarkerBeamColor() { return parseColor(worldMarkerBeamColor, 0xFFCC8800); }
        public boolean worldMarkerBeamThroughWalls() { return worldMarkerBeamThroughWalls; }
        public void setWorldMarkerBeamEnabled(boolean enabled) { this.worldMarkerBeamEnabled = enabled; }
        public void setWorldMarkerBeamColor(String color) { this.worldMarkerBeamColor = color; }
        public void setWorldMarkerBeamThroughWalls(boolean throughWalls) { this.worldMarkerBeamThroughWalls = throughWalls; }

        public boolean worldMarkerBoxEnabled() { return worldMarkerBoxEnabled; }
        public int worldMarkerBoxColor() { return parseColor(worldMarkerBoxColor, 0xFFAAAAAA); }
        public int worldMarkerBoxCycles() { return clamp(worldMarkerBoxCycles, 1, 20); }
        public void setWorldMarkerBoxEnabled(boolean enabled) { this.worldMarkerBoxEnabled = enabled; }
        public void setWorldMarkerBoxColor(String color) { this.worldMarkerBoxColor = color; }

        public int worldMarkerMaxMarkers() { return clamp(worldMarkerMaxMarkers, 1, 256); }
        public int worldMarkerMaxDistance() { return clamp(worldMarkerMaxDistance, 16, 2048); }

        public boolean highlightAllOnSearch() { return highlightAllOnSearch; }
        public boolean sourceColorCoding() { return sourceColorCoding; }

        public int localSourceColor() { return parseColor("#FF55FF55", 0xFF55FF55); }
        public int serverSourceColor() { return parseColor("#FF5555FF", 0xFF5555FF); }

        private static int parseColor(String hex, int fallback) {
            if (hex == null) return fallback;
            String s = hex.trim();
            if (s.startsWith("#")) s = s.substring(1);
            try {
                return (int) Long.parseLong(s, 16);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }
    }

    public static final class AutoIndexConfig {
        private boolean enabled = false;
        private int radius = 32;
        private String radiusUnit = "blocks";
        private int containersPerTick = 4;
        private int scanIntervalTicks = 20;
        private int maxMillisPerTick = 2;
        private int rescanTtlSeconds = 300;
        private boolean onlyLoadedChunks = true;

        public boolean enabled() { return enabled; }
        public int radius() { return clamp(radius, 4, 256); }
        public boolean radiusInBlocks() { return !"chunks".equalsIgnoreCase(radiusUnit); }
        public int containersPerTick() { return clamp(containersPerTick, 1, 64); }
        public int scanIntervalTicks() { return clamp(scanIntervalTicks, 1, 1200); }
        public int maxMillisPerTick() { return clamp(maxMillisPerTick, 1, 50); }
        public int rescanTtlSeconds() { return clamp(rescanTtlSeconds, 10, 3600); }
        public boolean onlyLoadedChunks() { return onlyLoadedChunks; }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(value, max));
        }
    }

    public static final class DataSourceConfig {
        private String mode = "MERGED";
        private boolean persistServerData = false;

        public DataSourceMode mode() {
            try {
                return DataSourceMode.valueOf(mode);
            } catch (Exception ignored) {
                return DataSourceMode.MERGED;
            }
        }

        public void setMode(DataSourceMode mode) {
            this.mode = mode.name();
        }

        public boolean persistServerData() {
            return persistServerData;
        }

        public void setPersistServerData(boolean persist) {
            this.persistServerData = persist;
        }

        private void validate() {
            try {
                DataSourceMode.valueOf(mode);
            } catch (Exception ignored) {
                mode = "MERGED";
            }
        }

    }

    public static final class ProximityScanConfig {
        private boolean enabled = false;
        private int radius = 6;
        private int containerThreshold = 3;
        private int scanIntervalSeconds = 3;

        public boolean enabled() { return enabled; }
        public int radius() { return clamp(radius, 4, 32); }
        public int containerThreshold() { return clamp(containerThreshold, 1, 64); }
        public int scanIntervalSeconds() { return clamp(scanIntervalSeconds, 1, 60); }

        public void setEnabled(boolean v) { this.enabled = v; }
        public void setRadius(int v) { this.radius = clamp(v, 4, 32); }
        public void setContainerThreshold(int v) { this.containerThreshold = clamp(v, 1, 64); }
        public void setScanIntervalSeconds(int v) { this.scanIntervalSeconds = clamp(v, 1, 60); }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(value, max));
        }
    }

    public static final class VanillaFallbackConfig {
        private boolean enabled = false;
        private int loopIntervalMillis = 300;
        private int takeIntervalMillis = 300;
        private int maxContainersPerLoop = 32;
        private boolean takeOnlyIndexed = true;
        private String blockFilter = "chest,barrel,shulker_box";
        private String scanComboKey = "NONE";
        private String scanComboMods = "";

        public boolean enabled() { return enabled; }
        public int loopIntervalMillis() { return clamp(loopIntervalMillis, 100, 5000); }
        public int takeIntervalMillis() { return clamp(takeIntervalMillis, 100, 5000); }
        public int maxContainersPerLoop() { return clamp(maxContainersPerLoop, 1, 256); }
        public boolean takeOnlyIndexed() { return takeOnlyIndexed; }
        public String blockFilter() { return blockFilter != null ? blockFilter : "chest,barrel,shulker_box"; }
        public String scanComboKey() { return scanComboKey != null ? scanComboKey : "NONE"; }
        public String scanComboMods() { return scanComboMods != null ? scanComboMods : ""; }

        public void setEnabled(boolean v) { this.enabled = v; }
        public void setLoopIntervalMillis(int v) { this.loopIntervalMillis = clamp(v, 100, 5000); }
        public void setTakeIntervalMillis(int v) { this.takeIntervalMillis = clamp(v, 100, 5000); }
        public void setMaxContainersPerLoop(int v) { this.maxContainersPerLoop = clamp(v, 1, 256); }
        public void setTakeOnlyIndexed(boolean v) { this.takeOnlyIndexed = v; }
        public void setBlockFilter(String v) { this.blockFilter = v; }
        public void setScanComboKey(String v) { this.scanComboKey = v; }
        public void setScanComboMods(String v) { this.scanComboMods = v; }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(value, max));
        }
    }

    public static final class LiveSlotHighlightConfig {
        private boolean enabled = false;

        public boolean enabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
    }

    public static final class SearchInventoryBarConfig {
        private boolean enabled = false;

        public boolean enabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
    }

    public static final class TakeQueueConfig {
        private boolean enabled = true;
        private int capacity = 16;

        public boolean enabled() { return enabled; }
        public int capacity() { return clamp(capacity, 1, 64); }

        public void setEnabled(boolean v) { this.enabled = v; }
        public void setCapacity(int v) { this.capacity = clamp(v, 1, 64); }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(value, max));
        }
    }

    public static final class RemoteTakeConfig {
        private boolean enabled = false;
        private int defaultQuantity = 1;
        private boolean dropOnFullEnabled = true;
        private boolean keepScreenOnTake = true;
        private boolean takeContainingBoxEnabled = true;

        public boolean enabled() { return enabled; }
        public int defaultQuantity() { return clamp(defaultQuantity, 1, 64); }
        public boolean dropOnFullEnabled() { return dropOnFullEnabled; }
        public boolean keepScreenOnTake() { return keepScreenOnTake; }
        public boolean takeContainingBoxEnabled() { return takeContainingBoxEnabled; }

        public void setEnabled(boolean v) { this.enabled = v; }
        public void setDefaultQuantity(int v) { this.defaultQuantity = clamp(v, 1, 64); }
        public void setDropOnFullEnabled(boolean v) { this.dropOnFullEnabled = v; }
        public void setKeepScreenOnTake(boolean v) { this.keepScreenOnTake = v; }
        public void setTakeContainingBoxEnabled(boolean v) { this.takeContainingBoxEnabled = v; }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(value, max));
        }
    }
}
