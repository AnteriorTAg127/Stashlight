package strangequark.chestfinder.gui;

import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.core.*;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import strangequark.chestfinder.model.IndexedItem;

public class ItemSlot extends StackLayout {
    private static final Surface BASE_SURFACE = Surface.flat(0x55888888);
    private static final Surface HOVER_SURFACE = Surface.flat(0x44FFFFFF).and(Surface.outline(0xFFFFFFFF));

    public static ItemSlot of(IndexedItem indexedItem) {
        return new ItemSlot(indexedItem);
    }

    protected ItemSlot(IndexedItem indexedItem) {
        super(Sizing.fixed(24), Sizing.fixed(24));

        ItemStack stack = indexedItem.stack();

        ItemDisplay itemdisplay = new ItemDisplay(indexedItem);
        itemdisplay.showOverlay(false).sizing(Sizing.fill(85));

        int count = stack.getCount();
        var scale = count > 1000 ? 0.75f : 0.85f;
        
        QuantityLabel countLabel = QuantityLabel.of(Text.literal(String.valueOf(count)));
        countLabel.positioning(Positioning.relative(90, 90));


        this.surface(BASE_SURFACE).alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        this.mouseEnter().subscribe(() -> {
            this.surface(HOVER_SURFACE);
        });

        this.mouseLeave().subscribe(() -> {
            this.surface(BASE_SURFACE);
        });

        this.child(itemdisplay);
        this.child(countLabel.scale(scale));
    }
}