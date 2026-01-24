package dev.strangequark.containerlookup.gui;

import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import dev.strangequark.containerlookup.config.Config;
import dev.strangequark.containerlookup.model.IndexedItem;
import dev.strangequark.containerlookup.render.HighlightManager;

import java.util.ArrayList;
import java.util.List;

import static dev.strangequark.containerlookup.gui.UIStyle.SLOT_SIZE;

public class ItemSlot extends StackLayout {
    private final IndexedItem indexedItem;
    private static final Surface BASE_SURFACE = Surface.flat(0x55888888);
    private static final Surface HOVER_SURFACE = Surface.flat(0x44FFFFFF).and(Surface.outline(0xFFFFFFFF));

    public static ItemSlot of(IndexedItem indexedItem) {
        return new ItemSlot(indexedItem);
    }

    protected ItemSlot(IndexedItem indexedItem) {
        super(Sizing.fixed(SLOT_SIZE), Sizing.fixed(SLOT_SIZE));
        this.indexedItem = indexedItem;
        ItemStack stack = indexedItem.stack();

        ItemComponent itemdisplay = Components.item(stack);
        itemdisplay.showOverlay(false).sizing(Sizing.fill(85));

        int count = stack.getCount();
        var scale = count > 999 ? 0.75f : 0.85f;

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
        if (client.player == null || client.world == null) {
            return;
        }

        double dist = Math.sqrt(client.player.getBlockPos().getSquaredDistance(indexedItem.pos()));
        String formattedDist = String.format("%.1f", dist);
        String posStr = String.format("%d, %d, %d", indexedItem.pos().getX(), indexedItem.pos().getY(), indexedItem.pos().getZ());

        // 1. Get Vanilla Lines (Handles Item Name & Data Components)
        List<Text> lines = new ArrayList<>(this.indexedItem.stack().getTooltip(
                Item.TooltipContext.create(client.world.getRegistryManager()),
                client.player,
                client.options.advancedItemTooltips ? TooltipType.Default.ADVANCED : TooltipType.Default.BASIC
        ));

        lines.add(Text.empty()); // Spacer

// Container
        lines.add(Text.translatable("gui.containerlookup.label.container").formatted(Formatting.GRAY).append(": ")
                .append(Text.translatable(indexedItem.containerName()).formatted(Formatting.WHITE)));

// Location
        lines.add(Text.translatable("gui.containerlookup.label.location").formatted(Formatting.GRAY)
                .append(Text.literal(": ").formatted(Formatting.GRAY))
                .append(Text.literal(posStr).formatted(Formatting.AQUA))
                .append(Text.translatable("gui.containerlookup.label.blocksAway", formattedDist).formatted(Formatting.GRAY)));

// Dimension
        lines.add(Text.translatable("gui.containerlookup.label.dimension").formatted(Formatting.GRAY).append(": ")
                .append(Text.literal(indexedItem.dimension()).formatted(Formatting.GREEN)));

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

    @Override
    public boolean onMouseDown(Click click, boolean doubled) {
        if (click.button() != 0) {
            return super.onMouseDown(click, doubled);
        }

        var client = MinecraftClient.getInstance();
        var player = client.player;
        if (player == null) {
            return true;
        }

        boolean highlighted = HighlightManager.tryHighlight(indexedItem);
        if (!highlighted) {
            return true;
        }
        if (Config.get().lookAtTarget()) {
            lookAt(player, indexedItem.pos());
        }
        client.setScreen(null);
        return true;
    }

    private void lookAt(PlayerEntity player, BlockPos target) {
        double d = target.getX() + 0.5 - player.getX();
        double e = target.getY() + 0.5 - player.getEyeY();
        double f = target.getZ() + 0.5 - player.getZ();
        double g = Math.sqrt(d * d + f * f);

        float yaw = (float) (Math.atan2(f, d) * 180.0 / Math.PI) - 90.0f;
        float pitch = (float) (-(Math.atan2(e, g) * 180.0 / Math.PI));

        player.setYaw(yaw);
        player.setPitch(pitch);
    }
}