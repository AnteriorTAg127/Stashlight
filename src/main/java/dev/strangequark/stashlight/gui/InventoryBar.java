package dev.strangequark.stashlight.gui;

import io.wispforest.owo.ui.base.BaseComponent;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import static dev.strangequark.stashlight.gui.UIStyle.*;

/**
 * Displays the player's inventory (9×4 slots) inside the search screen.
 * Read-only — no click interaction.
 * <p>
 * Syncs every frame by reading {@code player.getInventory().items}
 * directly, so items picked up via remote-take appear instantly.
 */
public class InventoryBar extends BaseComponent {
    private static final int ROWS = 4;
    private static final int COLS = 9;

    public InventoryBar() {
        this.horizontalSizing(Sizing.content());
        this.verticalSizing(Sizing.content());
    }

    @Override
    protected int determineHorizontalContentSize(Sizing sizing) {
        return COLS * (SLOT_SIZE + GAP) + GAP;
    }

    @Override
    protected int determineVerticalContentSize(Sizing sizing) {
        return ROWS * (SLOT_SIZE + GAP) + GAP;
    }

    @Override
    public void draw(OwoUIDrawContext graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;

        var inventory = mc.player.getInventory();

        for (int i = 0; i < 36; i++) {
            int row = i / COLS;
            int col = i % COLS;

            int slotX = this.x + GAP + col * (SLOT_SIZE + GAP);
            int slotY = this.y + GAP + row * (SLOT_SIZE + GAP);

            // Slot background
            graphics.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, SLOT_BG);

            // Item icon
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                int iconX = slotX + (SLOT_SIZE - 16) / 2;
                int iconY = slotY + (SLOT_SIZE - 16) / 2;
                graphics.renderItem(stack, iconX, iconY);

                // Count label
                int count = stack.getCount();
                if (count > 1) {
                    var font = mc.font;
                    String countStr = String.valueOf(count);
                    float scale = count > 999 ? 0.75f : 0.85f;
                    float labelX = slotX + SLOT_SIZE - font.width(countStr) * scale - 1;
                    float labelY = slotY + SLOT_SIZE - font.lineHeight * scale - 1;
                    graphics.drawText(
                            net.minecraft.network.chat.Component.literal(countStr),
                            labelX, labelY, scale,
                            0xFFFFFFFF,
                            OwoUIDrawContext.TextAnchor.TOP_LEFT
                    );
                }
            }
        }
    }
}
