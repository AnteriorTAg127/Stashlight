package dev.strangequark.stashlight.screen;

import dev.strangequark.stashlight.Stashlight;
import dev.strangequark.stashlight.config.Config;
import dev.strangequark.stashlight.crafting.RecipeCatalog;
import dev.strangequark.stashlight.crafting.RecipeList;
import dev.strangequark.stashlight.crafting.Station;
import dev.strangequark.stashlight.gui.CraftStockPanel;
import dev.strangequark.stashlight.gui.EnchantFilterPanel;
import dev.strangequark.stashlight.gui.InventoryBar;
import dev.strangequark.stashlight.gui.ItemGrid;
import dev.strangequark.stashlight.gui.NestedContainerPreview;
import dev.strangequark.stashlight.gui.RecipeDetailPanel;
import dev.strangequark.stashlight.gui.RecipeGrid;
import dev.strangequark.stashlight.gui.TakeQueuePanel;
import dev.strangequark.stashlight.logic.filter.*;
import dev.strangequark.stashlight.logic.sort.SortManager;
import dev.strangequark.stashlight.model.DataSourceMode;
import dev.strangequark.stashlight.model.DisplayItem;
import dev.strangequark.stashlight.model.EnchantEntry;
import dev.strangequark.stashlight.model.IndexedItem;
import dev.strangequark.stashlight.model.StackKey;
import dev.strangequark.stashlight.render.HighlightManager;
import dev.strangequark.stashlight.repository.ContainerRepository;
import dev.strangequark.stashlight.scan.VanillaTaker;
import dev.strangequark.stashlight.util.Util;
import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.*;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static dev.strangequark.stashlight.gui.UIStyle.*;

public class SearchScreen extends BaseOwoScreen<FlowLayout> {
    private final ContainerRepository repository;
    private FlowLayout rootComponent;
    private FlowLayout mainWindow;
    private FlowLayout searchBar;
    private FlowLayout rightColumn;
    private FlowLayout bottomRow;
    private TextBoxComponent searchField;
    private ItemGrid itemGrid;
    private InventoryBar inventoryBar;
    private TakeQueuePanel queuePanel;

    private final FilterManager filterManager = new FilterManager();
    private final SortManager sortManager;
    private final EnchantFilterState enchantState = new EnchantFilterState();
    private EnchantFilterPanel enchantPanel;
    private FlowLayout enchantPanelWrapper;
    private ScrollContainer<FlowLayout> enchantScroll;
    private FlowLayout modeTabs;
    private final Map<SearchMode, ButtonComponent> tabButtons = new EnumMap<>(SearchMode.class);
    private final Map<SearchPage, ButtonComponent> pageButtons = new EnumMap<>(SearchPage.class);
    private FlowLayout pageBar;
    private ButtonComponent logicButton;
    private ButtonComponent sourceButton;
    private ButtonComponent refreshButton;
    private LabelComponent syncTimeLabel;
    private ReachableTakeFilter takeableReachableFilter;

    private SearchPage page = SearchPage.SEARCH;
    private SearchMode mode;
    private DataSourceMode dataSourceMode;

    // Crafting (CRAFT page)
    private final RecipeCatalog recipeCatalog;
    private RecipeGrid recipeGrid;
    private RecipeDetailPanel detailPanel;
    private CraftStockPanel craftStockPanel;
    private ScrollContainer<FlowLayout> itemScroll;
    private ScrollContainer<FlowLayout> recipeGridScroll;
    private ScrollContainer<FlowLayout> stockScroll;
    private FlowLayout craftContent;
    private FlowLayout gridWrapper;
    private InventoryBar craftInventoryBar;
    private TextBoxComponent craftSearchField;
    private CheckboxComponent craftCraftableCheckbox;
    private String craftQuery = "";
    private boolean craftCraftableOnly;
    private int craftCheckTicks = 0;

    /** Number of slot columns of the nearby-stock panel (fixed width, avoids fill/content cycles). */
    private static final int STOCK_COLS = 8;

    // Footer: the settings row (search-radius slider + checkboxes) is collapsible
    private FlowLayout footer;
    private FlowLayout footerRow1;
    private ButtonComponent settingsToggleButton;
    private boolean settingsExpanded = false;

    private static final long DEBOUNCE_MS = 150;
    private String pendingQuery = null;
    private long lastQueryChangeTime = 0;

    private List<IndexedItem> lastFilteredItems = new ArrayList<>();
    private boolean scanRequested = false;

    public SearchScreen(ContainerRepository repository) {
        this(repository, SearchPage.SEARCH);
    }

    public SearchScreen(ContainerRepository repository, SearchPage initialPage) {
        this.repository = repository;
        this.page = initialPage;
        this.mode = SearchMode.ITEM;
        this.sortManager = new SortManager(Config.get().sortKey());
        this.dataSourceMode = Config.get().dataSource().mode();
        this.recipeCatalog = new RecipeCatalog(repository);
        // "Show craftable only" mirrors Config.crafting.showAllRecipes (inverted);
        // the craft-page checkbox and the settings screen stay in sync.
        this.craftCraftableOnly = !Config.get().crafting().showAllRecipes();
        setupFilters();
    }

