package strangequark.chestfinder.screen;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
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
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import strangequark.chestfinder.gui.ItemSlot;
import strangequark.chestfinder.logic.filter.DimensionFilter;
import strangequark.chestfinder.logic.filter.FilterManager;
import strangequark.chestfinder.logic.filter.FilterStrategy;
import strangequark.chestfinder.logic.sort.SortManager;
import strangequark.chestfinder.model.IndexedItem;
import strangequark.chestfinder.repository.ContainerRepository;

import java.util.ArrayList;
import java.util.List;

public class SearchScreen extends BaseOwoScreen<FlowLayout> {

    // --- Tailwind-aligned spacing constants ---
    private static final int SIZE_XXXS = 4;
    private static final int SIZE_XXS = 8;
    private static final int SIZE_XS = 12;
    private static final int SIZE_S = 16;
    private static final int SIZE_M = 24;
    private static final int SIZE_L = 32;
    private static final int SIZE_XL = 40;
    private static final int SIZE_XXL = 48;
    private static final int SIZE_XXXL = 64;

    private static final int COLOR_BORDER_GRID = 0xFF555555;
    private static final int SCROLLBAR_SIZE = SIZE_XXS;
    private static final int SCREEN_FILL_PCT = 95;

    private final ContainerRepository repository;
    private FlowLayout rootComponent;
    private FlowLayout scrollContent;
    private TextBoxComponent searchField;

    private final FilterManager filterManager = new FilterManager();
    private final SortManager sortManager = new SortManager();

    public SearchScreen(ContainerRepository repository) {
        this.repository = repository;
        setupFilters();
    }

    private void setupFilters() {
        List<FilterStrategy> strategies = new ArrayList<>();
        var world = MinecraftClient.getInstance().world;
        String currentDim = null;

        if (world != null) {
            currentDim = world.getRegistryKey().getValue().toString();
            strategies.add(new DimensionFilter("Current", currentDim));
        }

        strategies.add(new DimensionFilter("All", null));

        final String finalCurrentDim = currentDim;
        repository.getContainerEntriesMap().keySet().stream()
                .filter(dim -> !dim.equals(finalCurrentDim))
                .sorted()
                .forEach(dim -> strategies.add(new DimensionFilter(dim, dim)));

        filterManager.setStrategies(strategies);
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void init() {
        super.init();
        if (this.rootComponent.focusHandler() != null && this.searchField.focusHandler() != null) {
            this.rootComponent.focusHandler().focus(this.searchField, Component.FocusSource.MOUSE_CLICK);
        }
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        this.rootComponent = rootComponent;

        // --- 1. MAIN WINDOW ---
        FlowLayout mainWindow = (FlowLayout) Containers
                .verticalFlow(Sizing.fill(SCREEN_FILL_PCT), Sizing.fill(SCREEN_FILL_PCT))
                .gap(SIZE_XXS)
                .surface(Surface.VANILLA_TRANSLUCENT)
                .horizontalAlignment(HorizontalAlignment.CENTER)
                .verticalAlignment(VerticalAlignment.TOP)
                .padding(Insets.of(SIZE_XXS));

        // --- 2. HEADER & SEARCH BAR ---
        LabelComponent title = Components.label(Text.of("Search Containers")).shadow(true);

        FlowLayout searchBar = (FlowLayout) Containers
                .horizontalFlow(Sizing.fill(), Sizing.fixed(SIZE_M))
                .gap(SIZE_XXS)
                .alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        ButtonComponent sortBtn = (ButtonComponent) Components
                .button(Text.of(sortManager.getCurrent().getLabel()), b -> {
                    sortManager.cycle();
                    b.setMessage(Text.of(sortManager.getCurrent().getLabel()));
                    refreshGrid(searchField.getText());
                })
                .sizing(Sizing.fixed(SIZE_XL / 2));

        this.searchField = Components.textBox(Sizing.fixed(SIZE_XXXL * 4));
        this.searchField.setMaxLength(100);
        this.searchField.onChanged().subscribe(this::refreshGrid);

        searchBar.child(sortBtn).child(this.searchField);

        // --- 3. SCROLLABLE GRID ---
        FlowLayout gridWrapper = (FlowLayout) Containers.verticalFlow(Sizing.fill(100), Sizing.expand(100))
                .surface(Surface.outline(COLOR_BORDER_GRID));

        this.scrollContent = (FlowLayout) Containers.verticalFlow(Sizing.content(), Sizing.content())
                .padding(Insets.right(SIZE_XXXS))
                .horizontalAlignment(HorizontalAlignment.CENTER);

        ScrollContainer<FlowLayout> scrollContainer = Containers.verticalScroll(
                        Sizing.fill(100), Sizing.fill(100), this.scrollContent)
                .scrollbarThiccness(SCROLLBAR_SIZE)
                .scrollbar(ScrollContainer.Scrollbar.vanillaFlat());

        gridWrapper.child(scrollContainer);

        // --- 4. FOOTER ---
        FlowLayout footer = (FlowLayout) Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(SIZE_M))
                .gap(SIZE_XXS)
                .verticalAlignment(VerticalAlignment.CENTER);

        ButtonComponent dimFilterBtn = (ButtonComponent) Components.button(
                        Text.of("Dimension: " + filterManager.getCurrent().getLabel()),
                        b -> {
                            filterManager.cycle();
                            b.setMessage(Text.of("Dimension: " + filterManager.getCurrent().getLabel()));
                            refreshGrid(searchField.getText());
                        })
                .sizing(Sizing.fixed(SIZE_XXXL * 2), Sizing.fixed(SIZE_XL / 2));

        var checkbox = Components.checkbox(Text.of("Look at target"));
        checkbox.margins(Insets.top(SIZE_XXXS / 2));

        footer.child(dimFilterBtn).child(checkbox);

        // --- ASSEMBLE ---
        mainWindow.child(title).child(searchBar).child(gridWrapper).child(footer);
        rootComponent.child(mainWindow).alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        refreshGrid("");
    }

