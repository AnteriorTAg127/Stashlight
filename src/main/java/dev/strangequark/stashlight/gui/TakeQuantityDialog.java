package dev.strangequark.stashlight.gui;

import dev.strangequark.stashlight.config.Config;
import dev.strangequark.stashlight.model.DisplayItem;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import static dev.strangequark.stashlight.gui.UIStyle.*;

/**
 * Quantity picker dialog. Shows a text input + quick buttons (+64/-64/+16/-16/+1/-1)
 * that all stay in sync. Changes propagate bidirectionally: editing the text updates
 * the internal value, clicking buttons updates both text and value.
 */
public final class TakeQuantityDialog extends BaseOwoScreen<FlowLayout> {

    private final Screen parent;
    private final DisplayItem target;
    private TextBoxComponent quantityInput;
    private LabelComponent qtyLabel;
    private int quantity;
    private int maxQty;

    public TakeQuantityDialog(Screen parent, DisplayItem target) {
        this.parent = parent;
        this.target = target;
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        rootComponent.gap(GAP);
        rootComponent.horizontalAlignment(HorizontalAlignment.CENTER);
        rootComponent.verticalAlignment(VerticalAlignment.CENTER);
        rootComponent.padding(Insets.of(PADDING));
        rootComponent.surface(Surface.VANILLA_TRANSLUCENT);

        // Title
        rootComponent.child(Components.label(
                Component.translatable("gui.stashlight.take.title", target.stack().getHoverName())
        ).shadow(true));

        // Maximum available
        maxQty = target.sources().stream().mapToInt(s -> s.stack().getCount()).sum();
        if (maxQty < 1) maxQty = 1;
        this.quantity = Math.min(Config.get().remoteTake().defaultQuantity(), maxQty);

        // Quantity input row: label + textbox + "/ max"
        FlowLayout inputRow = (FlowLayout) Containers.horizontalFlow(Sizing.content(), Sizing.content())
                .gap(GAP)
                .horizontalAlignment(HorizontalAlignment.CENTER)
                .verticalAlignment(VerticalAlignment.CENTER);

        inputRow.child(Components.label(Component.translatable("gui.stashlight.label.quantity")).shadow(true));

        this.quantityInput = Components.textBox(Sizing.fixed(60), String.valueOf(quantity));
        this.quantityInput.setMaxLength(4);
        this.quantityInput.onChanged().subscribe(s -> {
            int parsed = parseQty(s);
            if (parsed > 0) {
                quantity = Math.min(parsed, maxQty);
                updateDisplays();
            }
        });
        inputRow.child(quantityInput);

        qtyLabel = (LabelComponent) Components.label(
                Component.literal("/ " + maxQty)
        ).shadow(true);
        inputRow.child(qtyLabel);
        rootComponent.child(inputRow);

        // Quick quantity buttons
        FlowLayout btnRow1 = (FlowLayout) Containers.horizontalFlow(Sizing.content(), Sizing.content())
                .gap(GAP)
                .horizontalAlignment(HorizontalAlignment.CENTER);

        addQtyButton(btnRow1, "+64", 64);
        addQtyButton(btnRow1, "+16", 16);
        addQtyButton(btnRow1, "+1", 1);

        FlowLayout btnRow2 = (FlowLayout) Containers.horizontalFlow(Sizing.content(), Sizing.content())
                .gap(GAP)
                .horizontalAlignment(HorizontalAlignment.CENTER);

        addQtyButton(btnRow2, "-1", -1);
        addQtyButton(btnRow2, "-16", -16);
        addQtyButton(btnRow2, "-64", -64);

        rootComponent.child(btnRow1);
        rootComponent.child(btnRow2);

        // Confirm / Cancel
        FlowLayout actionRow = (FlowLayout) Containers.horizontalFlow(Sizing.content(), Sizing.content())
                .gap(GAP)
                .horizontalAlignment(HorizontalAlignment.CENTER);

        actionRow.child(Components.button(
                Component.translatable("gui.stashlight.take.confirm"),
                b -> confirmTake()
        ).sizing(Sizing.fixed(60), Sizing.fixed(COMPONENT_HEIGHT)));

        actionRow.child(Components.button(
                Component.translatable("gui.stashlight.take.cancel"),
                b -> onClose()
        ).sizing(Sizing.fixed(60), Sizing.fixed(COMPONENT_HEIGHT)));

        rootComponent.child(actionRow);
    }

    private void addQtyButton(FlowLayout row, String label, int delta) {
        ButtonComponent btn = (ButtonComponent) Components.button(Component.literal(label), b -> {
            quantity = Math.max(1, Math.min(maxQty, quantity + delta));
            updateDisplays();
        }).sizing(Sizing.fixed(40), Sizing.fixed(COMPONENT_HEIGHT));
        row.child(btn);
    }

    /**
     * Sync both the text box and the label to the current quantity.
     */
    private void updateDisplays() {
        quantityInput.text(String.valueOf(quantity));
        quantityInput.moveCursorToEnd(false);
        qtyLabel.text(Component.literal("/ " + maxQty));
    }

    private static int parseQty(String s) {
        if (s == null || s.isBlank()) return -1;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void confirmTake() {
        int qty = Math.max(1, Math.min(quantity, maxQty));

        Minecraft mc = Minecraft.getInstance();
        var stashlight = dev.strangequark.stashlight.Stashlight.getInstance();
        boolean keepScreen = Config.get().remoteTake().keepScreenOnTake();
        boolean modded = stashlight != null && stashlight.isModdedTakeAvailable();
        // Keep search screen visible for modded take; vanilla-fallback closes
        // (its state machine reopens the search screen on completion).
        if (keepScreen && modded) {
            mc.setScreen(parent);
        } else {
            mc.setScreen(null);
        }

        if (stashlight != null) {
            stashlight.getTakeClient().startTake(target, qty);
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
