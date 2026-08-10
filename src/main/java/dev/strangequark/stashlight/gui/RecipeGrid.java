package dev.strangequark.stashlight.gui;

import dev.strangequark.stashlight.crafting.RecipeList;
import dev.strangequark.stashlight.model.StackKey;
import io.wispforest.owo.ui.base.BaseComponent;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.PositionedRectangle;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

import static dev.strangequark.stashlight.gui.UIStyle.*;

/**
 * Grid of craftable recipe results. Craftable results get a green outline,
 * unavailable ones are grayed; clicking selects (or cycles recipe variants).
 */
public class RecipeGrid extends BaseComponent {
    private static final int CRAFTABLE_COLOR = 0xFF55FF55;
    private static final int UNCRAFTABLE_COLOR = 0xFF666666;
    private static final int SELECTED_COLOR = 0xFFFFAA00;

    private List<RecipeList> items = Collections.emptyList();
    private Set<StackKey> craftable = Set.of();
    private int slotsPerRow = 1;
    private int selectedIndex = -1;
    private int variantIndex = 0;
    private int hoveredIndex = -1;

    private final BiConsumer<RecipeList, Integer> onSelect;

    public RecipeGrid(BiConsumer<RecipeList, Integer> onSelect) {
        this.onSelect = onSelect;
        this.horizontalSizing(Sizing.content());
        this.verticalSizing(Sizing.content());
    }

    public void setItems(List<RecipeList> newItems, int newSlotsPerRow, Set<StackKey> craftable) {
        this.items = newItems;
        this.slotsPerRow = Math.max(1, newSlotsPerRow);
        this.craftable = craftable;
        if (this.selectedIndex >= items.size()) {
            this.selectedIndex = -1;
            this.variantIndex = 0;
        }
        this.hoveredIndex = -1;
        this.notifyParentIfMounted();
    }

    public int selectedIndex() {
        return selectedIndex;
    }

    /** Select the recipe at {@code index} and fire the selection callback (used for auto-select). */
    public void select(int index) {
        if (index < 0 || index >= items.size()) return;
        selectedIndex = index;
        variantIndex = 0;
        onSelect.accept(items.get(index), 0);
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
        hoveredIndex = slotIndexAt(mouseX, mouseY);

        for (int i = 0; i < items.size(); i++) {
            int row = i / slotsPerRow;
            int col = i % slotsPerRow;
            int slotX = this.x + GAP + col * (SLOT_SIZE + GAP);
            int slotY = this.y + GAP + row * (SLOT_SIZE + GAP);

            if (!graphics.intersectsScissor(PositionedRectangle.of(slotX, slotY, SLOT_SIZE, SLOT_SIZE))) continue;

            boolean hovered = i == hoveredIndex;
            boolean selected = i == selectedIndex;

            graphics.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE,
                    selected ? SELECTED_COLOR : hovered ? SLOT_HOVER : SLOT_BG);
            if (hovered) {
                graphics.drawRectOutline(slotX, slotY, SLOT_SIZE, SLOT_SIZE, SLOT_OUTLINE);
            }

            RecipeList list = items.get(i);
            ItemStack stack = list.result();
            int iconX = slotX + (SLOT_SIZE - 16) / 2;
            int iconY = slotY + (SLOT_SIZE - 16) / 2;
            graphics.renderItem(stack, iconX, iconY);

            if (list.variants().size() > 1) {
                var mc = Minecraft.getInstance();
                String s = String.valueOf(list.variants().size());
                float scale = 0.7f;
                graphics.drawText(
                        Component.literal(s),
                        slotX + SLOT_SIZE - mc.font.width(s) * scale - 1,
                        slotY + SLOT_SIZE - mc.font.lineHeight * scale - 1,
                        scale, 0xFFFFFFAA, OwoUIDrawContext.TextAnchor.TOP_LEFT);
            }

            boolean isCraftable = craftable.contains(new StackKey(stack));
            graphics.drawRectOutline(slotX, slotY, SLOT_SIZE, SLOT_SIZE,
                    isCraftable ? CRAFTABLE_COLOR : UNCRAFTABLE_COLOR);

            if (hovered && Minecraft.getInstance().player != null) {
                List<Component> lines = new java.util.ArrayList<>(stack.getTooltipLines(
                        net.minecraft.world.item.Item.TooltipContext.of(Minecraft.getInstance().level),
                        Minecraft.getInstance().player,
                        Minecraft.getInstance().options.advancedItemTooltips
                                ? net.minecraft.world.item.TooltipFlag.ADVANCED
                                : net.minecraft.world.item.TooltipFlag.NORMAL));
                if (list.variants().size() > 1) {
                    lines.add(Component.translatable("gui.stashlight.craft.multipleRecipes",
                            list.variants().size()).withStyle(ChatFormatting.DARK_GRAY));
                }
                lines.add(Component.translatable(isCraftable
                        ? "gui.stashlight.craft.craftable"
                        : "gui.stashlight.craft.missingMaterials").withStyle(ChatFormatting.DARK_GRAY));
                graphics.setComponentTooltipForNextFrame(Minecraft.getInstance().font, lines, mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        int idx = slotIndexAt((int) (this.x + click.x()), (int) (this.y + click.y()));
        if (idx < 0 || idx >= items.size()) return true;
        if (click.button() != 0) return true;

        RecipeList list = items.get(idx);
        if (idx == selectedIndex && list.variants().size() > 1) {
            variantIndex = (variantIndex + 1) % list.variants().size();
        } else {
            selectedIndex = idx;
            variantIndex = 0;
        }
        onSelect.accept(list, variantIndex);
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
