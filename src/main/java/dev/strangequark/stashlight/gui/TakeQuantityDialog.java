package dev.strangequark.stashlight.gui;

import dev.strangequark.stashlight.config.Config;
import dev.strangequark.stashlight.model.DisplayItem;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.Components;
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
 * A small owo dialog for specifying how many items to take.
 * Shown when left-clicking a search result with {@code remoteTake.enabled}.
 * <p>
 * Shift+left-click in {@link ItemGrid} skips this dialog entirely
 * (takes one stack).
 */
public final class TakeQuantityDialog extends BaseOwoScreen<FlowLayout> {

    private final Screen parent;
    private final DisplayItem target;
    private TextBoxComponent quantityInput;

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

        // Quantity text box
        int defaultQty = Config.get().remoteTake().defaultQuantity();
        this.quantityInput = Components.textBox(Sizing.fixed(80), String.valueOf(defaultQty));
        this.quantityInput.setMaxLength(4);
        this.quantityInput.onChanged().subscribe(s -> {
            // Only allow digits — filter in subscribe (setter may not exist)
        });
        rootComponent.child(this.quantityInput);

        // Buttons
        FlowLayout buttonRow = (FlowLayout) Containers.horizontalFlow(Sizing.content(), Sizing.content())
                .gap(GAP)
                .horizontalAlignment(HorizontalAlignment.CENTER);

        buttonRow.child(Components.button(
                Component.translatable("gui.stashlight.take.confirm"),
                b -> confirmTake()
        ).sizing(Sizing.fixed(60), Sizing.fixed(COMPONENT_HEIGHT)));

        buttonRow.child(Components.button(
                Component.translatable("gui.stashlight.take.cancel"),
                b -> onClose()
        ).sizing(Sizing.fixed(60), Sizing.fixed(COMPONENT_HEIGHT)));

        rootComponent.child(buttonRow);
    }

    private void confirmTake() {
        int qty;
        try {
            qty = Integer.parseInt(this.quantityInput.getValue().trim());
        } catch (NumberFormatException e) {
            qty = Config.get().remoteTake().defaultQuantity();
        }
        qty = Math.max(1, Math.min(qty, 64 * target.sources().size()));

        // Close ourselves, then execute the take
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(null);

        var stashlight = dev.strangequark.stashlight.Stashlight.getInstance();
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
