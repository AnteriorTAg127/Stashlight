package dev.strangequark.stashlight.screen;

import dev.strangequark.stashlight.Stashlight;
import dev.strangequark.stashlight.config.Config;
import dev.strangequark.stashlight.gui.EnchantFilterPanel;
import dev.strangequark.stashlight.gui.InventoryBar;
import dev.strangequark.stashlight.gui.ItemGrid;
import dev.strangequark.stashlight.logic.filter.*;
import dev.strangequark.stashlight.logic.sort.SortManager;
import dev.strangequark.stashlight.model.DataSourceMode;
import dev.strangequark.stashlight.model.DisplayItem;
import dev.strangequark.stashlight.model.EnchantEntry;
import dev.strangequark.stashlight.model.IndexedItem;
import dev.strangequark.stashlight.model.StackKey;
import dev.strangequark.stashlight.render.HighlightManager;
import dev.strangequark.stashlight.repository.ContainerRepository;
import dev.strangequark.stashlight.util.Util;
import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.*;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static dev.strangequark.stashlight.gui.UIStyle.*;

public class SearchScreen extends BaseOwoScreen<FlowLayout> {
    private final ContainerRepository repository;
    private FlowLayout rootComponent;
    private FlowLayout mainWindow;
    private TextBoxComponent searchField;
    private ItemGrid itemGrid;

    private final FilterManager filterManager = new FilterManager();
    private final SortManager sortManager;
    private final EnchantFilterState enchantState = new EnchantFilterState();
    private EnchantFilterPanel enchantPanel;
    private FlowLayout enchantPanelWrapper;
    private ScrollContainer<FlowLayout> enchantScroll;
    private ButtonComponent modeButton;
    private ButtonComponent logicButton;
    private ButtonComponent sourceButton;
    private ButtonComponent refreshButton;
    private LabelComponent syncTimeLabel;

    private SearchMode mode = SearchMode.ITEM;
    private DataSourceMode dataSourceMode;

    private static final long DEBOUNCE_MS = 150;
    private String pendingQuery = null;
    private long lastQueryChangeTime = 0;

    private List<IndexedItem> lastFilteredItems = new ArrayList<>();
    private boolean scanRequested = false;

    public SearchScreen(ContainerRepository repository) {
        this.repository = repository;
        this.sortManager = new SortManager(Config.get().sortKey());
        this.dataSourceMode = Config.get().dataSource().mode();
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

        // --- 1. MAIN WINDOW ---
        this.mainWindow = (FlowLayout) Containers
                .verticalFlow(Sizing.fill(SCREEN_FILL_PERCENT), Sizing.fill(SCREEN_FILL_PERCENT))
                .gap(GAP)
                .surface(Surface.VANILLA_TRANSLUCENT)
                .horizontalAlignment(HorizontalAlignment.CENTER)
                .verticalAlignment(VerticalAlignment.TOP)
                .padding(Insets.of(PADDING));

        // --- 2. HEADER & SEARCH BAR ---
        LabelComponent title = Components.label(Component.translatable("screen.stashlight.label.searchContainers")).shadow(true);

        FlowLayout searchBar = (FlowLayout) Containers
                .horizontalFlow(Sizing.fill(), Sizing.fixed(COMPONENT_HEIGHT))
                .gap(GAP)
                .alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        this.modeButton = (ButtonComponent) Components
                .button(Component.translatable("gui.stashlight.mode.item"), b -> toggleMode())
                .sizing(Sizing.fixed(COMPONENT_HEIGHT), Sizing.fixed(COMPONENT_HEIGHT));

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

        searchBar.child(modeButton).child(sortBtn).child(this.searchField).child(dimFilterBtn)
                .child(logicButton).child(highlightAllBtn).child(sourceButton).child(refreshButton);

        // --- 3. ENCHANTMENT FILTER PANEL ---
        this.enchantPanelWrapper = (FlowLayout) Containers.verticalFlow(Sizing.fixed(0), Sizing.fill(100)).gap(GAP);
        this.enchantPanel = new EnchantFilterPanel(enchantState, this::onEnchantToggle, () -> {
            updateEnchantPanel();
            refreshGrid(searchField.getValue());
        });

        // --- 4. SCROLLABLE GRID ---
        FlowLayout gridWrapper = (FlowLayout) Containers.verticalFlow(Sizing.fill(100), Sizing.expand(100))
                .surface(Surface.outline(GRID_BORDER))
                .padding(Insets.of(BORDER));

        this.itemGrid = new ItemGrid();

        FlowLayout scrollContent = (FlowLayout) Containers
                .verticalFlow(Sizing.content(), Sizing.content())
                .horizontalAlignment(HorizontalAlignment.CENTER);

        scrollContent.child(this.itemGrid);

        ScrollContainer<FlowLayout> scrollContainer = Containers
                .verticalScroll(Sizing.fill(100), Sizing.fill(100), scrollContent);

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

        // --- 5. FOOTER ---
        FlowLayout footer = (FlowLayout) Containers.verticalFlow(Sizing.fill(100), Sizing.content())
                .gap(GAP)
                .verticalAlignment(VerticalAlignment.CENTER);

        FlowLayout footerRow1 = (FlowLayout) Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(COMPONENT_HEIGHT))
                .gap(GAP)
                .verticalAlignment(VerticalAlignment.CENTER);

