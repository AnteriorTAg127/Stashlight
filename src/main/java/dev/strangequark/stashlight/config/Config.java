package dev.strangequark.stashlight.config;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.strangequark.stashlight.logic.sort.SortKey;
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

    public static void load() {
        if (Files.exists(FILE)) {
            try {
                INSTANCE = GSON.fromJson(Files.readString(FILE), Config.class);
                return;
            } catch (Exception ignored) {
            }
        }
        INSTANCE = new Config();
    }

    public static void save() {
        try {
            Files.writeString(FILE, GSON.toJson(get()));
        } catch (IOException ignored) {
        }
    }
}
