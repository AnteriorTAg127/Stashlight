package dev.strangequark.stashlight.gui;

import dev.strangequark.stashlight.config.Config;
import dev.strangequark.stashlight.model.DisplayItem;
import dev.strangequark.stashlight.model.EnchantEntry;
import dev.strangequark.stashlight.model.IndexedItem;
import dev.strangequark.stashlight.model.LocatePath;
import dev.strangequark.stashlight.render.HighlightManager;
import dev.strangequark.stashlight.util.Util;
import io.wispforest.owo.ui.base.BaseComponent;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.PositionedRectangle;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.strangequark.stashlight.gui.UIStyle.*;

public class ItemGrid extends BaseComponent {
    private List<DisplayItem> items = Collections.emptyList();
    private int slotsPerRow = 1;

    // Hovered slot index (-1 = none)
    private int hoveredIndex = -1;

    public ItemGrid() {
        this.horizontalSizing(Sizing.content());
        this.verticalSizing(Sizing.content());
    }

    public void setItems(List<DisplayItem> newItems, int newSlotsPerRow) {
        this.items = newItems;
        this.slotsPerRow = Math.max(1, newSlotsPerRow);
        this.hoveredIndex = -1;
        this.notifyParentIfMounted();
    }

    @Override
    protected int determineHorizontalContentSize(Sizing sizing) {
        return slotsPerRow * (SLOT_SIZE + GAP) + GAP;
    }

    @Override
    protected int determineVerticalContentSize(Sizing sizing) {
        if (items.isEmpty()) return 0;
        int rows = (int) Math.ceil((double) items.size() / slotsPerRow);
        return rows * (SLOT_SIZE + GAP) + GAP;
    }