        CheckboxComponent lookAtCheckbox = (CheckboxComponent) Components
                .checkbox(Component.translatable("screen.stashlight.lookAtTarget"))
                .checked(config.lookAtTarget()).onChanged(config::setLookAtTarget)
                .margins(Insets.top(BORDER));

        CheckboxComponent showSmallCheckbox = (CheckboxComponent) Components
                .checkbox(Component.translatable("screen.stashlight.showSmallContainers"))
                .checked(config.showSmallContainers())
                .onChanged(v -> {
                    config.setShowSmallContainers(v);
                    refreshGrid(searchField.getValue());
                })
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

        footerRow1.child(distanceSlider).child(lookAtCheckbox).child(showSmallCheckbox);

        FlowLayout footerRow2 = (FlowLayout) Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(COMPONENT_HEIGHT))
                .gap(GAP)
                .verticalAlignment(VerticalAlignment.CENTER);

        ButtonComponent settingsButton = (ButtonComponent) Components
                .button(Component.translatable("gui.stashlight.button.highlightSettings"), b ->
                        Minecraft.getInstance().setScreen(new HighlightSettingsScreen(this)))
                .tooltip(Component.translatable("gui.stashlight.button.highlightSettings.tooltip"))
                .sizing(Sizing.fixed(COMPONENT_HEIGHT), Sizing.fixed(COMPONENT_HEIGHT));

        this.syncTimeLabel = (LabelComponent) Components.label(Component.empty())
                .shadow(true);

        footerRow2.child(settingsButton).child(this.syncTimeLabel);
        footer.child(footerRow1).child(footerRow2);

        // --- ASSEMBLE ---
        // Right column: scrollable grid on top, inventory bar below
        FlowLayout rightColumn = (FlowLayout) Containers.verticalFlow(Sizing.fill(100), Sizing.fill(100))
                .gap(GAP)
                .horizontalAlignment(HorizontalAlignment.CENTER);

        // Grid wrapper (top right)
        rightColumn.child(gridWrapper);

        // Inventory bar (bottom center-right, v1.3)
        if (Config.get().searchInventoryBar().enabled()) {
            FlowLayout invBarWrapper = (FlowLayout) Containers.verticalFlow(Sizing.content(), Sizing.content())
                    .horizontalAlignment(HorizontalAlignment.CENTER)
                    .surface(Surface.outline(GRID_BORDER))
                    .padding(Insets.of(BORDER));
            invBarWrapper.child(new InventoryBar());
            rightColumn.child(invBarWrapper);
        }

        // Content area: enchant panel on left, right column on right
        FlowLayout contentArea = (FlowLayout) Containers.horizontalFlow(Sizing.fill(100), Sizing.expand(100)).gap(GAP);
        contentArea.child(enchantPanelWrapper).child(rightColumn);

        mainWindow.child(title).child(searchBar).child(contentArea).child(footer);
        rootComponent.child(mainWindow).alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        updateModeUi();
        updateSourceButton();
        requestServerScan();
    }

    private void refreshGrid(String query) {
        int windowWidth = (int) (this.width * (SCREEN_FILL_PERCENT / 100.0));
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
            IndexedItem first = group.get(0);
            ItemStack merged = first.stack().copy();
            merged.setCount(group.stream().mapToInt(i -> i.stack().getCount()).sum());
            result.add(new DisplayItem(merged, group));
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

    private void toggleMode() {
        mode = (mode == SearchMode.ITEM) ? SearchMode.ENCHANT : SearchMode.ITEM;
        updateModeUi();
        refreshGrid(searchField.getValue());
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
        if (modeButton == null || logicButton == null || enchantPanelWrapper == null) return;

        modeButton.setMessage(Component.translatable(mode == SearchMode.ITEM
                ? "gui.stashlight.mode.item"
                : "gui.stashlight.mode.enchant"));
        logicButton.setMessage(Component.translatable(enchantState.isAndMode()
                ? "gui.stashlight.logic.and"
                : "gui.stashlight.logic.or"));
        logicButton.active = (mode == SearchMode.ENCHANT);

        enchantPanelWrapper.clearChildren();
        if (mode == SearchMode.ENCHANT) {
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

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Debounce: fire refreshGrid only after typing has settled for DEBOUNCE_MS.
        if (pendingQuery != null && System.currentTimeMillis() - lastQueryChangeTime >= DEBOUNCE_MS) {
            refreshGrid(pendingQuery);
            pendingQuery = null;
        }

        // Retry scan request once when the handshake eventually arrives
        if (!scanRequested) {
            if (tryRequestServerScan()) {
                scanRequested = true;
            }
        }

        updateSyncTimeLabel();
        super.render(context, mouseX, mouseY, delta);
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
}