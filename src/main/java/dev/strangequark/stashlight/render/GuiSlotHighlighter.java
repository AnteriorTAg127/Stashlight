package dev.strangequark.stashlight.render;

import dev.strangequark.stashlight.config.Config;
import dev.strangequark.stashlight.model.HighlightTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws slot overlays on open container screens for active highlight targets.
 * <p>
 * Since v1.3, supports two matching modes controlled by
 * {@code liveSlotHighlight.enabled}:
 * <ul>
 *   <li><b>Live mode</b> (default off): matches by
 *       {@link ItemStack#isSameItemSameComponents} against the <em>current</em>
 *       slot contents. Items moved by another player cause the highlight to
 *       follow the item to its new slot, or disappear if the item is taken.</li>
 *   <li><b>Legacy mode</b> (fallback): matches by cached {@code topSlot} index.
 *       Used when the player disables live matching.</li>
 * </ul>
 */
public final class GuiSlotHighlighter {

    private static BlockPos currentContainerPos = null;

    private GuiSlotHighlighter() {
    }

    public static void setCurrentContainerPos(BlockPos pos) {
        currentContainerPos = pos;
    }

    public static void clearCurrentContainerPos() {
        currentContainerPos = null;
    }

    public static void render(AbstractContainerScreen<?> screen, GuiGraphics graphics, float tickDelta) {
        var cfg = Config.get().highlight();
        if (cfg == null) return;
        if (!cfg.guiSlotEnabled() && !cfg.nestedBoxEnabled()) return;
        if (currentContainerPos == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        List<HighlightTarget> active = HighlightManager.getActiveHighlights();
        List<HighlightTarget> matches = new ArrayList<>();
        for (HighlightTarget target : active) {
            if (target.pos().equals(currentContainerPos)) {
                matches.add(target);
            }
        }

        if (matches.isEmpty()) return;

        boolean liveMode = Config.get().liveSlotHighlight().enabled();

        int left = screen.leftPos;
        int top = screen.topPos;

        for (Slot slot : screen.getMenu().slots) {
            if (mc.player != null && slot.container == mc.player.getInventory()) continue;

            if (liveMode) {
                renderLive(slot, matches, left, top, graphics, cfg);
            } else {
                renderLegacy(slot, matches, left, top, graphics, cfg);
            }
        }
    }

    private static void renderLive(Slot slot, List<HighlightTarget> matches,
                                    int left, int top,
                                    GuiGraphics graphics,
                                    Config.HighlightConfig cfg) {
        ItemStack liveStack = slot.getItem();
        if (liveStack.isEmpty()) return;

        for (HighlightTarget target : matches) {
            if (target.stack() == null || target.stack().isEmpty()) continue;
            if (!ItemStack.isSameItemSameComponents(liveStack, target.stack())) continue;

            int x = left + slot.x;
            int y = top + slot.y;
            int color = target.isNested()
                    ? (cfg.nestedBoxEnabled() ? cfg.nestedBoxColor() : 0)
                    : (cfg.guiSlotEnabled() ? cfg.guiSlotColor() : 0);
            if (color == 0) continue;

            long elapsed = System.currentTimeMillis() - target.startTimeMillis();
            if (elapsed > cfg.guiSlotDisplayTimeSeconds() * 1000L) continue;
            if (cfg.guiSlotPulse() && !HighlightEffect.shouldRender(elapsed)) continue;

            drawSlotOutline(graphics, x, y, color);
        }
    }

    private static void renderLegacy(Slot slot, List<HighlightTarget> matches,
                                     int left, int top,
                                     GuiGraphics graphics,
                                     Config.HighlightConfig cfg) {
        int slotIndex = slot.index;
        for (HighlightTarget target : matches) {
            if (target.topSlot() < 0) continue;
            if (target.topSlot() != slotIndex) continue;

            int x = left + slot.x;
            int y = top + slot.y;
            int color = target.isNested()
                    ? (cfg.nestedBoxEnabled() ? cfg.nestedBoxColor() : 0)
                    : (cfg.guiSlotEnabled() ? cfg.guiSlotColor() : 0);
            if (color == 0) continue;

            long elapsed = System.currentTimeMillis() - target.startTimeMillis();
            if (elapsed > cfg.guiSlotDisplayTimeSeconds() * 1000L) continue;
            if (cfg.guiSlotPulse() && !HighlightEffect.shouldRender(elapsed)) continue;

            drawSlotOutline(graphics, x, y, color);
        }
    }

    private static void drawSlotOutline(GuiGraphics graphics, int x, int y, int color) {
        int size = 16;
        int thickness = 2;
        // Top
        graphics.fill(x, y, x + size, y + thickness, color);
        // Bottom
        graphics.fill(x, y + size - thickness, x + size, y + size, color);
        // Left
        graphics.fill(x, y, x + thickness, y + size, color);
        // Right
        graphics.fill(x + size - thickness, y, x + size, y + size, color);
    }
}
