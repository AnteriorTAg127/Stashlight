package dev.strangequark.stashlight.gui;

import dev.strangequark.stashlight.config.Config;
import dev.strangequark.stashlight.model.DisplayItem;
import dev.strangequark.stashlight.take.TakeQueue;
import dev.strangequark.stashlight.take.TakeQueueEntry;
import io.wispforest.owo.ui.base.BaseComponent;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.List;

import static dev.strangequark.stashlight.gui.UIStyle.*;

/**
 * Visual panel for the take queue. Renders up to {@link Config.TakeQueueConfig#capacity()}
 * slots, laid out as either 4×4 (when the inventory bar is shown) or 1×16.
 * Out-of-range entries are drawn with a grayed background.
 */
public final class TakeQueuePanel extends BaseComponent {

    private final TakeQueue queue;
    private int columns = 4;
    private int hoveredIndex = -1;

    public TakeQueuePanel(TakeQueue queue) {
        this.queue = queue;
        this.horizontalSizing(Sizing.content());
        this.verticalSizing(Sizing.content());
    }

    public void setColumns(int columns) {
        this.columns = Math.max(1, columns);
        notifyParentIfMounted();
    }

    @Override
    protected int determineHorizontalContentSize(Sizing sizing) {
        return columns * (SLOT_SIZE + GAP) + GAP;
    }

    @Override
    protected int determineVerticalContentSize(Sizing sizing) {
        int capacity = Config.get().takeQueue().capacity();
        int rows = (int) Math.ceil((double) capacity / columns);
        return rows * (SLOT_SIZE + GAP) + GAP;
    }

    @Override
    public void draw(OwoUIDrawContext graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        List<TakeQueueEntry> entries = queue.entries();
        int capacity = Config.get().takeQueue().capacity();
        hoveredIndex = hoveredIndex(mouseX, mouseY);

        var mc = Minecraft.getInstance();
        var font = mc.font;

        for (int i = 0; i < capacity; i++) {
            int row = i / columns;
            int col = i % columns;
            int slotX = this.x + GAP + col * (SLOT_SIZE + GAP);
            int slotY = this.y + GAP + row * (SLOT_SIZE + GAP);

            TakeQueueEntry entry = i < entries.size() ? entries.get(i) : null;
            boolean reachable = entry != null && queue.isReachable(entry);
            int bgColor = entry == null ? SLOT_BG : (reachable ? SLOT_BG : 0xFF555555);
            graphics.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, bgColor);

            if (entry != null) {
                ItemStack stack = entry.displayStack();
                int iconX = slotX + (SLOT_SIZE - 16) / 2;
                int iconY = slotY + (SLOT_SIZE - 16) / 2;
                graphics.renderItem(stack, iconX, iconY);

                int qty = entry.quantity();
                if (qty > 1) {
                    String qtyStr = String.valueOf(qty);
                    float scale = qty > 999 ? 0.75f : 0.85f;
                    float labelX = slotX + SLOT_SIZE - font.width(qtyStr) * scale - 1;
                    float labelY = slotY + SLOT_SIZE - font.lineHeight * scale - 1;
                    graphics.drawText(
                            Component.literal(qtyStr),
                            labelX, labelY, scale,
                            reachable ? COUNT_COLOR : 0xFFAAAAAA,
                            OwoUIDrawContext.TextAnchor.TOP_LEFT
                    );
                }

                if (i == hoveredIndex && mc.player != null && mc.level != null) {
                    renderEntryTooltip(graphics, entry, mouseX, mouseY, mc);
                }
            }
        }
    }

    private void renderEntryTooltip(OwoUIDrawContext graphics, TakeQueueEntry entry, int mouseX, int mouseY, Minecraft mc) {
        DisplayItem resolved = queue.resolve(entry).orElse(null);
        ItemStack stack = resolved != null ? resolved.stack() : entry.displayStack();

        List<Component> lines = new ArrayList<>(stack.getTooltipLines(
                Item.TooltipContext.of(mc.level),
                mc.player,
                mc.options.advancedItemTooltips ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL
        ));
        lines.add(Component.empty());
        lines.add(Component.translatable("gui.stashlight.label.quantity")
                .append(Component.literal(": " + entry.quantity())));
        if (resolved == null || !queue.isReachable(resolved)) {
            lines.add(Component.translatable("gui.stashlight.message.queueUnreachable").withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("gui.stashlight.tooltip.queueRightClickRemove").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));

        List<ClientTooltipComponent> components = new ArrayList<>();
        for (Component line : lines) {
            components.add(ClientTooltipComponent.create(line.getVisualOrderText()));
        }
        graphics.drawTooltip(mc.font, mouseX, mouseY, components);
    }

    public void refresh() {
        notifyParentIfMounted();
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent event, boolean doubled) {
        int idx = hoveredIndex((int) (this.x + event.x()), (int) (this.y + event.y()));
        if (idx >= 0 && idx < queue.entries().size() && event.button() == 1) {
            queue.remove(idx);
            refresh();
            return true;
        }
        return super.onMouseDown(event, doubled);
    }

    private int hoveredIndex(int mouseX, int mouseY) {
        int relX = mouseX - this.x - GAP;
        int relY = mouseY - this.y - GAP;
        if (relX < 0 || relY < 0) return -1;

        int stride = SLOT_SIZE + GAP;
        int col = relX / stride;
        int row = relY / stride;
        if (relX % stride >= SLOT_SIZE) return -1;
        if (relY % stride >= SLOT_SIZE) return -1;
        if (col >= columns) return -1;

        int idx = row * columns + col;
        return idx < Config.get().takeQueue().capacity() ? idx : -1;
    }
}
