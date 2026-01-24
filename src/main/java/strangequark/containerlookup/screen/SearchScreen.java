package strangequark.containerlookup.screen;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.*;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.GridLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import strangequark.containerlookup.config.Config;
import strangequark.containerlookup.gui.ItemSlot;
import strangequark.containerlookup.logic.filter.DimensionFilter;
import strangequark.containerlookup.logic.filter.FilterManager;
import strangequark.containerlookup.logic.filter.FilterStrategy;
import strangequark.containerlookup.logic.filter.SmallContainerFilter;
import strangequark.containerlookup.logic.sort.SortManager;
import strangequark.containerlookup.model.IndexedItem;
import strangequark.containerlookup.repository.ContainerRepository;
import strangequark.containerlookup.util.Util;

import java.util.ArrayList;
import java.util.List;

import static strangequark.containerlookup.gui.UIStyle.*;

public class SearchScreen extends BaseOwoScreen<FlowLayout> {

    private final ContainerRepository repository;
    private FlowLayout rootComponent;
    private FlowLayout scrollContent;
    private TextBoxComponent searchField;

    private final FilterManager filterManager = new FilterManager();
    private final SortManager sortManager;

    public SearchScreen(ContainerRepository repository) {
        this.repository = repository;
        this.sortManager = new SortManager(Config.get().sortKey());
        setupFilters();
    }

    private void setupFilters() {
        List<FilterStrategy> strategies = new ArrayList<>();
        var world = MinecraftClient.getInstance().world;
        String currentDim;

        if (world != null) {
            currentDim = Util.getDimensionName(world);
            strategies.add(new DimensionFilter("Current", currentDim));
        }

        strategies.add(new DimensionFilter("All", null));

        repository.getDimensions().forEach(dim -> strategies.add(new DimensionFilter(dim, dim)));

        filterManager.setCyclingStrategies(strategies);
        filterManager.addAlwaysOn(new SmallContainerFilter());
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
            this.searchField.setSelectionStart(0);
            this.searchField.setSelectionEnd(this.searchField.getText().length());
        }
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        this.rootComponent = rootComponent;
        var config = Config.get();

        // --- 1. MAIN WINDOW ---
        FlowLayout mainWindow = (FlowLayout) Containers
                .verticalFlow(Sizing.fill(SCREEN_FILL_PERCENT), Sizing.fill(SCREEN_FILL_PERCENT))
                .gap(GAP)
                .surface(Surface.VANILLA_TRANSLUCENT)
                .horizontalAlignment(HorizontalAlignment.CENTER)
                .verticalAlignment(VerticalAlignment.TOP)
                .padding(Insets.of(PADDING));

        // --- 2. HEADER & SEARCH BAR ---
        LabelComponent title = Components.label(Text.of("Search Containers")).shadow(true);

        FlowLayout searchBar = (FlowLayout) Containers
                .horizontalFlow(Sizing.fill(), Sizing.fixed(COMPONENT_HEIGHT))
                .gap(GAP)
                .alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        ButtonComponent sortBtn = (ButtonComponent) Components
                .button(Text.of(sortManager.getCurrent().getLabel()), b -> {
                    sortManager.cycle();
                    b.setMessage(Text.of(sortManager.getCurrent().getLabel()));
                    b.tooltip(Text.of(sortManager.getCurrent().getTooltip()));
                    config.setSortKey(sortManager.getCurrent().key());
                    refreshGrid(searchField.getText());
                })
                .tooltip(Text.of(sortManager.getCurrent().getTooltip()))
                .sizing(Sizing.fixed(COMPONENT_HEIGHT), Sizing.fixed(COMPONENT_HEIGHT));

        this.searchField = Components.textBox(Sizing.fixed(SEARCH_WIDTH), config.searchQuery());
        this.searchField.setMaxLength(100);
        this.searchField.onChanged().subscribe(text -> {
            config.setSearchQuery(text);
            refreshGrid(text);
        });

        searchBar.child(sortBtn).child(this.searchField);

        // --- 3. SCROLLABLE GRID ---
        FlowLayout gridWrapper = (FlowLayout) Containers.verticalFlow(Sizing.fill(100), Sizing.expand(100))
                .surface(Surface.outline(GRID_BORDER))
                .padding(Insets.of(BORDER));

        this.scrollContent = (FlowLayout) Containers.verticalFlow(Sizing.content(), Sizing.content())
                .padding(Insets.right(GAP))
                .horizontalAlignment(HorizontalAlignment.CENTER);

