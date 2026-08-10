package dev.strangequark.stashlight.gui;

import dev.strangequark.stashlight.crafting.CraftCoordinator;
import dev.strangequark.stashlight.crafting.CraftMath;
import dev.strangequark.stashlight.crafting.RecipeCatalog;
import dev.strangequark.stashlight.config.Config;
import dev.strangequark.stashlight.util.Util;
import io.wispforest.owo.ui.base.BaseComponent;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import java.util.ArrayList;
import java.util.List;

import static dev.strangequark.stashlight.gui.UIStyle.*;

/**
 * Detail view for a selected recipe: the crafting grid + result, a quantity
 * input, a live ingredient-consumption table, and the "take & craft" button.
 */
public class RecipeDetailPanel {

    private final FlowLayout root;
    private RecipeDisplayEntry recipe;
    private int quantity = 1;
    private int resultPerCraft = 1;
    private int maxCraftable = 0;

    private TextBoxComponent quantityInput;
    private LabelComponent maxLabel;
    private FlowLayout consumptionRows;
    private ButtonComponent craftButton;

    public RecipeDetailPanel() {
        this.root = (FlowLayout) Containers.horizontalFlow(Sizing.content(), Sizing.content())
                .gap(GAP)
                .surface(Surface.outline(GRID_BORDER))
                .padding(Insets.of(BORDER));
        rebuild();
    }

    public FlowLayout root() {
        return root;
    }

    public boolean hasRecipe() {
        return recipe != null;
    }

    public void setRecipe(RecipeDisplayEntry entry) {
        this.recipe = entry;
        this.quantity = 1;
        this.resultPerCraft = 1;
        this.maxCraftable = 0;
        rebuild();
    }

    private void rebuild() {
        root.clearChildren();
        if (recipe == null) {
            buildEmptyConsole();
            return;
        }

        ItemStack result = RecipeCatalog.resultOf(recipe);
        resultPerCraft = result == null ? 1 : Math.max(1, result.getCount());

        // Left: the crafting preview (3x3 grid + result).
        root.child(new GridCanvas(recipe));

        // Right: the operation column — quantity, max/per-batch, take&craft
        // button and the live ingredient-consumption table. Laying these out
        // beside the preview (not below it) saves vertical space so the recipe
        // list above gets more room.
        FlowLayout ops = (FlowLayout) Containers
                .verticalFlow(Sizing.content(), Sizing.content())
                .gap(GAP);

        FlowLayout qtyRow = (FlowLayout) Containers.horizontalFlow(Sizing.content(), Sizing.fixed(COMPONENT_HEIGHT))
                .gap(GAP)
                .verticalAlignment(io.wispforest.owo.ui.core.VerticalAlignment.CENTER);
        qtyRow.child(Components.label(Component.translatable("gui.stashlight.craft.quantity")).shadow(true));

        this.quantityInput = Components.textBox(Sizing.fixed(60), "1");
        this.quantityInput.setMaxLength(4);
        this.quantityInput.onChanged().subscribe(s -> {
            int parsed = parseQty(s);
            if (parsed > 0) {
                quantity = parsed;
                updateConsumption();
            }
        });
        qtyRow.child(quantityInput);
        ops.child(qtyRow);

        FlowLayout maxRow = (FlowLayout) Containers.horizontalFlow(Sizing.content(), Sizing.fixed(COMPONENT_HEIGHT))
                .gap(GAP)
                .verticalAlignment(io.wispforest.owo.ui.core.VerticalAlignment.CENTER);
        this.maxLabel = (LabelComponent) Components.label(Component.empty()).shadow(true);
        maxRow.child(maxLabel);
        ops.child(maxRow);

        // Take & craft button
        this.craftButton = (ButtonComponent) Components.button(
                Component.translatable("gui.stashlight.craft.takeAndCraft"),
                b -> CraftCoordinator.start(recipe, quantity))
                .sizing(Sizing.fixed(150), Sizing.fixed(COMPONENT_HEIGHT));
        ops.child(craftButton);

        // Consumption rows (stats), below the button inside the ops column.
        this.consumptionRows = (FlowLayout) Containers.verticalFlow(Sizing.content(), Sizing.content()).gap(2);
        ops.child(consumptionRows);

        root.child(ops);
        updateConsumption();
    }

