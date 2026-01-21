package strangequark.chestfinder.gui;

import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import strangequark.chestfinder.model.IndexedItem;

import java.util.ArrayList;
import java.util.List;

public class ItemSlot extends StackLayout {
    private final IndexedItem indexedItem;
    private static final Surface BASE_SURFACE = Surface.flat(0x55888888);
    private static final Surface HOVER_SURFACE = Surface.flat(0x44FFFFFF).and(Surface.outline(0xFFFFFFFF));

    public static ItemSlot of(IndexedItem indexedItem) {
        return new ItemSlot(indexedItem);
    }

    protected ItemSlot(IndexedItem indexedItem) {
        super(Sizing.fixed(24), Sizing.fixed(24));
        this.indexedItem = indexedItem;
        ItemStack stack = indexedItem.stack();

        ItemComponent itemdisplay = Components.item(stack);
        itemdisplay.showOverlay(false).sizing(Sizing.fill(85));

        int count = stack.getCount();
        var scale = count > 1000 ? 0.75f : 0.85f;

        QuantityLabel countLabel = QuantityLabel.of(Text.literal(String.valueOf(count)));
        countLabel.positioning(Positioning.relative(90, 90));

        this.surface(BASE_SURFACE).alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        this.child(itemdisplay);
        if (count > 1) {
            this.child(countLabel.scale(scale));
        }
    }

    @Override
    public void draw(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
        boolean isMouseInside = mouseX >= this.x && mouseX <= (this.x + this.width)
                && mouseY >= this.y && mouseY <= (this.y + this.height);

        this.surface(isMouseInside ? HOVER_SURFACE : BASE_SURFACE);
        super.draw(context, mouseX, mouseY, partialTicks, delta);

        if (isMouseInside) {
            this.drawTooltip(context, mouseX, mouseY, partialTicks, delta);
        }
    }

    @Override
    public void drawTooltip(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
        var client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        // 1. Get Vanilla Lines (Handles Item Name & Data Components)
        List<Text> lines = new ArrayList<>(this.indexedItem.stack().getTooltip(
                Item.TooltipContext.create(client.world.getRegistryManager()),
                client.player,
                client.options.advancedItemTooltips ? TooltipType.Default.ADVANCED : TooltipType.Default.BASIC
        ));

        lines.add(Text.empty()); // Spacer

        // Container
        lines.add(Text.literal("Container: ").formatted(Formatting.GRAY)
                .append(Text.translatable(indexedItem.containerName()).formatted(Formatting.WHITE)));

        // Location
        double dist = Math.sqrt(client.player.getBlockPos().getSquaredDistance(indexedItem.pos()));
        String posStr = String.format("%d, %d, %d", indexedItem.pos().getX(), indexedItem.pos().getY(), indexedItem.pos().getZ());

        lines.add(Text.literal("Location: ").formatted(Formatting.GRAY)
                .append(Text.literal(posStr).formatted(Formatting.AQUA))
                .append(Text.literal(String.format(" (%.1f blocks away)", dist)).formatted(Formatting.YELLOW)));

        // Dimension
        lines.add(Text.literal("Dimension: ").formatted(Formatting.GRAY)
                .append(Text.literal(indexedItem.dimension()).formatted(Formatting.LIGHT_PURPLE)));

        // Call the vanilla internal method
        context.drawTooltip(
                client.textRenderer,
                lines,
                this.indexedItem.stack().getTooltipData(),
                mouseX,
                mouseY,
                this.indexedItem.stack().get(DataComponentTypes.TOOLTIP_STYLE)
        );
    }
}