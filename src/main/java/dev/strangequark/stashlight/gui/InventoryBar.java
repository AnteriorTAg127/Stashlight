package dev.strangequark.stashlight.gui;

import io.wispforest.owo.ui.base.BaseComponent;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    private ItemStack hoveredStack = ItemStack.EMPTY;

    public InventoryBar() {
        this.horizontalSizing(Sizing.content());
        this.verticalSizing(Sizing.content());
    }

    public ItemStack getHoveredStack() {
        return hoveredStack;
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
        hoveredStack = ItemStack.EMPTY;

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
                            Component.literal(countStr),
                            labelX, labelY, scale,
                            0xFFFFFFFF,
                            OwoUIDrawContext.TextAnchor.TOP_LEFT
                    );
                }
            }

            if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE
                    && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                hoveredStack = inventory.getItem(i);
            }
        }

        if (!hoveredStack.isEmpty() && mc.level != null) {
            renderTooltip(graphics, hoveredStack, mouseX, mouseY, mc);
        }
    }

    private void renderTooltip(OwoUIDrawContext graphics, ItemStack stack, int mouseX, int mouseY, Minecraft mc) {
        assert mc.player != null;
        List<Component> lines = new ArrayList<>(stack.getTooltipLines(
                Item.TooltipContext.of(mc.level),
                mc.player,
                mc.options.advancedItemTooltips ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL
        ));

        if (NestedContainerPreview.hasPreview(stack)) {
            lines.add(Component.empty());
            lines.add(Component.translatable("gui.stashlight.tooltip.shiftPreview").withStyle(ChatFormatting.DARK_GRAY));
        }

        graphics.setTooltipForNextFrame(
                mc.font, lines,
                Optional.empty(),
                mouseX, mouseY,
                stack.get(DataComponents.TOOLTIP_STYLE)
        );
    }
}