    private void setupFilters() {
        List<FilterStrategy> strategies = new ArrayList<>();
        var world = Minecraft.getInstance().level;
        String currentDim;

        if (world != null) {
            currentDim = Util.getDimensionName(world);
            strategies.add(new DimensionFilter(Component.translatable("gui.stashlight.label.dimensionCurrent"), currentDim));
        }

        strategies.add(new DimensionFilter(Component.translatable("gui.stashlight.label.dimensionAll"), null));

        repository.getDimensions().forEach(dim -> strategies.add(new DimensionFilter(Component.literal(dim), dim)));

        filterManager.setCyclingStrategies(strategies);
        filterManager.addAlwaysOn(new SmallContainerFilter());
        filterManager.addAlwaysOn(new RadiusFilter());
        takeableReachableFilter = new ReachableTakeFilter();
        filterManager.addAlwaysOn(takeableReachableFilter);
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void init() {
        super.init();
        if (this.rootComponent.focusHandler() != null && this.searchField.focusHandler() != null) {
            this.rootComponent.focusHandler().focus(this.searchField, io.wispforest.owo.ui.core.Component.FocusSource.MOUSE_CLICK);
            this.searchField.setHighlightPos(0);
            this.searchField.moveCursorToEnd(false);
        }
        this.rootComponent.queue(() -> this.refreshGrid(this.searchField.getValue()));
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        this.rootComponent = rootComponent;
        var config = Config.get();

        // ── 1. MAIN WINDOW ──────────────────────────────────────────────
        // A vertical 95%×95% surface, top-aligned: the top search bar, the
        // content area (which is swapped between the SEARCH and CRAFT pages),
        // and a thin footer strip pinned to the bottom.
        this.mainWindow = (FlowLayout) Containers
                .verticalFlow(Sizing.fill(SCREEN_FILL_PERCENT), Sizing.fill(SCREEN_FILL_PERCENT))
                .gap(GAP)
                .surface(Surface.VANILLA_TRANSLUCENT)
                .horizontalAlignment(HorizontalAlignment.CENTER)
                .verticalAlignment(VerticalAlignment.TOP)
                .padding(Insets.of(PADDING));

        LabelComponent title = Components.label(Component.translatable("screen.stashlight.label.searchContainers")).shadow(true);

        // ── 2. TOP SEARCH BAR (SEARCH page only) ────────────────────────
        this.searchBar = (FlowLayout) Containers
                .horizontalFlow(Sizing.fill(), Sizing.fixed(COMPONENT_HEIGHT))
                .gap(GAP)
                .alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        this.modeTabs = (FlowLayout) Containers
                .horizontalFlow(Sizing.content(), Sizing.fixed(COMPONENT_HEIGHT))
                .gap(GAP)
                .verticalAlignment(VerticalAlignment.CENTER);

        for (SearchMode tab : List.of(SearchMode.ITEM, SearchMode.ENCHANT)) {
            ButtonComponent tabButton = (ButtonComponent) Components
                    .button(Component.empty(), b -> setMode(tab))
                    .sizing(Sizing.fixed(COMPONENT_HEIGHT), Sizing.fixed(COMPONENT_HEIGHT));
            this.tabButtons.put(tab, tabButton);
            this.modeTabs.child(tabButton);
        }

        ButtonComponent sortBtn = (ButtonComponent) Components
                .button(Component.literal(sortManager.getCurrent().getLabel()), b -> {
                    sortManager.cycle();
                    b.setMessage(Component.literal(sortManager.getCurrent().getLabel()));
                    b.tooltip(sortManager.getCurrent().getTooltip());
                    config.setSortKey(sortManager.getCurrent().key());
                    refreshGrid(searchField.getValue());
                })
                .tooltip(sortManager.getCurrent().getTooltip())
                .sizing(Sizing.fixed(COMPONENT_HEIGHT), Sizing.fixed(COMPONENT_HEIGHT));

        this.searchField = Components.textBox(Sizing.fixed(SEARCH_WIDTH), config.searchQuery());
        this.searchField.setMaxLength(100);
        this.searchField.onChanged().subscribe(text -> {
            config.setSearchQuery(text);
            boolean wantsEnchant = text.toLowerCase().startsWith("@ench:");
            if (wantsEnchant && mode != SearchMode.ENCHANT) {
                mode = SearchMode.ENCHANT;
                enchantState.clear();
                enchantState.apply(EnchantQueryParser.parse(text, collectAvailableEnchants()));
                updateModeUi();
            } else if (!wantsEnchant && mode != SearchMode.ITEM) {
                mode = SearchMode.ITEM;
                enchantState.clear();
                updateModeUi();
            }
            // Don't rebuild immediately — record the change and let the debounce
            // in render() fire refreshGrid once typing has settled.
            this.pendingQuery = text;
            this.lastQueryChangeTime = System.currentTimeMillis();
        });

        ButtonComponent dimFilterBtn = (ButtonComponent) Components.button(
                        Component.translatable("gui.stashlight.label.dimensionWithValue", filterManager.getCurrentLabel()),
                        b -> {
                            filterManager.cycle();
                            b.setMessage(Component.translatable("gui.stashlight.label.dimensionWithValue", filterManager.getCurrentLabel()));
                            refreshGrid(searchField.getValue());
                        })
                .sizing(Sizing.fixed(FILTER_WIDTH), Sizing.fixed(COMPONENT_HEIGHT));

        this.logicButton = (ButtonComponent) Components
                .button(Component.translatable("gui.stashlight.logic.and"), b -> toggleLogicMode())
                .sizing(Sizing.fixed(LOGIC_BUTTON_WIDTH), Sizing.fixed(COMPONENT_HEIGHT));

        ButtonComponent highlightAllBtn = (ButtonComponent) Components
                .button(Component.translatable("gui.stashlight.button.highlightAll"), b -> {
                    int count = HighlightManager.highlightAll(lastFilteredItems);
                    if (count > 0) {
                        Minecraft.getInstance().setScreen(null);
                    }
                })
                .tooltip(Component.translatable("gui.stashlight.button.highlightAll.tooltip"))
                .sizing(Sizing.fixed(COMPONENT_HEIGHT), Sizing.fixed(COMPONENT_HEIGHT));

        this.sourceButton = (ButtonComponent) Components
                .button(Component.translatable("gui.stashlight.dataSource." + dataSourceMode.name().toLowerCase()), b -> cycleDataSource())
                .tooltip(Component.translatable("gui.stashlight.dataSource.tooltip"))
                .sizing(Sizing.fixed(FILTER_WIDTH), Sizing.fixed(COMPONENT_HEIGHT));

        this.refreshButton = (ButtonComponent) Components
                .button(Component.translatable("gui.stashlight.button.refresh"), b -> {
                    var window = Minecraft.getInstance().getWindow();
                    if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)
                            || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT)) {
                        requestServerScanForceFull();
                    } else {
                        requestServerScan();
                    }
                })
                .tooltip(Component.translatable("gui.stashlight.button.refresh.tooltip"))
                .sizing(Sizing.fixed(COMPONENT_HEIGHT), Sizing.fixed(COMPONENT_HEIGHT));

        this.searchBar.child(modeTabs).child(sortBtn).child(this.searchField).child(dimFilterBtn)
                .child(logicButton).child(highlightAllBtn).child(sourceButton).child(refreshButton);

        // ── 3. ENCHANTMENT FILTER PANEL (SEARCH + ENCHANT mode) ─────────
        this.enchantPanelWrapper = (FlowLayout) Containers.verticalFlow(Sizing.fixed(0), Sizing.fill(100)).gap(GAP);
        this.enchantPanel = new EnchantFilterPanel(enchantState, this::onEnchantToggle, () -> {
            updateEnchantPanel();
            refreshGrid(searchField.getValue());
        });

        // ── 4. GRID AREA (SEARCH item grid <-> CRAFT content swap) ───────
        this.gridWrapper = (FlowLayout) Containers.verticalFlow(Sizing.fill(100), Sizing.expand(100))
                .surface(Surface.outline(GRID_BORDER))
                .padding(Insets.of(BORDER));

        this.itemGrid = new ItemGrid();
        FlowLayout scrollContent = (FlowLayout) Containers
                .verticalFlow(Sizing.content(), Sizing.content())
                .horizontalAlignment(HorizontalAlignment.CENTER);
        scrollContent.child(this.itemGrid);
        this.itemScroll = makeScroll(scrollContent, Sizing.fill(100));
        gridWrapper.child(this.itemScroll);

        // ── 5. CRAFT PAGE: nearby stock (top-left) + recipe list on top;
        //      below it one row holding the crafting console (preview +
        //      operations) and the backpack bar.
        this.recipeGrid = new RecipeGrid(this::onRecipeSelect);
        this.detailPanel = new RecipeDetailPanel();
        this.craftStockPanel = new CraftStockPanel(this::onStockSelect);
        this.craftStockPanel.setSlotsPerRow(STOCK_COLS);

        FlowLayout recipeScrollContent = (FlowLayout) Containers
                .verticalFlow(Sizing.content(), Sizing.content())
                .horizontalAlignment(HorizontalAlignment.CENTER);
        recipeScrollContent.child(this.recipeGrid);
        // expand(100) height: the recipe list takes the space above the bottom row
        this.recipeGridScroll = makeScroll(recipeScrollContent, Sizing.expand(100));

        this.craftContent = (FlowLayout) Containers.horizontalFlow(Sizing.fill(100), Sizing.fill(100))
                .gap(GAP)
                .verticalAlignment(VerticalAlignment.TOP);

        // Top-left: nearby stock — reachable chest items, scrollable grid.
        // Fixed width (STOCK_COLS slots + borders) so the inner scroll's
        // fill(100) sizing resolves without a content/fill cycle.
        FlowLayout stockScrollContent = (FlowLayout) Containers
                .verticalFlow(Sizing.content(), Sizing.content())
                .horizontalAlignment(HorizontalAlignment.CENTER);
        stockScrollContent.child(this.craftStockPanel);
        this.stockScroll = makeScroll(stockScrollContent, Sizing.expand(100));

        FlowLayout stockPanel = (FlowLayout) Containers
                .verticalFlow(Sizing.fixed(STOCK_COLS * (SLOT_SIZE + GAP) + GAP + (BORDER * 2)), Sizing.fill(100))
                .surface(Surface.outline(GRID_BORDER))
                .padding(Insets.of(BORDER))
                .horizontalAlignment(HorizontalAlignment.CENTER);
        stockPanel.child(Components.label(
                Component.translatable("gui.stashlight.craft.stock")).shadow(true));
        stockPanel.child(this.stockScroll);
        this.craftContent.child(stockPanel);

        // Right: the crafting console. Recipe list on top (expand height), the
        // bottom row below it: crafting console + backpack. Sizing.expand(100) =
        // the leftover width after the stock panel (fill(100) would overflow).
        FlowLayout craftConsole = (FlowLayout) Containers
                .verticalFlow(Sizing.expand(100), Sizing.fill(100))
                .gap(GAP)
                .verticalAlignment(VerticalAlignment.TOP);

        FlowLayout recipePanel = (FlowLayout) Containers
                .verticalFlow(Sizing.fill(100), Sizing.expand(100))
                .surface(Surface.outline(GRID_BORDER))
                .padding(Insets.of(BORDER));

        // Recipe filter row: name search + "craftable only" toggle (synced to
        // Config.crafting.showAllRecipes so the settings screen agrees).
        FlowLayout craftFilterRow = (FlowLayout) Containers
                .horizontalFlow(Sizing.fill(100), Sizing.fixed(COMPONENT_HEIGHT))
                .gap(GAP)
                .verticalAlignment(VerticalAlignment.CENTER);
        this.craftSearchField = Components.textBox(Sizing.expand(100), craftQuery);
        this.craftSearchField.setMaxLength(50);
        this.craftSearchField.onChanged().subscribe(text -> {
            craftQuery = text;
            refreshGrid(searchField.getValue());
        });
        craftFilterRow.child(this.craftSearchField);

        this.craftCraftableCheckbox = (CheckboxComponent) Components
                .checkbox(Component.translatable("gui.stashlight.craft.craftableOnly"))
                .checked(craftCraftableOnly)
                .onChanged(v -> {
                    craftCraftableOnly = v;
                    var cr = Config.get().crafting();
                    cr.setShowAllRecipes(!v);
                    Config.save();
                    refreshGrid(searchField.getValue());
                });
        craftFilterRow.child(this.craftCraftableCheckbox);

        recipePanel.child(craftFilterRow);
        recipePanel.child(this.recipeGridScroll);
        craftConsole.child(recipePanel);

        // Bottom row (left to right): crafting preview | craft operations |
        // backpack space bar. Horizontal so the panel stays compact vertically
        // and the recipe list above keeps as much height as possible.
        FlowLayout craftInvBarWrapper = (FlowLayout) Containers
                .verticalFlow(Sizing.content(), Sizing.content())
                .horizontalAlignment(HorizontalAlignment.CENTER)
                .surface(Surface.outline(GRID_BORDER))
                .padding(Insets.of(BORDER));
        this.craftInventoryBar = new InventoryBar();
        craftInvBarWrapper.child(this.craftInventoryBar);

        FlowLayout craftBottomRow = (FlowLayout) Containers
                .horizontalFlow(Sizing.fill(100), Sizing.content())
                .gap(GAP)
                .verticalAlignment(VerticalAlignment.TOP);
        craftBottomRow.child(this.detailPanel.root());
        craftBottomRow.child(craftInvBarWrapper);
        craftConsole.child(craftBottomRow);

        this.craftContent.child(craftConsole);

        // ── 6. BOTTOM PAGE BAR [搜索][合成] ───────────────────────────────
        this.pageBar = (FlowLayout) Containers.horizontalFlow(Sizing.content(), Sizing.fixed(COMPONENT_HEIGHT))
                .gap(GAP)
                .verticalAlignment(VerticalAlignment.CENTER);

        for (SearchPage p : SearchPage.values()) {
            ButtonComponent pageButton = (ButtonComponent) Components
                    .button(Component.empty(), b -> setPage(p))
                    .sizing(Sizing.content(), Sizing.fixed(COMPONENT_HEIGHT));
            this.pageButtons.put(p, pageButton);
            this.pageBar.child(pageButton);
        }

        // ── 7. FOOTER (thin bottom strip; settings row collapsible) ──────
        this.footer = (FlowLayout) Containers.verticalFlow(Sizing.fill(100), Sizing.content())
                .gap(GAP)
                .horizontalAlignment(HorizontalAlignment.CENTER)
                .verticalAlignment(VerticalAlignment.CENTER);

        this.footerRow1 = (FlowLayout) Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(COMPONENT_HEIGHT))
                .gap(GAP)
                .verticalAlignment(VerticalAlignment.CENTER);

        CheckboxComponent lookAtCheckbox = (CheckboxComponent) Components
                .checkbox(Component.translatable("screen.stashlight.lookAtTarget"))
                .checked(config.lookAtTarget()).onChanged(config::setLookAtTarget)
                .margins(Insets.top(BORDER));

        CheckboxComponent showSmallCheckbox = (CheckboxComponent) Components
                .checkbox(Component.translatable("screen.stashlight.showSmallContainers"))
                .checked(config.showSmallContainers()).onChanged(v -> {
                    config.setShowSmallContainers(v);
                    refreshGrid(searchField.getValue());
                })
                .margins(Insets.top(BORDER));

        CheckboxComponent takeableReachableCheckbox = (CheckboxComponent) Components
                .checkbox(Component.translatable("gui.stashlight.checkbox.takeableReachable"))
                .checked(false)
                .onChanged(v -> {
                    takeableReachableFilter.setEnabled(v);
                    refreshGrid(searchField.getValue());
                })
                .tooltip(Component.translatable("gui.stashlight.checkbox.takeableReachable.tooltip"))
                .margins(Insets.top(BORDER));

        DiscreteSliderComponent distanceSlider = Components.discreteSlider(Sizing.fixed(SLIDER_WIDTH), 0, 5);
        distanceSlider.snap(true).decimalPlaces(0);

        distanceSlider.setFromDiscreteValue(config.searchRadiusIndex());
        distanceSlider.message(s -> RadiusFilter.getLabelForIndex(config.searchRadiusIndex()));

        distanceSlider.onChanged().subscribe(v -> {
            int index = (int) Math.round(v);
            if (index == config.searchRadiusIndex()) {
                return;
            }
            config.setSearchRadiusIndex(index);
            distanceSlider.message(s -> RadiusFilter.getLabelForIndex(config.searchRadiusIndex()));
            refreshGrid(searchField.getValue());
        });

        this.footerRow1.child(distanceSlider).child(lookAtCheckbox).child(showSmallCheckbox).child(takeableReachableCheckbox);

        FlowLayout footerRow2 = (FlowLayout) Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(COMPONENT_HEIGHT))
                .gap(GAP)
                .verticalAlignment(VerticalAlignment.CENTER);

        this.settingsToggleButton = (ButtonComponent) Components
                .button(Component.literal(settingsExpanded ? "⚙ 设置 ▴" : "⚙ 设置 ▾"), b -> toggleSettings())
                .tooltip(Component.translatable("gui.stashlight.button.settingsToggle.tooltip"))
                .sizing(Sizing.content(), Sizing.fixed(COMPONENT_HEIGHT));

        ButtonComponent settingsButton = (ButtonComponent) Components
                .button(Component.translatable("gui.stashlight.button.highlightSettings"), b ->
                        Minecraft.getInstance().setScreen(new HighlightSettingsScreen(this)))
                .tooltip(Component.translatable("gui.stashlight.button.highlightSettings.tooltip"))
                .sizing(Sizing.fixed(COMPONENT_HEIGHT), Sizing.fixed(COMPONENT_HEIGHT));

        this.syncTimeLabel = (LabelComponent) Components.label(Component.empty())
                .shadow(true);

        // One thin bottom strip: settings toggle + page toggle [搜索][合成] +
        // highlight-settings + sync time. The settings row drops down only when
        // expanded, so by default the footer is a single row of height.
        footerRow2.child(this.settingsToggleButton).child(this.pageBar)
                .child(settingsButton).child(this.syncTimeLabel);
        if (settingsExpanded) {
            this.footer.child(0, this.footerRow1);
        }
        this.footer.child(footerRow2);

        // ── 8. ASSEMBLE ──────────────────────────────────────────────────
        this.rightColumn = (FlowLayout) Containers.verticalFlow(Sizing.expand(100), Sizing.fill(100))
                .gap(GAP)
                .horizontalAlignment(HorizontalAlignment.CENTER);

        this.rightColumn.child(gridWrapper);

        boolean showInvBar = Config.get().searchInventoryBar().enabled();
        boolean showQueue = Config.get().takeQueue().enabled();
        this.bottomRow = null;

        if (showInvBar || showQueue) {
            this.bottomRow = (FlowLayout) Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
                    .gap(GAP)
                    .verticalAlignment(VerticalAlignment.TOP);
        }

        if (showInvBar) {
            FlowLayout invBarWrapper = (FlowLayout) Containers.verticalFlow(
                            showQueue ? Sizing.content() : Sizing.fill(100),
                            Sizing.content())
                    .horizontalAlignment(HorizontalAlignment.LEFT)
                    .surface(Surface.outline(GRID_BORDER))
                    .padding(Insets.of(BORDER));
            this.inventoryBar = new InventoryBar();
            invBarWrapper.child(this.inventoryBar);
            this.bottomRow.child(invBarWrapper);
        }

        if (showQueue) {
            this.bottomRow.child(buildQueueSection(showInvBar));
        }

        if (this.bottomRow != null) {
            this.bottomRow.horizontalAlignment(showQueue ? HorizontalAlignment.CENTER : HorizontalAlignment.LEFT);
            this.rightColumn.child(this.bottomRow);
        }

        FlowLayout contentArea = (FlowLayout) Containers.horizontalFlow(Sizing.fill(100), Sizing.expand(100)).gap(GAP);
        contentArea.child(enchantPanelWrapper).child(this.rightColumn);

        mainWindow.child(title).child(searchBar).child(contentArea).child(this.footer);
        rootComponent.child(mainWindow).alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        if (page == SearchPage.CRAFT) {
            recipeCatalog.refresh(Station.detect());
        }
        updateModeUi();
        updateSourceButton();
        requestServerScan();
    }

    private FlowLayout buildQueueSection(boolean inventoryBarVisible) {
        var stashlight = Stashlight.getInstance();
        var takeQueue = stashlight != null ? stashlight.getTakeQueue() : null;
        this.queuePanel = new TakeQueuePanel(takeQueue);
        int capacity = Config.get().takeQueue().capacity();
        this.queuePanel.setColumns(inventoryBarVisible ? Math.min(4, capacity) : capacity);

        FlowLayout buttons = (FlowLayout) Containers.horizontalFlow(Sizing.content(), Sizing.fixed(COMPONENT_HEIGHT))
                .gap(GAP)
                .verticalAlignment(VerticalAlignment.CENTER);

        buttons.child(Components.button(
                Component.translatable("gui.stashlight.queue.takeAll"),
                b -> processQueue()
        ).sizing(Sizing.fixed(70), Sizing.fixed(COMPONENT_HEIGHT)));

        buttons.child(Components.button(
                Component.translatable("gui.stashlight.queue.clear"),
                b -> {
                    if (takeQueue != null) takeQueue.clear();
                    if (queuePanel != null) queuePanel.refresh();
                }
        ).sizing(Sizing.fixed(70), Sizing.fixed(COMPONENT_HEIGHT)));

        FlowLayout wrapper = (FlowLayout) Containers.verticalFlow(Sizing.content(), Sizing.content())
                .gap(GAP)
                .horizontalAlignment(HorizontalAlignment.CENTER);
        wrapper.child(queuePanel);
        wrapper.child(buttons);
        return wrapper;
    }

    private void processQueue() {
        var mc = Minecraft.getInstance();
        var stashlight = Stashlight.getInstance();
        if (stashlight == null) return;
        var takeQueue = stashlight.getTakeQueue();
        if (takeQueue == null || takeQueue.isEmpty()) return;

        java.util.List<dev.strangequark.stashlight.take.TakeQueueEntry> copy =
                new java.util.ArrayList<>(takeQueue.entries());
        takeQueue.clear();
        if (queuePanel != null) queuePanel.refresh();

        boolean modded = stashlight.isModdedTakeAvailable();
        if (modded) {
            stashlight.getTakeClient().startQueue(copy);
            if (!Config.get().remoteTake().keepScreenOnTake()) {
                mc.setScreen(null);
            }
        } else {
            VanillaTaker.startQueue(copy);
        }
    }

    private void refreshGrid(String query) {
        int windowWidth = (int) (this.width * (SCREEN_FILL_PERCENT / 100.0));

        if (page == SearchPage.CRAFT) {
            // The recipe list spans the console width right of the nearby-stock
            // panel. Size the grid from the width left over after the stock
            // panel (+ window padding, gaps, grid border and scrollbar), so it
            // uses the available space and is not clipped.
            int stockWidth = STOCK_COLS * (SLOT_SIZE + GAP) + GAP + (BORDER * 2);
            int fixedVars = (PADDING * 2) + GAP + stockWidth + GAP + (BORDER * 2) + SCROLL_WIDTH;
            int slotsPerRow = Math.max(1, (windowWidth - fixedVars) / (SLOT_SIZE + GAP));

            // Recipe search + craftable-only filtering (by display name).
            String lowerQuery = craftQuery == null ? "" : craftQuery.toLowerCase();
            List<RecipeList> filtered = new ArrayList<>();
            for (RecipeList list : recipeCatalog.lists()) {
                if (!lowerQuery.isEmpty()
                        && !list.result().getHoverName().getString().toLowerCase().contains(lowerQuery)) {
                    continue;
                }
                if (craftCraftableOnly && !recipeCatalog.isCraftable(list)) continue;
                filtered.add(list);
            }

            this.recipeGrid.setItems(filtered, slotsPerRow, recipeCatalog.craftable());
            // Nearby stock: re-aggregate reachable chest items (fresh counts).
            this.craftStockPanel.refresh();
            // Auto-select the first recipe so the detail console below the list
            // is populated immediately instead of sitting in its empty state.
            if (recipeGrid.selectedIndex() < 0 && !filtered.isEmpty() && !detailPanel.hasRecipe()) {
                recipeGrid.select(0);
            }
            return;
        }

        int enchantPanelWidth = (mode == SearchMode.ENCHANT) ? ENCHANT_PANEL_WIDTH : 0;
        int contentAreaVars = (PADDING * 2) + GAP + enchantPanelWidth;
        int gridVars = SCROLL_WIDTH + GAP + (BORDER * 2);
        int maxGridWidth = windowWidth - contentAreaVars - gridVars;
        int slotsPerRow = Math.max(1, maxGridWidth / (SLOT_SIZE + GAP));

        // Snap window width to exactly fit the columns
        int gridContentWidth = slotsPerRow * (SLOT_SIZE + GAP) + GAP;
        int gridWidth = gridContentWidth + gridVars;
        int contentAreaWidth = gridWidth + enchantPanelWidth + GAP;
        int snappedWidth = contentAreaWidth + (PADDING * 2);
        if (this.mainWindow != null) {
            this.mainWindow.horizontalSizing(Sizing.fixed(snappedWidth));
        }

        List<IndexedItem> source = repository.getSearchIndex(dataSourceMode);
        List<IndexedItem> filteredItems;

        if (mode == SearchMode.ENCHANT) {
            filteredItems = source.stream()
                    .filter(item -> enchantState.matches(item) && filterManager.matches(item))
                    .collect(Collectors.toCollection(ArrayList::new));
        } else {
            final String lowerQuery = query.toLowerCase();
            filteredItems = source.stream()
                    .filter(item -> matchesDeep(item, lowerQuery) && filterManager.matches(item))
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        this.lastFilteredItems = filteredItems;

        List<DisplayItem> aggregated = aggregate(filteredItems);

        if (sortManager.getCurrent() != null) sortManager.getCurrent().sort(aggregated);

        this.itemGrid.setItems(aggregated, slotsPerRow);
    }

    private static List<DisplayItem> aggregate(List<IndexedItem> items) {
        java.util.Map<StackKey, List<IndexedItem>> groups = new java.util.LinkedHashMap<>();
        for (IndexedItem item : items) {
            groups.computeIfAbsent(new StackKey(item.stack()), k -> new ArrayList<>()).add(item);
        }

        List<DisplayItem> result = new ArrayList<>();
        for (var group : groups.values()) {
            // A container indexed both locally and by a server scan appears twice;
            // dedupe by (position, locate path) so totals aren't doubled.
            java.util.Map<dev.strangequark.stashlight.model.LocatePath, IndexedItem> dedup =
                    new java.util.LinkedHashMap<>();
            for (IndexedItem i : group) {
                var key = i.path() != null ? i.path() : new dev.strangequark.stashlight.model.LocatePath(-1);
                IndexedItem existing = dedup.get(key);
                if (existing == null || i.timestamp() > existing.timestamp()) {
                    dedup.put(key, i);
                }
            }
            List<IndexedItem> unique = new ArrayList<>(dedup.values());
            IndexedItem first = unique.get(0);
            ItemStack merged = first.stack().copy();
            merged.setCount(unique.stream().mapToInt(i -> i.stack().getCount()).sum());
            result.add(new DisplayItem(merged, unique));
        }
        return result;
    }

    public static boolean matchesDeep(IndexedItem item, String lowerQuery) {
        if (lowerQuery.isEmpty()) return true;

        // 1. Check main item name (searchKey is already lowercase)
        if (item.searchKey().contains(lowerQuery)) return true;

        // 2. Check enchantments
        for (var enchant : item.enchantments()) {
            if (enchant.displayName().getString().toLowerCase().contains(lowerQuery)) return true;
            if (enchant.id().toString().toLowerCase().contains(lowerQuery)) return true;
        }

        return false;
    }

    private void setMode(SearchMode newMode) {
        this.mode = newMode;
        updateModeUi();
        refreshGrid(searchField.getValue());
    }

    /**
     * Switch the whole main content area between the search page and the
     * crafting page. Only on the crafting page is the station re-detected and
     * the recipe data refreshed.
     */
    private void setPage(SearchPage newPage) {
        if (this.page == newPage) return;
        if (newPage == SearchPage.CRAFT && !Config.get().crafting().enabled()) {
            return;
        }
        this.page = newPage;
        if (newPage == SearchPage.CRAFT) {
            Station current = Station.detect();
            if (current != recipeCatalog.station() || recipeCatalog.lists().isEmpty()) {
                recipeCatalog.refresh(current);
            } else {
                recipeCatalog.refreshCraftability();
            }
            // Refresh in-range container data once when entering the craft page,
            // so recipes pull from up-to-date chest contents ("take material from
            // a chest, then craft"). Not per-frame: this only runs on the
            // SEARCH -> CRAFT transition.
            requestServerScan();
        }
        updateModeUi();
        refreshGrid(searchField.getValue());
    }

    /**
     * Hide or show the page-specific regions of the layout: the top search bar
     * and the bottom inventory/queue row belong to the SEARCH page only; the
     * crafting page keeps just the content area (recipe list + detail panel)
     * and the footer page bar. Children are added/removed from their parents so
     * the hidden rows do not participate in layout at all.
     */
    private void applyPageLayout() {
        if (mainWindow == null || searchBar == null || rightColumn == null) return;
        boolean craft = page == SearchPage.CRAFT;

        boolean searchBarPresent = mainWindow.children().contains(searchBar);
        if (craft && searchBarPresent) {
            mainWindow.removeChild(searchBar);
        } else if (!craft && !searchBarPresent) {
            mainWindow.child(1, searchBar);
        }

        if (bottomRow != null) {
            boolean bottomRowPresent = rightColumn.children().contains(bottomRow);
            if (craft && bottomRowPresent) {
                rightColumn.removeChild(bottomRow);
            } else if (!craft && !bottomRowPresent) {
                rightColumn.child(1, bottomRow);
            }
        }
    }

    /**
     * Expand/collapse the footer settings row (search-radius slider + the
     * look-at-target / show-small-containers / takeable-reachable checkboxes).
     * The row is added to / removed from the footer so a collapsed row takes no
     * vertical space at all (owo has no visibility flag); the "⚙ 设置" button
     * label reflects the state. The footer is shared by the SEARCH and CRAFT
     * pages, so this applies to both.
     */
    private void toggleSettings() {
        if (footer == null || footerRow1 == null) return;
        settingsExpanded = !settingsExpanded;
        if (settingsExpanded) {
            footer.child(0, footerRow1);
        } else {
            footer.removeChild(footerRow1);
        }
        if (settingsToggleButton != null) {
            settingsToggleButton.setMessage(Component.literal(
                    settingsExpanded ? "⚙ 设置 ▴" : "⚙ 设置 ▾"));
        }
    }

    private void onRecipeSelect(RecipeList list, int variantIndex) {
        if (list == null || list.variants().isEmpty()) return;
        this.detailPanel.setRecipe(list.variants().get(variantIndex % list.variants().size()));
    }

    /**
     * Clicking an item in the nearby-stock panel selects the recipe that uses
     * it as an ingredient (auto-filling the 3x3 pre-craft grid with the recipe
     * shape); falls back to a recipe that produces that item.
     */
    private void onStockSelect(DisplayItem item, int index) {
        List<RecipeList> lists = recipeCatalog.lists();
        for (int i = 0; i < lists.size(); i++) {
            for (RecipeDisplayEntry entry : lists.get(i).variants()) {
                for (Ingredient ing : entry.craftingRequirements().orElse(List.of())) {
                    if (ing.test(item.stack())) {
                        this.recipeGrid.select(i);
                        return;
                    }
                }
            }
        }
        StackKey key = new StackKey(item.stack());
        for (int i = 0; i < lists.size(); i++) {
            if (key.equals(new StackKey(lists.get(i).result()))) {
                this.recipeGrid.select(i);
                return;
            }
        }
    }

    private static String modeKey(SearchMode m) {
        return switch (m) {
            case ITEM -> "gui.stashlight.mode.item";
            case ENCHANT -> "gui.stashlight.mode.enchant";
        };
    }

    private static String pageKey(SearchPage p) {
        return switch (p) {
            case SEARCH -> "gui.stashlight.page.search";
            case CRAFT -> "gui.stashlight.page.craft";
        };
    }

    /** Highlight the tab for the current mode; mute the others. */
    private void updateTabHighlight() {
        for (Map.Entry<SearchMode, ButtonComponent> entry : tabButtons.entrySet()) {
            boolean active = entry.getKey() == mode;
            entry.getValue().setMessage(Component.translatable(modeKey(entry.getKey()))
                    .withStyle(active ? ChatFormatting.WHITE : ChatFormatting.GRAY));
        }
    }

    /** Highlight the current page in the bottom bar; show the reachable station on the craft page button. */
    private void updatePageHighlight(Station station) {
        for (Map.Entry<SearchPage, ButtonComponent> entry : pageButtons.entrySet()) {
            boolean active = entry.getKey() == page;
            entry.getValue().setMessage(Component.translatable(pageKey(entry.getKey()))
                    .withStyle(active ? ChatFormatting.WHITE : ChatFormatting.GRAY));
        }
        ButtonComponent craftPage = pageButtons.get(SearchPage.CRAFT);
        if (craftPage != null) {
            craftPage.active = Config.get().crafting().enabled();
            craftPage.tooltip(Component.translatable(station == Station.CRAFTING_TABLE
                    ? "gui.stashlight.craft.station.table"
                    : "gui.stashlight.craft.station.inventory"));
        }
    }

    private static ScrollContainer<FlowLayout> makeScroll(FlowLayout content, Sizing verticalSizing) {
        ScrollContainer<FlowLayout> scroll = Containers
                .verticalScroll(Sizing.fill(100), verticalSizing, content)
                .scrollbarThiccness(SCROLL_WIDTH)
                .scrollbar(ScrollContainer.Scrollbar.vanillaFlat());
        scroll.surface((drawContext, component) -> {
            int x1 = component.x() + component.width() - SCROLL_WIDTH;
            int y1 = component.y();
            int x2 = component.x() + component.width();
            int y2 = component.y() + component.height();
            drawContext.fill(x1, y1, x2, y2, SCROLL_TRACK);
        });
        return scroll;
    }

    private void toggleLogicMode() {
        enchantState.setAndMode(!enchantState.isAndMode());
        updateModeUi();
        refreshGrid(searchField.getValue());
    }

    private void onEnchantToggle(ResourceLocation id) {
        enchantState.toggle(id, 1, 255);
        updateEnchantPanel();
        refreshGrid(searchField.getValue());
    }

    private void cycleDataSource() {
        dataSourceMode = switch (dataSourceMode) {
            case LOCAL -> DataSourceMode.SERVER;
            case SERVER -> DataSourceMode.MERGED;
            case MERGED -> DataSourceMode.LOCAL;
        };
        Config.get().dataSource().setMode(dataSourceMode);
        Config.save();
        updateSourceButton();
        requestServerScan();
        refreshGrid(searchField.getValue());
    }

    /**
     * Try to send a scan request. Returns true if the request was actually sent.
     */
    private boolean tryRequestServerScan() {
        if (dataSourceMode == DataSourceMode.LOCAL) return false;
        var stashlight = Stashlight.getInstance();
        if (stashlight == null || !stashlight.isServerModPresent() || !stashlight.isServerEnabled()) return false;

        // Dedup: skip if ProximityScanner sent a scan request within the last 500ms
        long lastScan = stashlight.getLastScanRequestTime();
        if (System.currentTimeMillis() - lastScan < 500) return false;

        stashlight.requestServerScan();
        return true;
    }

    private void requestServerScan() {
        tryRequestServerScan();
    }

    private void requestServerScanForceFull() {
        if (dataSourceMode == DataSourceMode.LOCAL) return;
        var stashlight = Stashlight.getInstance();
        if (stashlight != null && stashlight.isServerModPresent() && stashlight.isServerEnabled()) {
            stashlight.requestServerScanForceFull();
        }
    }

    private void updateSyncTimeLabel() {
        if (syncTimeLabel == null) return;
        var stashlight = Stashlight.getInstance();
        if (stashlight == null) {
            syncTimeLabel.text(Component.empty());
            return;
        }
        long lastTime = stashlight.getLastServerDataTime();
        if (lastTime <= 0) {
            syncTimeLabel.text(Component.translatable("gui.stashlight.label.syncNever"));
        } else {
            long secondsAgo = (System.currentTimeMillis() - lastTime) / 1000;
            syncTimeLabel.text(Component.translatable("gui.stashlight.label.syncAgo", secondsAgo));
        }
    }

    private void updateSourceButton() {
        if (sourceButton == null) return;
        sourceButton.setMessage(Component.translatable("gui.stashlight.dataSource." + dataSourceMode.name().toLowerCase()));

        var stashlight = Stashlight.getInstance();
        boolean serverAvailable = stashlight != null && stashlight.isServerModPresent() && stashlight.isServerEnabled();
        sourceButton.active = serverAvailable;
        if (refreshButton != null) {
            refreshButton.active = serverAvailable && dataSourceMode != DataSourceMode.LOCAL;
        }
    }

    private void updateModeUi() {
        if (logicButton == null || enchantPanelWrapper == null || gridWrapper == null) return;

        updateTabHighlight();
        updatePageHighlight(Station.detect());
        boolean inEnchant = (page == SearchPage.SEARCH && mode == SearchMode.ENCHANT);
        logicButton.setMessage(Component.translatable(enchantState.isAndMode()
                ? "gui.stashlight.logic.and"
                : "gui.stashlight.logic.or"));
        logicButton.active = inEnchant;

        enchantPanelWrapper.clearChildren();
        if (inEnchant) {
            enchantPanelWrapper.horizontalSizing(Sizing.fixed(ENCHANT_PANEL_WIDTH));
            if (this.enchantScroll == null) {
                this.enchantScroll = Containers
                        .verticalScroll(Sizing.fill(100), Sizing.fill(100), enchantPanel.root())
                        .scrollbarThiccness(SCROLL_WIDTH)
                        .scrollbar(ScrollContainer.Scrollbar.vanillaFlat());
            }
            enchantPanelWrapper.child(this.enchantScroll);
            updateEnchantPanel();
        } else {
            enchantPanelWrapper.horizontalSizing(Sizing.fixed(0));
        }

        // Swap the right-column content: the craft split (backpack + console) on
        // the CRAFT page, the regular item grid on the SEARCH page. Only touch
        // gridWrapper when the active content actually changes, so ITEM<->ENCHANT
        // switches keep the item grid mounted and preserve its scroll offset.
        io.wispforest.owo.ui.core.Component wantedContent = (page == SearchPage.CRAFT) ? this.craftContent : this.itemScroll;
        if (gridWrapper.children().size() != 1 || gridWrapper.children().get(0) != wantedContent) {
            gridWrapper.clearChildren();
            gridWrapper.child(wantedContent);
        }

        applyPageLayout();
    }

    private void updateEnchantPanel() {
        double offset = this.enchantScroll != null ? getScrollOffset(this.enchantScroll) : 0;
        int maxScroll = this.enchantScroll != null ? getMaxScroll(this.enchantScroll) : 0;
        double ratio = maxScroll > 0 ? offset / maxScroll : 0;

        enchantPanel.update(collectAvailableEnchants());

        if (this.enchantScroll != null) {
            this.enchantScroll.queue(() -> this.enchantScroll.scrollTo(ratio));
        }
    }

    private static double getScrollOffset(ScrollContainer<?> container) {
        try {
            var field = ScrollContainer.class.getDeclaredField("scrollOffset");
            field.setAccessible(true);
            return field.getDouble(container);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static int getMaxScroll(ScrollContainer<?> container) {
        try {
            var field = ScrollContainer.class.getDeclaredField("maxScroll");
            field.setAccessible(true);
            return field.getInt(container);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private List<EnchantEntry> collectAvailableEnchants() {
        List<EnchantEntry> available = new ArrayList<>();
        for (var entry : repository.getEnchantmentIndex(dataSourceMode).entrySet()) {
            ResourceLocation id = entry.getKey();
            outer:
            for (IndexedItem item : entry.getValue()) {
                for (EnchantEntry e : item.enchantments()) {
                    if (e.id().equals(id)) {
                        available.add(e);
                        break outer;
                    }
                }
            }
        }
        return available;
    }

    public void refreshFromServer() {
        if (this.searchField != null) {
            refreshGrid(this.searchField.getValue());
        }
    }

    public void refreshQueuePanel() {
        if (this.queuePanel != null) {
            this.queuePanel.refresh();
        }
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Debounce: fire refreshGrid only after typing has settled for DEBOUNCE_MS.
        if (pendingQuery != null && System.currentTimeMillis() - lastQueryChangeTime >= DEBOUNCE_MS) {
            refreshGrid(pendingQuery);
            pendingQuery = null;
        }

        // v1.5 crafting: every 20 frames re-detect the crafting station, keep the
        // craft page button tooltip fresh, and refresh craftability/recipe list —
        // only while the CRAFT page is active (avoids the cost on the search page).
        if (++craftCheckTicks >= 20) {
            craftCheckTicks = 0;
            if (page == SearchPage.CRAFT) {
                Station current = Station.detect();
                updatePageHighlight(current);
                if (current != recipeCatalog.station() || recipeCatalog.lists().isEmpty()) {
                    recipeCatalog.refresh(current);
                } else {
                    recipeCatalog.refreshCraftability();
                }
                refreshGrid(searchField.getValue());
            }        }

        // Retry scan request once when the handshake eventually arrives
        if (!scanRequested) {
            if (tryRequestServerScan()) {
                scanRequested = true;
            }
        }

        updateSyncTimeLabel();
        super.render(context, mouseX, mouseY, delta);

        // v1.3: Shift-hover nested container preview (chest-like grid)
        if (hasShiftDown()) {
            ItemStack hovered = ItemStack.EMPTY;
            if (inventoryBar != null && !inventoryBar.getHoveredStack().isEmpty()) {
                hovered = inventoryBar.getHoveredStack();
            } else if (itemGrid != null && !itemGrid.getHoveredStack().isEmpty()) {
                hovered = itemGrid.getHoveredStack();
            }
            if (!hovered.isEmpty() && NestedContainerPreview.hasPreview(hovered)) {
                NestedContainerPreview.render(context, hovered, mouseX, mouseY);
            }
        }
    }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.blurBeforeThisStratum();
        super.renderBackground(context, mouseX, mouseY, delta);
    }

    @Override
    public void resize(@NotNull Minecraft client, int width, int height) {
        super.resize(client, width, height);
        this.refreshGrid(this.searchField.getValue());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static boolean hasShiftDown() {
        return Minecraft.getInstance().options.keyShift.isDown();
    }
}
