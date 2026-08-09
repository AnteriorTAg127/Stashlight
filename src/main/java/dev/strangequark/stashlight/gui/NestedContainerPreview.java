package dev.strangequark.stashlight.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static dev.strangequark.stashlight.gui.UIStyle.*;

/**
 * Renders a chest-like preview grid for nested containers (shulker boxes,
 * bundles, etc.) when Shift is held. This mimics the preview style used by
 * shulker-box tooltip mods.
 */
public final class NestedContainerPreview {

    private static final int PREVIEW_SLOT_SIZE = 18;
    private static final int PREVIEW_GAP = 1;
    private static final int PREVIEW_PADDING = 6;
    private static final int PREVIEW_TITLE_HEIGHT = 12;
    private static final int BACKGROUND = 0xF0101010;
    private static final int BORDER = 0xFF555555;
    private static final int EMPTY_SLOT_BG = 0xFF222222;

    private NestedContainerPreview() {
    }

    public record Preview(List<ItemStack> items, int columns, int rows, Component title) {
    }

    public static Optional<Preview> of(ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();

        // Shulker box / generic container: 9 x 3 grid
        ItemContainerContents container = stack.get(DataComponents.CONTAINER);
        if (container != null) {
            NonNullList<ItemStack> list = NonNullList.withSize(27, ItemStack.EMPTY);
            container.copyInto(list);
            return Optional.of(new Preview(
                    Collections.unmodifiableList(new ArrayList<>(list)),
                    9, 3,
                    stack.getHoverName()
            ));
        }

        // Bundle: compact grid
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) {
            List<ItemStack> items = new ArrayList<>();
            for (ItemStack item : bundle.items()) {
                items.add(item);
            }
            if (items.isEmpty()) return Optional.empty();
            int cols = Math.min(9, items.size());
            int rows = (int) Math.ceil((double) items.size() / cols);
            return Optional.of(new Preview(
                    Collections.unmodifiableList(items),
                    cols, rows,
                    stack.getHoverName()
            ));
        }

        return Optional.empty();
    }

    public static boolean hasPreview(ItemStack stack) {
        return of(stack).isPresent();
    }

    /**
     * Renders the preview near the given anchor point, keeping it on screen.
     */
    public static void render(GuiGraphics graphics, ItemStack stack, int anchorX, int anchorY) {
        Optional<Preview> previewOpt = of(stack);
        if (previewOpt.isEmpty()) return;
        Preview preview = previewOpt.get();

        var window = Minecraft.getInstance().getWindow();
        int screenW = window.getGuiScaledWidth();
        int screenH = window.getGuiScaledHeight();

        int gridW = preview.columns * (PREVIEW_SLOT_SIZE + PREVIEW_GAP) + PREVIEW_GAP;
        int gridH = preview.rows * (PREVIEW_SLOT_SIZE + PREVIEW_GAP) + PREVIEW_GAP;
        int panelW = gridW + PREVIEW_PADDING * 2;
        int panelH = gridH + PREVIEW_PADDING * 2 + PREVIEW_TITLE_HEIGHT;

        int x = anchorX + 12;
        int y = anchorY + 12;
        if (x + panelW > screenW) x = anchorX - panelW - 8;
        if (y + panelH > screenH) y = anchorY - panelH - 8;
        x = Math.max(4, Math.min(x, screenW - panelW - 4));
        y = Math.max(4, Math.min(y, screenH - panelH - 4));

        // Background + border
        graphics.fill(x, y, x + panelW, y + panelH, BORDER);
        graphics.fill(x + 1, y + 1, x + panelW - 1, y + panelH - 1, BACKGROUND);

        // Title
        graphics.drawString(Minecraft.getInstance().font, preview.title,
                x + PREVIEW_PADDING, y + PREVIEW_PADDING / 2, 0xFFFFFFFF, false);

        int gridX = x + PREVIEW_PADDING;
        int gridY = y + PREVIEW_PADDING + PREVIEW_TITLE_HEIGHT;

        List<ItemStack> items = preview.items;
        int slot = 0;
        for (int row = 0; row < preview.rows; row++) {
            for (int col = 0; col < preview.columns; col++) {
                int slotX = gridX + col * (PREVIEW_SLOT_SIZE + PREVIEW_GAP);
                int slotY = gridY + row * (PREVIEW_SLOT_SIZE + PREVIEW_GAP);
                graphics.fill(slotX, slotY, slotX + PREVIEW_SLOT_SIZE, slotY + PREVIEW_SLOT_SIZE, EMPTY_SLOT_BG);

                if (slot < items.size()) {
                    ItemStack item = items.get(slot);
                    if (!item.isEmpty()) {
                        int iconX = slotX + (PREVIEW_SLOT_SIZE - 16) / 2;
                        int iconY = slotY + (PREVIEW_SLOT_SIZE - 16) / 2;
                        graphics.renderItem(item, iconX, iconY);
                        graphics.renderItemDecorations(Minecraft.getInstance().font, item, iconX, iconY);
                    }
                }
                slot++;
            }
        }
    }
}
