package strangequark.chestfinder.search;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import strangequark.chestfinder.model.ItemTile;
import strangequark.chestfinder.repository.ContainerRepository_OLD;

import java.util.List;

public class SearchScreen extends Screen {
    private TextFieldWidget searchField;
    private final ContainerRepository_OLD repository;

    public SearchScreen(ContainerRepository_OLD repository) {
        super(Text.of("Chest Finder"));
        this.repository = repository;
    }

    @Override
    protected void init() {
        searchField = new TextFieldWidget(textRenderer, (width - 200) / 2, 40, 200, 20, Text.of(""));
        addSelectableChild(searchField);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        context.fill(0, 0, width, height, 0x88000000);
        searchField.render(context, mouseX, mouseY, deltaTicks);
        setFocused(searchField);
        drawCenteredText(context, textRenderer, "Search", 20, 0xFFFFFFFF);
        List<ItemTile> tiles = repository.getTiles();
        for (int i = 0; i < tiles.size(); i++) {
            ItemTile tile = tiles.get(i);
            Identifier id = Identifier.ofVanilla(tile.itemId());
            Item item = Registries.ITEM.get(id);
            ItemStack stack = new ItemStack(item);
            int x = 50;
            int y = 30 + i * 20;
            context.drawItem(stack, x, y);
            context.drawStackOverlay(textRenderer, stack, x, y, String.valueOf(tile.totalCount()));
        }
        super.render(context, mouseX, mouseY, deltaTicks);
    }

    private void drawCenteredText(DrawContext ctx, TextRenderer tr, String s, int y, int color) {
        int w = tr.getWidth(s);
        int x = (width - w) / 2;
        ctx.drawText(tr, s, x, y, color, false);
    }
}