    @Override
    public void draw(OwoUIDrawContext graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        if (items.isEmpty()) return;

        var mc = Minecraft.getInstance();
        var font = mc.font;

        // Recompute hovered slot from raw mouse coords (O(1) math, no iteration)
        hoveredIndex = slotIndexAt(mouseX, mouseY);

        int n = items.size();
        for (int i = 0; i < n; i++) {
            int row = i / slotsPerRow;
            int col = i % slotsPerRow;

            int slotX = this.x + GAP + col * (SLOT_SIZE + GAP);
            int slotY = this.y + GAP + row * (SLOT_SIZE + GAP);

            // Scissor cull: skip slots fully outside the scroll viewport.
            // intersectsScissor uses the scissor rect already set by ScrollContainer.
            if (!graphics.intersectsScissor(PositionedRectangle.of(slotX, slotY, SLOT_SIZE, SLOT_SIZE))) continue;

            boolean hovered = (i == hoveredIndex);

            // Background
            graphics.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE,
                    hovered ? SLOT_HOVER : SLOT_BG);

            // Hover outline
            if (hovered) {
                graphics.drawRectOutline(slotX, slotY, SLOT_SIZE, SLOT_SIZE, SLOT_OUTLINE);
            }

            // Item icon — render directly at 16x16 centered in the 24x24 slot.
            // No scaling = pixel perfect sharpness.
            DisplayItem displayItem = items.get(i);
            ItemStack stack = displayItem.stack();
            int iconX = slotX + (SLOT_SIZE - 16) / 2;
            int iconY = slotY + (SLOT_SIZE - 16) / 2;
            graphics.renderItem(stack, iconX, iconY);

            // Count label
            int count = displayItem.totalCount();
            if (count > 1) {
                String countStr = String.valueOf(count);
                float scale = count > 999 ? 0.75f : 0.85f;

                float labelX = slotX + SLOT_SIZE - font.width(countStr) * scale - 1;
                float labelY = slotY + SLOT_SIZE - font.lineHeight * scale - 1;
                graphics.drawText(
                        Component.literal(countStr),
                        labelX, labelY, scale,
                        COUNT_COLOR,
                        OwoUIDrawContext.TextAnchor.TOP_LEFT
                );
            }

            // Tooltip — only for the hovered slot, deferred to end of frame
            if (hovered && mc.player != null && mc.level != null) {
                renderTooltip(graphics, displayItem, mouseX, mouseY, mc);
            }
        }
    }

    private void renderTooltip(OwoUIDrawContext graphics, DisplayItem item, int mouseX, int mouseY, Minecraft mc) {
        assert mc.player != null;
        double dist = Math.sqrt(mc.player.blockPosition().distSqr(item.pos()));
        String formattedDist = String.format("%.1f", dist);
        String posStr = String.format("%d, %d, %d",
                item.pos().getX(), item.pos().getY(), item.pos().getZ());

        List<Component> lines = new ArrayList<>(item.stack().getTooltipLines(
                Item.TooltipContext.of(mc.level),
                mc.player,
                mc.options.advancedItemTooltips ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL
        ));

        lines.add(Component.empty());
        lines.add(Component.translatable("gui.stashlight.label.container").withStyle(ChatFormatting.GRAY)
                .append(": ")
                .append(Component.literal(item.containerName()).withStyle(ChatFormatting.WHITE)));

        if (isCtrlDown()) {
            lines.add(Component.translatable("gui.stashlight.label.location").withStyle(ChatFormatting.GRAY)
                    .append(": ")
                    .append(Component.translatable("gui.stashlight.label.blocksAway", formattedDist).withStyle(ChatFormatting.GRAY)));
            for (IndexedItem source : item.sources()) {
                String sourcePos = String.format("%d, %d, %d",
                        source.pos().getX(), source.pos().getY(), source.pos().getZ());
                lines.add(Component.literal("  " + sourcePos).withStyle(ChatFormatting.AQUA));
            }
        } else {
            lines.add(Component.translatable("gui.stashlight.label.location").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(posStr).withStyle(ChatFormatting.AQUA))
                    .append(Component.translatable("gui.stashlight.label.blocksAway", formattedDist).withStyle(ChatFormatting.GRAY)));
        }

        lines.add(Component.translatable("gui.stashlight.label.dimension").withStyle(ChatFormatting.GRAY).append(": ")
                .append(Component.literal(item.dimension()).withStyle(ChatFormatting.GREEN)));

        addSlotInfo(lines, item);

        if (!item.enchantments().isEmpty()) {
            lines.add(Component.empty());
            lines.add(Component.translatable("gui.stashlight.label.enchantments").withStyle(ChatFormatting.LIGHT_PURPLE));
            for (EnchantEntry entry : item.enchantments()) {
                lines.add(Component.literal(" • ")
                        .append(entry.displayName())
                        .append(" " + Util.toRoman(entry.level()))
                        .withStyle(ChatFormatting.GRAY));
            }
        }

        graphics.setTooltipForNextFrame(
                mc.font, lines,
                item.stack().getTooltipImage(),
                mouseX, mouseY,
                item.stack().get(DataComponents.TOOLTIP_STYLE)
        );
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        if (click.button() != 0) return super.onMouseDown(click, doubled);

        // click coords are relative to the component origin in owo
        int idx = slotIndexAt((int) (this.x + click.x()), (int) (this.y + click.y()));
        if (idx < 0 || idx >= items.size()) return true;

        var mc = Minecraft.getInstance();
        if (mc.player == null) return true;

        DisplayItem item = items.get(idx);
        boolean shift = hasShiftDown();
        boolean added = HighlightManager.tryHighlight(item, shift);
        if (!added) return true;

        if (Config.get().lookAtTarget() && !shift) lookAt(mc.player, item.pos());
        if (!shift) mc.setScreen(null);
        return true;
    }

    private static boolean hasShiftDown() {
        return Minecraft.getInstance().options.keyShift.isDown();
    }

    private static boolean isCtrlDown() {
        Minecraft mc = Minecraft.getInstance();
        return InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    /**
     * Returns the item index under screen coords (mouseX, mouseY), or -1.
     * Pure integer math — O(1), no iteration.
     */
    private int slotIndexAt(int mouseX, int mouseY) {
        int relX = mouseX - this.x - GAP;
        int relY = mouseY - this.y - GAP;
        if (relX < 0 || relY < 0) return -1;

        int stride = SLOT_SIZE + GAP;
        int col = relX / stride;
        int row = relY / stride;

        // Reject clicks that land in the gap between slots
        if (relX % stride >= SLOT_SIZE) return -1;
        if (relY % stride >= SLOT_SIZE) return -1;
        if (col >= slotsPerRow) return -1;

        int idx = row * slotsPerRow + col;
        return idx < items.size() ? idx : -1;
    }

    private static final int MAX_TOP_SLOTS_SHOWN = 8;

    private void addSlotInfo(List<Component> lines, DisplayItem item) {
        List<IndexedItem> sources = item.sources();
        if (sources.isEmpty()) return;

        Set<Integer> topSlots = new LinkedHashSet<>();
        List<String> nestedPaths = new ArrayList<>();

        for (IndexedItem source : sources) {
            LocatePath path = source.path();
            if (path == null || path.slots().isEmpty()) continue;
            topSlots.add(path.topSlot());
            if (path.slots().size() > 1) {
                nestedPaths.add(path.slots().stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(" → ")));
            }
        }

        if (topSlots.isEmpty()) return;

        List<Integer> topSlotList = new ArrayList<>(topSlots);
        StringBuilder slotsBuilder = new StringBuilder();
        int shown = 0;
        for (int i = 0; i < topSlotList.size() && i < MAX_TOP_SLOTS_SHOWN; i++) {
            if (i > 0) slotsBuilder.append(", ");
            slotsBuilder.append(topSlotList.get(i));
            shown++;
        }
        if (topSlotList.size() > shown) {
            slotsBuilder.append(", ... (").append(topSlotList.size() - shown).append(" 更多)");
        }

        lines.add(Component.translatable("gui.stashlight.label.slots").withStyle(ChatFormatting.GRAY)
                .append(": ")
                .append(Component.literal(slotsBuilder.toString()).withStyle(ChatFormatting.YELLOW)));

        if (isCtrlDown()) {
            for (String nested : nestedPaths) {
                lines.add(Component.literal("  → ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(nested).withStyle(ChatFormatting.DARK_AQUA)));
            }
        }
    }

    private void lookAt(Player player, BlockPos target) {
        double dx = target.getX() + 0.5 - player.getX();
        double dy = target.getY() + 0.5 - player.getEyeY();
        double dz = target.getZ() + 0.5 - player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        player.setYRot((float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f);
        player.setXRot((float) (-(Math.atan2(dy, dist) * 180.0 / Math.PI)));
    }
}
