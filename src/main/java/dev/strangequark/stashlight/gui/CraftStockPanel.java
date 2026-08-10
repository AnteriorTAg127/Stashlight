package dev.strangequark.stashlight.gui;

import dev.strangequark.stashlight.Stashlight;
import dev.strangequark.stashlight.model.DisplayItem;
import dev.strangequark.stashlight.model.IndexedItem;
import dev.strangequark.stashlight.model.LocatePath;
import dev.strangequark.stashlight.model.StackKey;
import dev.strangequark.stashlight.repository.ContainerRepository;
import dev.strangequark.stashlight.util.Util;
import io.wispforest.owo.ui.base.BaseComponent;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.PositionedRectangle;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static dev.strangequark.stashlight.gui.UIStyle.*;

/**
 * The craft page's "nearby stock" panel (top-left): every item currently
 * reachable in the repository (player-touchable chests), aggregated by item +
 * components and shown as a slot grid with counts. Clicking an item selects
 * the recipe that uses it as an ingredient (auto-filling the 3x3 grid), or the
 * recipe that produces it as a fallback.
 */
public class CraftStockPanel extends BaseComponent {

    private static final int SELECTED_COLOR = 0xFF33FF33;

    private final BiConsumer<DisplayItem, Integer> onSelect;
    private List<DisplayItem> items = List.of();
    private int slotsPerRow = 8;
    private int hoveredIndex = -1;
    private StackKey selected = null;

    public CraftStockPanel(BiConsumer<DisplayItem, Integer> onSelect) {
        this.onSelect = onSelect;
        this.horizontalSizing(Sizing.content());
        this.verticalSizing(Sizing.content());
    }

    /** Re-aggregate the reachable chest items (called when the craft page refreshes). */
    public void refresh() {
        var stashlight = Stashlight.getInstance();
        ContainerRepository repository = stashlight != null ? stashlight.getRepository() : null;
        items = aggregate(Util.reachableItems(repository));
        if (selected != null && items.stream().noneMatch(i -> new StackKey(i.stack()).equals(selected))) {
            selected = null;
        }
        this.notifyParentIfMounted();
    }

    private static List<DisplayItem> aggregate(List<IndexedItem> source) {
        Map<StackKey, List<IndexedItem>> groups = new LinkedHashMap<>();
        for (IndexedItem item : source) {
            groups.computeIfAbsent(new StackKey(item.stack()), k -> new ArrayList<>()).add(item);
        }

        List<DisplayItem> result = new ArrayList<>();
        for (var group : groups.values()) {
            // Same container indexed both locally and by a server scan: dedupe by
            // locate path so totals are not doubled.
            Map<LocatePath, IndexedItem> dedup = new LinkedHashMap<>();
            for (IndexedItem i : group) {
                LocatePath key = i.path() != null ? i.path() : new LocatePath(-1);
                IndexedItem existing = dedup.get(key);
                if (existing == null || i.timestamp() > existing.timestamp()) {
                    dedup.put(key, i);
                }
            }
            List<IndexedItem> unique = new ArrayList<>(dedup.values());
            IndexedItem first = unique.get(0);
            ItemStack merged = first.stack().copy();
            merged.setCount(unique.stream().mapToInt(i -> i.stack().getCount()).sum());
            result.add(new DisplayItem(merged, unique));
        }
        return result;
    }

    public void setSlotsPerRow(int newSlotsPerRow) {
        this.slotsPerRow = Math.max(1, newSlotsPerRow);
        this.notifyParentIfMounted();
    }

    public int itemCount() {
        return items.size();
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
        if (items.isEmpty()) {
            Component hint = Component.translatable("gui.stashlight.craft.stockEmpty");
            graphics.drawText(hint, this.x + GAP, this.y + GAP, 1.0f,
                    0xFF888888, OwoUIDrawContext.TextAnchor.TOP_LEFT);
            return;
        }

        hoveredIndex = slotIndexAt(mouseX, mouseY);
        var mc = Minecraft.getInstance();

        for (int i = 0; i < items.size(); i++) {
            int row = i / slotsPerRow;
            int col = i % slotsPerRow;
            int slotX = this.x + GAP + col * (SLOT_SIZE + GAP);
            int slotY = this.y + GAP + row * (SLOT_SIZE + GAP);

            if (!graphics.intersectsScissor(PositionedRectangle.of(slotX, slotY, SLOT_SIZE, SLOT_SIZE))) continue;

            boolean hovered = i == hoveredIndex;
            boolean isSelected = selected != null && new StackKey(items.get(i).stack()).equals(selected);

            graphics.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE,
                    hovered ? SLOT_HOVER : SLOT_BG);
            if (hovered) {
                graphics.drawRectOutline(slotX, slotY, SLOT_SIZE, SLOT_SIZE, SLOT_OUTLINE);
            }
            if (isSelected) {
                graphics.drawRectOutline(slotX - 1, slotY - 1, SLOT_SIZE + 2, SLOT_SIZE + 2, SELECTED_COLOR);
            }

            ItemStack stack = items.get(i).stack();
            graphics.renderItem(stack, slotX + (SLOT_SIZE - 16) / 2, slotY + (SLOT_SIZE - 16) / 2);

            int count = stack.getCount();
            if (count > 1) {
                String countStr = String.valueOf(count);
                float scale = count > 999 ? 0.7f : 0.85f;
                graphics.drawText(
                        Component.literal(countStr),
                        slotX + SLOT_SIZE - mc.font.width(countStr) * scale - 1,
                        slotY + SLOT_SIZE - mc.font.lineHeight * scale - 1,
                        scale, 0xFFFFFFFF, OwoUIDrawContext.TextAnchor.TOP_LEFT);
            }
        }

        if (hoveredIndex >= 0 && mc.player != null) {
            ItemStack stack = items.get(hoveredIndex).stack();
            List<Component> lines = new ArrayList<>(stack.getTooltipLines(
                    net.minecraft.world.item.Item.TooltipContext.of(mc.level),
                    mc.player,
                    mc.options.advancedItemTooltips
                            ? net.minecraft.world.item.TooltipFlag.ADVANCED
                            : net.minecraft.world.item.TooltipFlag.NORMAL));
            lines.add(Component.translatable("gui.stashlight.craft.stockClickHint")
                    .withStyle(ChatFormatting.DARK_GRAY));
            graphics.setComponentTooltipForNextFrame(mc.font, lines, mouseX, mouseY);
        }
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        int idx = slotIndexAt((int) (this.x + click.x()), (int) (this.y + click.y()));
        if (click.button() != 0 || idx < 0 || idx >= items.size()) return true;

        selected = new StackKey(items.get(idx).stack());
        onSelect.accept(items.get(idx), idx);
        return true;
    }

    private int slotIndexAt(int mouseX, int mouseY) {
        int relX = mouseX - this.x - GAP;
        int relY = mouseY - this.y - GAP;
        if (relX < 0 || relY < 0) return -1;

        int stride = SLOT_SIZE + GAP;
        int col = relX / stride;
        int row = relY / stride;

        if (relX % stride >= SLOT_SIZE) return -1;
        if (relY % stride >= SLOT_SIZE) return -1;
        if (col >= slotsPerRow) return -1;

        int idx = row * slotsPerRow + col;
        return idx < items.size() ? idx : -1;
    }
}
