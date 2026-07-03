package dev.strangequark.stashlight.render;

import dev.strangequark.stashlight.config.Config;
import dev.strangequark.stashlight.model.DisplayItem;
import dev.strangequark.stashlight.model.HighlightTarget;
import dev.strangequark.stashlight.model.IndexedItem;
import dev.strangequark.stashlight.util.Util;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class HighlightManager {
    private static final List<HighlightTarget> highlights = new ArrayList<>();

    private HighlightManager() {
    }

    public static boolean tryHighlight(IndexedItem item) {
        return tryHighlight(item, false);
    }

    /**
     * Highlights a single indexed item. When {@code additive} is false, existing
     * highlights for the same dimension are cleared first (legacy behaviour).
     */
    public static boolean tryHighlight(IndexedItem item, boolean additive) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return false;

        String currentDim = Util.getDimensionName(client.level);
        if (!currentDim.equals(item.dimension())) {
            notifyWrongDimension(client.player);
            return false;
        }

        if (!additive) {
            clearByDimension(currentDim);
        }

        highlights.add(new HighlightTarget(
                item.pos(), item.path(), item.source(),
                System.currentTimeMillis(), colorForSource(item.source()),
                item.stack().copy()
        ));
        return true;
    }

    public static boolean tryHighlight(DisplayItem item) {
        return tryHighlight(item, false);
    }

    public static boolean tryHighlight(DisplayItem item, boolean additive) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || item.sources().isEmpty()) return false;

        String currentDim = Util.getDimensionName(client.level);
        if (!currentDim.equals(item.dimension())) {
            notifyWrongDimension(client.player);
            return false;
        }

        if (!additive) {
            clearByDimension(currentDim);
        }

        long now = System.currentTimeMillis();
        int color = colorForSource(item.source());
        for (IndexedItem source : item.sources()) {
            highlights.add(new HighlightTarget(
                    source.pos(), source.path(), source.source(), now, color,
                    source.stack().copy()
            ));
        }
        return true;
    }

    /**
     * Highlights every source item in the given list. Clears existing highlights
     * for the current dimension first.
     */
    public static int highlightAll(List<IndexedItem> items) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return 0;

        String currentDim = Util.getDimensionName(client.level);
        clearByDimension(currentDim);

        long now = System.currentTimeMillis();
        int added = 0;
        for (IndexedItem item : items) {
            if (!currentDim.equals(item.dimension())) continue;
            highlights.add(new HighlightTarget(
                    item.pos(), item.path(), item.source(), now,
                    colorForSource(item.source()),
                    item.stack().copy()
            ));
            added++;
        }
        return added;
    }

    public static void clear() {
        highlights.clear();
    }

    public static void clearByDimension(String dimension) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        String currentDim = Util.getDimensionName(client.level);
        if (!currentDim.equals(dimension)) return;
        highlights.clear();
    }

    private static long maxHighlightDuration() {
        return Math.max(HighlightEffect.MAX_DURATION, Config.get().highlight().guiSlotDisplayTimeSeconds() * 1000L);
    }

    public static void removeExpired() {
        long now = System.currentTimeMillis();
        long maxDuration = maxHighlightDuration();
        highlights.removeIf(h -> now - h.startTimeMillis() > maxDuration);
    }

    public static List<HighlightTarget> getActiveHighlights() {
        long now = System.currentTimeMillis();
        long maxDuration = maxHighlightDuration();
        List<HighlightTarget> active = new ArrayList<>();
        for (HighlightTarget h : highlights) {
            if (now - h.startTimeMillis() <= maxDuration) {
                active.add(h);
            }
        }
        return active;
    }

    public static int colorForSource(dev.strangequark.stashlight.model.Source source) {
        var cfg = Config.get().highlight();
        if (cfg == null || !cfg.sourceColorCoding()) {
            return cfg != null ? cfg.blockOutlineColor() : 0xFFFFFFFF;
        }
        return switch (source) {
            case SERVER_PUSH -> cfg.serverSourceColor();
            default -> cfg.localSourceColor();
        };
    }

    private static void notifyWrongDimension(@NotNull Player player) {
        player.displayClientMessage(Component.translatable("gui.stashlight.message.differentDimension").withStyle(ChatFormatting.RED), false);
    }
}
