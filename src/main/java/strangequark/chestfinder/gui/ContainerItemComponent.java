package strangequark.chestfinder.gui;

import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.core.*;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

public class ContainerItemComponent extends StackLayout {

    public static ContainerItemComponent of(ItemStack stack, int count) {
        return new ContainerItemComponent(stack, count);
    }

    protected ContainerItemComponent(ItemStack stack, int count) {
        super(Sizing.fixed(24), Sizing.fixed(24));

        ItemComponent itemComponent = (ItemComponent) Components.item(stack).setTooltipFromStack(true).showOverlay(true).sizing(Sizing.fill(80));
        ScalableLabelComponent countLabel = ScalableLabelComponent.of(Text.literal(String.valueOf(count))).scale(0.75f);

        countLabel.positioning(Positioning.relative(90, 90));

        this.surface(Surface.flat(0x55888888)).alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        this.child(itemComponent);
        if (count > 1) {
            this.child(countLabel);
        }
    }
}
