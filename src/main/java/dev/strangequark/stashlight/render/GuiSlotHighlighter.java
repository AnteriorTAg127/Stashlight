package dev.strangequark.stashlight.render;

import dev.strangequark.stashlight.config.Config;
import dev.strangequark.stashlight.model.HighlightTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws slot overlays on open container screens for active highlight targets.
 * Highlights the top-level container slot; nested items use a distinct color.
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
            if (target.pos().equals(currentContainerPos) && target.topSlot() >= 0) {
                matches.add(target);
            }
        }

        if (matches.isEmpty()) return;

        int left = screen.leftPos;
        int top = screen.topPos;

        for (Slot slot : screen.getMenu().slots) {
            if (mc.player != null && slot.container == mc.player.getInventory()) continue;

            int slotIndex = slot.index;
            for (HighlightTarget target : matches) {
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