    /**
     * Console skeleton shown before a recipe is selected. The 3x3 grid, result
     * slot, quantity row and take&craft button are always visible so the right
     * panel reads as a complete crafting console instead of a blank strip; only
     * the (disabled) button hints that a recipe must be picked first.
     */
    private void buildEmptyConsole() {
        // Left: empty preview grid; right: disabled operation column.
        root.child(GridCanvas.empty());

        FlowLayout ops = (FlowLayout) Containers
                .verticalFlow(Sizing.content(), Sizing.content())
                .gap(GAP);

        FlowLayout qtyRow = (FlowLayout) Containers.horizontalFlow(Sizing.content(), Sizing.fixed(COMPONENT_HEIGHT))
                .gap(GAP)
                .verticalAlignment(io.wispforest.owo.ui.core.VerticalAlignment.CENTER);
        qtyRow.child(Components.label(Component.translatable("gui.stashlight.craft.quantity")).shadow(true));
        this.quantityInput = Components.textBox(Sizing.fixed(60), "1");
        this.quantityInput.setMaxLength(4);
        qtyRow.child(quantityInput);
        ops.child(qtyRow);

        FlowLayout maxRow = (FlowLayout) Containers.horizontalFlow(Sizing.content(), Sizing.fixed(COMPONENT_HEIGHT))
                .gap(GAP)
                .verticalAlignment(io.wispforest.owo.ui.core.VerticalAlignment.CENTER);
        this.maxLabel = (LabelComponent) Components.label(
                Component.translatable("gui.stashlight.craft.max", 0)).shadow(true);
        maxRow.child(maxLabel);
        ops.child(maxRow);

        this.craftButton = (ButtonComponent) Components.button(
                Component.translatable("gui.stashlight.craft.takeAndCraft"), b -> {})
                .sizing(Sizing.fixed(150), Sizing.fixed(COMPONENT_HEIGHT));
        craftButton.active = false;
        craftButton.tooltip((java.util.List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent>) null);
        ops.child(craftButton);

        this.consumptionRows = (FlowLayout) Containers.verticalFlow(Sizing.content(), Sizing.content()).gap(2);
        consumptionRows.child(Components.label(
                Component.translatable("gui.stashlight.craft.selectRecipe").withStyle(ChatFormatting.GRAY)).shadow(true));
        ops.child(consumptionRows);

        root.child(ops);
    }

    private void updateConsumption() {
        if (recipe == null) return;
        int qty = Math.max(1, quantity);

        // max counts batches; show the equivalent product count. Uses inventory +
        // reachable chests so the "take & craft" flow is not disabled just because
        // materials live in nearby chests.
        this.maxCraftable = RecipeCatalog.maxCraftableInReach(recipe);
        int batches = CraftMath.batchesFor(qty, resultPerCraft);
        boolean insufficient = batches > Math.max(0, maxCraftable);

        // maxCraftable counts batches; show the equivalent product count and the
        // per-batch output so "1 iron block -> 9 ingots" reads as max 9.
        maxLabel.text(Component.translatable("gui.stashlight.craft.max", maxCraftable * resultPerCraft)
                .append(Component.literal("  ").append(
                        Component.translatable("gui.stashlight.craft.perBatch", resultPerCraft)
                                .withStyle(ChatFormatting.GRAY))));
        craftButton.active = !insufficient;
        if (insufficient) {
            craftButton.tooltip(Component.translatable("gui.stashlight.craft.insufficientHint"));
        } else {
            // owo's tooltip(Component) overload NPEs on null; the List overload clears.
            craftButton.tooltip((java.util.List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent>) null);
        }

        consumptionRows.clearChildren();
        for (RecipeCatalog.IngredientGroup group : RecipeCatalog.ingredientGroups(recipe)) {
            int need = CraftMath.needFor(group.perCraft(), batches);
            int have = Util.countInInventory(group.stack());
            int inReach = Util.countInReach(group.stack());
            int deficit = CraftMath.deficit(need, have);

            LabelComponent row = (LabelComponent) Components.label(Component.literal(
                    group.stack().getHoverName().getString()
                            + "  §7" + need
                            + " 背包 " + have
                            + " 范围 " + inReach
                            + " 需取 " + deficit))
                    .shadow(true);
            consumptionRows.child(row);
        }
        if (consumptionRows.children().isEmpty()) {
            consumptionRows.child(Components.label(
                    Component.translatable("gui.stashlight.craft.noIngredients").withStyle(ChatFormatting.GRAY)).shadow(true));
        }
    }

