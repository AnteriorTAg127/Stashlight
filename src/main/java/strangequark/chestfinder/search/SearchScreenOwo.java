package strangequark.chestfinder.search;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.GridLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import strangequark.chestfinder.gui.ContainerItemComponent;
import strangequark.chestfinder.model.ItemTile;
import strangequark.chestfinder.repository.ContainerRepository;

import java.util.ArrayList;
import java.util.List;

public class SearchScreenOwo extends BaseOwoScreen<FlowLayout> {

    // ==========================================
    //               CONFIGURATION
    // ==========================================

    public static final int COMPONENT_SIZE = 24;
    public static final int GAP_SIZE = 4;
    public static final int SCROLLBAR_WIDTH = 16;
    public static final int PADDING_MAIN = 8;
    public static final int SEARCH_WIDTH = 250;
    public static final int COLOR_BORDER_GRID = 0xFF555555;

    // ==========================================

    private final ContainerRepository repository;
    private ScrollContainer<Component> scrollContainer;
    private FlowLayout scrollContent; // FIX: The inner container that holds the grid
    private TextBoxComponent searchField;

    // Resize Tracking
    private int lastWidth = -1;
    private int lastHeight = -1;

    public SearchScreenOwo(ContainerRepository repository) {
        this.repository = repository;
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        // Track size to detect manual resizing later
        this.lastWidth = this.width;
        this.lastHeight = this.height;

        // --- 1. MAIN WINDOW ---
        FlowLayout mainWindow = Containers.verticalFlow(Sizing.fill(95), Sizing.fill(95));
        mainWindow
                .surface(Surface.VANILLA_TRANSLUCENT)
                .horizontalAlignment(HorizontalAlignment.CENTER)
                .verticalAlignment(VerticalAlignment.TOP)
                .padding(Insets.of(PADDING_MAIN));

        // --- 2. HEADER ---
        LabelComponent title = Components.label(Text.of("Search in Containers"));
        title.shadow(true);
        title.margins(Insets.bottom(5));

        // Restore text logic
        String oldText = (this.searchField != null) ? this.searchField.getText() : "";
        this.searchField = Components.textBox(Sizing.fixed(SEARCH_WIDTH));
        this.searchField.setMaxLength(100);
        this.searchField.setText(oldText);
        this.searchField.onChanged().subscribe(this::refreshGrid);

        // --- 3. SCROLL WRAPPER ---
        FlowLayout gridWrapper = Containers.verticalFlow(Sizing.fill(100), Sizing.expand(100));
        gridWrapper.surface(Surface.outline(COLOR_BORDER_GRID)).padding(Insets.of(1));
        
        this.scrollContent = Containers.verticalFlow(Sizing.content(), Sizing.content());
        this.scrollContent.horizontalAlignment(HorizontalAlignment.CENTER);
        this.scrollContent.padding(Insets.right(4));

        this.scrollContainer = Containers.verticalScroll(
                Sizing.fill(100),
                Sizing.fill(100),
                this.scrollContent
        );
        this.scrollContainer.scrollbarThiccness(8).scrollbar(ScrollContainer.Scrollbar.vanillaFlat());
        gridWrapper.child(this.scrollContainer);

        // --- 4. FOOTER ---
        FlowLayout footer = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        footer.gap(10).verticalAlignment(VerticalAlignment.CENTER);

        footer.child(Components.button(Text.of("Dimension: Current"), b -> {
                })
                .sizing(Sizing.fixed(100), Sizing.fixed(20)));
        footer.child(Components.checkbox(Text.of("Look at target")));

        // --- ASSEMBLE ---
        mainWindow.child(title);
        mainWindow.child(this.searchField);
        mainWindow.child(Containers.verticalFlow(Sizing.fill(), Sizing.fixed(10)));
        mainWindow.child(gridWrapper);
        mainWindow.child(Containers.verticalFlow(Sizing.fill(), Sizing.fixed(5)));
        mainWindow.child(footer);

        rootComponent.child(mainWindow);
        rootComponent.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        refreshGrid(oldText);
    }

    private void refreshGrid(String query) {
        this.scrollContent.clearChildren();

        // Recalculate based on current width
        int windowWidth = (int) (this.width * 0.95);
        int availableWidth = windowWidth - (PADDING_MAIN * 2) - SCROLLBAR_WIDTH - 4;

        // Safety check to prevent division by zero on minimizing
        if (availableWidth < 24) availableWidth = 24;

        int itemFootprint = COMPONENT_SIZE + GAP_SIZE;
        int slotsPerRow = Math.max(1, availableWidth / itemFootprint);

        List<ItemTile> allItems = repository.getTiles();
        List<ItemTile> itemsToShow = new ArrayList<>();
        String lowerQuery = query.toLowerCase();

        for (ItemTile tile : allItems) {
            Identifier id = Identifier.of(tile.itemId());
            if (!Registries.ITEM.containsId(id)) continue;
            if (query.isEmpty() || tile.itemId().contains(lowerQuery)) {
                itemsToShow.add(tile);
            }
        }

        int totalItems = itemsToShow.size();
        int rowsNeeded = (int) Math.ceil((double) totalItems / slotsPerRow);

        GridLayout grid = Containers.grid(
                Sizing.fill(100),
                Sizing.content(),
                rowsNeeded,
                slotsPerRow
        );
        grid.margins(Insets.of(GAP_SIZE / 2));

        for (int i = 0; i < totalItems; i++) {
            ItemTile tile = itemsToShow.get(i);
            Identifier id = Identifier.of(tile.itemId());
            ItemStack stack = new ItemStack(Registries.ITEM.get(id));

            var widget = ContainerItemComponent.of(stack, tile.totalCount());
            widget.margins(Insets.of(GAP_SIZE / 2));

            int row = i / slotsPerRow;
            int col = i % slotsPerRow;
            grid.child(widget, row, col);
        }

        // Add the new grid to our dedicated content layer
        this.scrollContent.child(grid);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.applyBlur();
        super.renderBackground(context, mouseX, mouseY, delta);
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        super.resize(client, width, height);
        this.refreshGrid(this.searchField.getText());
    }
}