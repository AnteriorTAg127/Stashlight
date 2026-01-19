package strangequark.chestfinder.gui;

import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;

public class ContainerItemComponent extends StackLayout {

    public static ContainerItemComponent of(ItemStack stack, int count) {
        return new ContainerItemComponent(stack, count);
    }

    protected ContainerItemComponent(ItemStack stack, int count) {
        super(Sizing.fixed(24), Sizing.fixed(24));

        // 1. Use our Custom Component instead of Components.item()
        // This allows us to override the render logic directly on the item.
        NativeItemComponent itemComponent = new NativeItemComponent(stack);

        // 2. Configure it standard owo style
        itemComponent.showOverlay(false).sizing(Sizing.fill(85));

        // 3. Count Label
        ScalableLabelComponent countLabel = ScalableLabelComponent.of(Text.literal(String.valueOf(count)));
        countLabel.positioning(Positioning.relative(90, 90));
        var scale = count > 1000 ? 0.75f : 0.85f;

        // 4. Layout
        this.surface(Surface.flat(0x55888888)).alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        this.child(itemComponent);
        this.child(countLabel.scale(scale));
    }

    /**
     * Inner class that extends ItemComponent to hijack the tooltip rendering.
     * We MUST extend it because the standard one cannot be forced to draw native tooltips easily.
     */
    private static class NativeItemComponent extends ItemComponent {

        protected NativeItemComponent(ItemStack stack) {
            super(stack);

            // TRICK: We give it a "Dummy" tooltip containing 1 empty text line.
            // This forces owo-ui to say "Yes, I should call drawTooltip() on this component".
            this.tooltip(List.of(Text.empty()));
        }

        @Override
        public void drawTooltip(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
            // We IGNORE the dummy text we set above.
            // Instead, we draw the native Minecraft tooltip.
            // This is exactly what ShulkerBoxTooltip hooks into.
            context.drawItemTooltip(
                    MinecraftClient.getInstance().textRenderer,
                    this.stack,
                    mouseX,
                    mouseY
            );
        }
    }
}