package strangequark.chestfinder.gui;

import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import strangequark.chestfinder.model.IndexedItem;

import java.util.ArrayList;
import java.util.List;

public class ItemDisplay extends ItemComponent {
    private final IndexedItem indexedItem;

    public ItemDisplay(IndexedItem indexedItem) {
        super(indexedItem.stack());
        this.indexedItem = indexedItem;
        this.tooltip(List.of(Text.empty()));
    }

    @Override
    public void drawTooltip(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
        var client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        // 1. Get Vanilla Lines (Handles Item Name & Data Components)
        List<Text> lines = new ArrayList<>(this.stack.getTooltip(
                Item.TooltipContext.create(client.world.getRegistryManager()),
                client.player,
                client.options.advancedItemTooltips ? TooltipType.Default.ADVANCED : TooltipType.Default.BASIC
        ));

        lines.add(Text.empty()); // Spacer

        // Container: Chest
        // FIX: Using .getString() or just passing the Text object avoids the 'translation{key=...}' bug
        lines.add(Text.literal("Container: ").formatted(Formatting.GRAY)
                .append(Text.translatable(indexedItem.containerName()).formatted(Formatting.WHITE)));

        // Location: 0, 0, 0 (3.5 blocks away)
        double dist = Math.sqrt(client.player.getBlockPos().getSquaredDistance(indexedItem.pos()));
        String posStr = String.format("%d, %d, %d", indexedItem.pos().getX(), indexedItem.pos().getY(), indexedItem.pos().getZ());

        lines.add(Text.literal("Location: ").formatted(Formatting.GRAY)
                .append(Text.literal(posStr).formatted(Formatting.AQUA))
                .append(Text.literal(String.format(" (%.1f blocks away)", dist)).formatted(Formatting.YELLOW)));

        // Dimension: overworld
        lines.add(Text.literal("Dimension: ").formatted(Formatting.GRAY)
                .append(Text.literal(indexedItem.dimension()).formatted(Formatting.LIGHT_PURPLE)));

        // Call the vanilla internal method
        context.drawTooltip(
                client.textRenderer,
                lines,
                this.stack.getTooltipData(),
                mouseX,
                mouseY,
                this.stack.get(DataComponentTypes.TOOLTIP_STYLE)
        );
    }
}