    private void refreshGrid(String query) {
        this.scrollContent.clearChildren();

        int windowWidth = (int) (this.width * (SCREEN_FILL_PCT / 100.0));
        // Padding (XXS * 2) + Scrollbar + Gap
        int availableWidth = windowWidth - (SIZE_XXS * 2) - (SCROLLBAR_SIZE * 2) - SIZE_XXS;

        // Slot size (M) + Gap (XXXS)
        int itemFootprint = SIZE_M + SIZE_XXXS;
        int slotsPerRow = Math.max(1, availableWidth / itemFootprint);

        List<IndexedItem> filteredItems = repository.getSearchIndex().stream()
                .filter(item -> {
                    boolean matchesQuery = query.isEmpty() || item.stack().getName().getString().toLowerCase().contains(query.toLowerCase());
                    return matchesQuery && (filterManager.getCurrent() == null || filterManager.getCurrent().matches(item));
                }).toList();

        List<IndexedItem> sortedItems = new ArrayList<>(filteredItems);
        if (sortManager.getCurrent() != null) sortManager.getCurrent().sort(sortedItems);

        int rows = (int) Math.ceil((double) sortedItems.size() / slotsPerRow);
        GridLayout grid = (GridLayout) Containers.grid(Sizing.fill(100), Sizing.content(), rows, slotsPerRow)
                .margins(Insets.of(SIZE_XXXS / 2));

        for (int i = 0; i < sortedItems.size(); i++) {
            grid.child(ItemSlot.of(sortedItems.get(i)).margins(Insets.of(SIZE_XXXS / 2)), i / slotsPerRow, i % slotsPerRow);
        }

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

    @Override
    public boolean shouldPause() {
        return false;
    }
}