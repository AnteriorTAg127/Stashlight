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
        super(Sizing.fixed(16), Sizing.fixed(16));
        
        ItemComponent itemComponent = Components.item(stack);
        ScalableLabelComponent countLabel = ScalableLabelComponent.of(Text.literal(String.valueOf(count))).scale(0.6f);

        countLabel.positioning(Positioning.relative(100, 100));

        this.surface(Surface.flat(0x33000000)).alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        this.child(itemComponent);
        this.child(countLabel);
    }
}
