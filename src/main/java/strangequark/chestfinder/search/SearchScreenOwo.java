package strangequark.chestfinder.search;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.GridLayout;
import io.wispforest.owo.ui.core.*;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import strangequark.chestfinder.gui.ContainerItemComponent;
import strangequark.chestfinder.repository.ContainerRepository;

public class SearchScreenOwo extends BaseOwoScreen<FlowLayout> {
    private final ContainerRepository repository;

    public SearchScreenOwo(ContainerRepository repository) {
        this.repository = repository;
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        final int MAX_COLUMNS = 16;
        FlowLayout main = Containers.verticalFlow(Sizing.fill(), Sizing.fill());

        main.surface(Surface.VANILLA_TRANSLUCENT)
                .horizontalAlignment(HorizontalAlignment.LEFT)
                .verticalAlignment(VerticalAlignment.TOP)
                .padding(Insets.of(16));


        FlowLayout searchContainer = Containers.verticalFlow(Sizing.fill(), Sizing.content());
        TextBoxComponent search = Components.textBox(Sizing.fixed(200));

        GridLayout gridContainer = Containers.grid(Sizing.fill(), Sizing.fill(), MAX_COLUMNS, MAX_COLUMNS);

        var items = repository.getTiles();


        int row;
        int col;

        for (int i = 0; i < items.size(); i++) {
            var item = items.get(i);
            var stack = new ItemStack(net.minecraft.registry.Registries.ITEM.get(net.minecraft.util.Identifier.ofVanilla(item.itemId())));

            ContainerItemComponent itemResult = ContainerItemComponent.of(stack, item.totalCount());

            col = i % MAX_COLUMNS;
            row = i / MAX_COLUMNS;

            gridContainer.child(itemResult, row, col);
        }


        searchContainer.alignment(HorizontalAlignment.CENTER, VerticalAlignment.TOP);
        gridContainer.surface(Surface.outline(0x220000FF)).margins(Insets.horizontal(128));


        searchContainer.child(search);
        main.gap(16).child(searchContainer).child(Containers.verticalScroll(Sizing.fill(), Sizing.fixed(200), gridContainer));

        rootComponent.child(main);
    }
}