    private static int parseQty(String s) {
        if (s == null || s.isBlank()) return -1;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Draws the recipe's crafting grid cells + the result slot. */
    private static final class GridCanvas extends BaseComponent {
        private final List<ItemStack> cells = new ArrayList<>();
        private final int cols;
        private final int rows;
        private ItemStack result = ItemStack.EMPTY;
        private static final int ARROW_W = 12;

        static GridCanvas empty() {
            GridCanvas canvas = new GridCanvas();
            canvas.horizontalSizing(Sizing.content());
            canvas.verticalSizing(Sizing.content());
            return canvas;
        }

        private GridCanvas() {
            this.cols = 3;
            this.rows = 3;
            this.result = ItemStack.EMPTY;
        }

        GridCanvas(RecipeDisplayEntry entry) {
            var mc = Minecraft.getInstance();
            ContextMap ctx = mc.level != null ? SlotDisplayContext.fromLevel(mc.level) : null;
            RecipeDisplay display = entry.display();
            int w = 0;
            int h = 0;
            if (display instanceof ShapedCraftingRecipeDisplay shaped) {
                w = shaped.width();
                h = shaped.height();
                for (SlotDisplay cell : shaped.ingredients()) {
                    cells.add(firstOf(cell, ctx));
                }
                result = firstOf(shaped.result(), ctx);
            } else if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
                List<SlotDisplay> ings = shapeless.ingredients();
                w = Math.min(3, ings.size());
                h = (int) Math.ceil(ings.size() / 3.0);
                for (SlotDisplay cell : ings) {
                    cells.add(firstOf(cell, ctx));
                }
                result = firstOf(shapeless.result(), ctx);
            }
            this.cols = Math.max(1, w);
            this.rows = Math.max(1, h);
            this.horizontalSizing(Sizing.content());
            this.verticalSizing(Sizing.content());
        }

        private static ItemStack firstOf(SlotDisplay display, ContextMap ctx) {
            if (display == null) return ItemStack.EMPTY;
            List<ItemStack> stacks = display.resolveForStacks(ctx);
            return stacks.isEmpty() ? ItemStack.EMPTY : stacks.get(0);
        }

        @Override
        protected int determineHorizontalContentSize(Sizing sizing) {
            return cols * (SLOT_SIZE + GAP) + GAP + ARROW_W + GAP + SLOT_SIZE;
        }

        @Override
        protected int determineVerticalContentSize(Sizing sizing) {
            return Math.max(rows * (SLOT_SIZE + GAP) + GAP, SLOT_SIZE);
        }

        @Override
        public void draw(OwoUIDrawContext graphics, int mouseX, int mouseY, float partialTicks, float delta) {
            int gridW = cols * (SLOT_SIZE + GAP) + GAP;
            int gridH = rows * (SLOT_SIZE + GAP) + GAP;
            int baseY = this.y + Math.max(0, (gridH - rows * (SLOT_SIZE + GAP) - GAP) / 2);

            for (int i = 0; i < cols * rows; i++) {
                int r = i / cols;
                int c = i % cols;
                int sx = this.x + GAP + c * (SLOT_SIZE + GAP);
                int sy = baseY + GAP + r * (SLOT_SIZE + GAP);
                ItemStack stack = i < cells.size() ? cells.get(i) : ItemStack.EMPTY;
                drawSlot(graphics, sx, sy, stack);
            }

            // Arrow + result
            int arrowX = this.x + gridW + GAP;
            graphics.drawString(Minecraft.getInstance().font, "▶", arrowX, this.y + gridH / 2 - 4, 0xFFFFFFFF);
            int resultX = this.x + gridW + ARROW_W + GAP;
            int resultY = this.y + gridH / 2 - SLOT_SIZE / 2;
            drawSlot(graphics, resultX, resultY, result);

            // Per-craft output count on the result slot: "1 iron ingot -> 9 iron
            // nuggets" shows a ×9 badge so the ratio is not read as 1:1.
            int count = result.getCount();
            if (count > 1) {
                var font = Minecraft.getInstance().font;
                String s = String.valueOf(count);
                float scale = 0.85f;
                graphics.drawText(
                        Component.literal(s),
                        resultX + SLOT_SIZE - font.width(s) * scale - 1,
                        resultY + SLOT_SIZE - font.lineHeight * scale - 1,
                        scale, 0xFFFFFFFF, OwoUIDrawContext.TextAnchor.TOP_LEFT);
            }
        }

        private static void drawSlot(OwoUIDrawContext graphics, int sx, int sy, ItemStack stack) {
            graphics.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, SLOT_BG);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, sx + (SLOT_SIZE - 16) / 2, sy + (SLOT_SIZE - 16) / 2);
            }
        }
    }
}
