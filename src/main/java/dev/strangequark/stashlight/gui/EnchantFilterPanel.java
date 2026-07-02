package dev.strangequark.stashlight.gui;

import dev.strangequark.stashlight.logic.filter.EnchantFilterState;
import dev.strangequark.stashlight.model.EnchantEntry;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.CheckboxComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static dev.strangequark.stashlight.gui.UIStyle.*;

/**
 * Panel that lists available enchantments and lets the user toggle them
 * for filtering. Selected enchantments expose min/max level controls.
 */
public final class EnchantFilterPanel {
    private static final int ROW_HEIGHT = 14;
    private static final int LEVEL_BUTTON_SIZE = 14;

    private final EnchantFilterState state;
    private final Consumer<ResourceLocation> onToggle;
    private final Runnable onRangeChanged;
    private final FlowLayout root;

    public EnchantFilterPanel(EnchantFilterState state, Consumer<ResourceLocation> onToggle, Runnable onRangeChanged) {
        this.state = state;
        this.onToggle = onToggle;
        this.onRangeChanged = onRangeChanged;
        this.root = (FlowLayout) Containers.verticalFlow(Sizing.fill(100), Sizing.content()).gap(GAP);
    }

    public FlowLayout root() {
        return root;
    }

    public void update(List<EnchantEntry> available) {
        root.clearChildren();
        Map<ResourceLocation, EnchantFilterState.LevelRange> selected = state.getSelected();

        List<EnchantEntry> sorted = new ArrayList<>(available);
        sorted.sort(Comparator.comparing(a -> a.displayName().getString().toLowerCase()));

        if (sorted.isEmpty()) {
            return;
        }

        FlowLayout list = (FlowLayout) Containers.verticalFlow(Sizing.fill(100), Sizing.content()).gap(GAP);

        for (EnchantEntry entry : sorted) {
            boolean isSelected = selected.containsKey(entry.id());
            ResourceLocation id = entry.id();
            int maxLevel = entry.level();

            Component label = entry.displayName().copy();
            if (isSelected) {
                label = label.copy().withStyle(ChatFormatting.GREEN);
            }

            FlowLayout row = (FlowLayout) Containers.verticalFlow(Sizing.fill(100), Sizing.content()).gap(GAP / 2);

            CheckboxComponent checkbox = (CheckboxComponent) Components
                    .checkbox(label)
                    .checked(isSelected)
                    .onChanged(v -> onToggle.accept(id))
                    .sizing(Sizing.fill(100), Sizing.fixed(ROW_HEIGHT))
                    .margins(Insets.of(0));

            row.child(checkbox);

            if (isSelected) {
                EnchantFilterState.LevelRange range = selected.getOrDefault(id, new EnchantFilterState.LevelRange(1, maxLevel));
                row.child(levelControl(id, range, maxLevel));
            }

            list.child(row);
        }

        root.child(list);
    }

    private FlowLayout levelControl(ResourceLocation id, EnchantFilterState.LevelRange range, int maxLevel) {
        FlowLayout control = (FlowLayout) Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(LEVEL_BUTTON_SIZE))
                .gap(GAP)
                .verticalAlignment(VerticalAlignment.CENTER);

        FlowLayout minGroup = numberGroup(id, range, maxLevel, true);
        LabelComponent separator = Components.label(Component.literal("~").withStyle(ChatFormatting.GRAY));
        FlowLayout maxGroup = numberGroup(id, range, maxLevel, false);

        control.child(minGroup).child(separator).child(maxGroup);
        return control;
    }

    private FlowLayout numberGroup(ResourceLocation id, EnchantFilterState.LevelRange range, int maxLevel, boolean isMin) {
        FlowLayout group = (FlowLayout) Containers.horizontalFlow(Sizing.content(), Sizing.fixed(LEVEL_BUTTON_SIZE))
                .gap(GAP / 2)
                .verticalAlignment(VerticalAlignment.CENTER);

        int value = isMin ? range.min() : range.max();
        Runnable decrease = () -> adjustRange(id, range.min() - (isMin ? 1 : 0), range.max() - (isMin ? 0 : 1), maxLevel);
        Runnable increase = () -> adjustRange(id, range.min() + (isMin ? 1 : 0), range.max() + (isMin ? 0 : 1), maxLevel);

        LabelComponent valueLabel = Components.label(Component.literal(String.valueOf(value)).withStyle(ChatFormatting.WHITE));
        valueLabel.sizing(Sizing.fixed(16), Sizing.fixed(LEVEL_BUTTON_SIZE));

        group.child(smallButton("-", decrease))
             .child(valueLabel)
             .child(smallButton("+", increase));
        return group;
    }

    private ButtonComponent smallButton(String text, Runnable action) {
        return (ButtonComponent) Components.button(Component.literal(text), b -> action.run())
                .sizing(Sizing.fixed(LEVEL_BUTTON_SIZE), Sizing.fixed(LEVEL_BUTTON_SIZE));
    }

    private void adjustRange(ResourceLocation id, int newMin, int newMax, int maxLevel) {
        int min = Math.max(1, Math.min(newMin, maxLevel));
        int max = Math.max(1, Math.min(newMax, maxLevel));
        if (min > max) {
            int tmp = min;
            min = max;
            max = tmp;
        }
        state.setRange(id, min, max);
        onRangeChanged.run();
    }
}