        ScrollContainer<FlowLayout> scrollContainer = Containers
                .verticalScroll(Sizing.fill(100), Sizing.fill(100), this.scrollContent);

        scrollContainer
                .scrollbarThiccness(SCROLL_WIDTH)
                .scrollbar(ScrollContainer.Scrollbar.vanillaFlat())
                .surface((drawContext, component) -> {
                    int x1 = component.x() + component.width() - SCROLL_WIDTH;
                    int y1 = component.y();
                    int x2 = component.x() + component.width();
                    int y2 = component.y() + component.height();
                    drawContext.fill(x1, y1, x2, y2, SCROLL_TRACK);
                });

        gridWrapper.child(scrollContainer);

        // --- 4. FOOTER ---
        FlowLayout footer = (FlowLayout) Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(COMPONENT_HEIGHT))
                .gap(GAP)
                .verticalAlignment(VerticalAlignment.CENTER);

        ButtonComponent dimFilterBtn = (ButtonComponent) Components.button(
                        Text.of("Dimension: " + filterManager.getCurrentLabel()),
                        b -> {
                            filterManager.cycle();
                            b.setMessage(Text.of("Dimension: " + filterManager.getCurrentLabel()));
                            refreshGrid(searchField.getText());
                        })
                .sizing(Sizing.fixed(FILTER_WIDTH), Sizing.fixed(COMPONENT_HEIGHT));

        CheckboxComponent lookAtCheckbox = (CheckboxComponent) Components
                .checkbox(Text.of("Look at target"))
                .checked(config.lookAtTarget()).onChanged(config::setLookAtTarget)
                .margins(Insets.top(BORDER));

        CheckboxComponent showSmallCheckbox = (CheckboxComponent) Components
                .checkbox(Text.of("Show small containers"))
                .checked(config.showSmallContainers())
                .onChanged(v -> {
                    config.setShowSmallContainers(v);
                    refreshGrid(searchField.getText());
                })
                .margins(Insets.top(BORDER));


        footer.child(dimFilterBtn).child(lookAtCheckbox).child(showSmallCheckbox);

        // --- ASSEMBLE ---
        mainWindow.child(title).child(searchBar).child(gridWrapper).child(footer);
        rootComponent.child(mainWindow).alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        refreshGrid(config.searchQuery());
    }

    private void refreshGrid(String query) {
        this.scrollContent.clearChildren();

        int windowWidth = (int) (this.width * (SCREEN_FILL_PERCENT / 100.0));
        // Total available horizontal space minus padding and scrollbar area
        int availableWidth = windowWidth - (PADDING * 2) - (SCROLL_WIDTH + GAP);

        int itemFootprint = SLOT_SIZE + GAP;
        int slotsPerRow = Math.max(1, availableWidth / itemFootprint);

        List<IndexedItem> filteredItems = repository.getSearchIndex().stream()
                .filter(item -> {
                    boolean matchesQuery = matchesDeep(item.stack(), query);
                    return matchesQuery && filterManager.matches(item);
                }).toList();

        List<IndexedItem> sortedItems = new ArrayList<>(filteredItems);
        if (sortManager.getCurrent() != null) sortManager.getCurrent().sort(sortedItems);

        int rows = (int) Math.ceil((double) sortedItems.size() / slotsPerRow);
        GridLayout grid = (GridLayout) Containers.grid(Sizing.fill(100), Sizing.content(), rows, slotsPerRow)
                .margins(Insets.of(GAP / 2));

        for (int i = 0; i < sortedItems.size(); i++) {
            grid.child(ItemSlot.of(sortedItems.get(i)).margins(Insets.of(GAP / 2)), i / slotsPerRow, i % slotsPerRow);
        }

        this.scrollContent.child(grid);
    }

    public static boolean matchesDeep(ItemStack stack, String query) {
        if (query.isEmpty()) return true;
        String q = query.toLowerCase();

        // 1. Check main item name
        if (stack.getName().getString().toLowerCase().contains(q)) return true;

        // 2. Check Shulker-like containers
        var container = stack.get(DataComponentTypes.CONTAINER);
        if (container != null) {
            for (ItemStack inner : container.iterateNonEmpty()) {
                if (inner.getName().getString().toLowerCase().contains(q)) return true;
            }
        }

        // 3. Check Bundles
        var bundle = stack.get(DataComponentTypes.BUNDLE_CONTENTS);
        if (bundle != null) {
            for (ItemStack inner : bundle.iterate()) {
                if (inner.getName().getString().toLowerCase().contains(q)) return true;
            }
        }

        return false;
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