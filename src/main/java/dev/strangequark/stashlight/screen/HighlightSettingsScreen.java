package dev.strangequark.stashlight.screen;

import dev.strangequark.stashlight.config.Config;
import dev.strangequark.stashlight.config.Config.ProximityScanConfig;
import dev.strangequark.stashlight.config.Config.VanillaFallbackConfig;
import dev.strangequark.stashlight.config.Config.LiveSlotHighlightConfig;
import dev.strangequark.stashlight.config.Config.SearchInventoryBarConfig;
import dev.strangequark.stashlight.config.Config.RemoteTakeConfig;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.CheckboxComponent;
import io.wispforest.owo.ui.component.DiscreteSliderComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

import static dev.strangequark.stashlight.gui.UIStyle.*;

/**
 * Screen for configuring highlight layers: toggles, colors, pulse and duration.
 */
public final class HighlightSettingsScreen extends BaseOwoScreen<FlowLayout> {

    private final Screen parent;
    private final Config.HighlightConfig cfg = Config.get().highlight();

    public HighlightSettingsScreen(Screen parent) {
        this.parent = parent;
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        rootComponent.gap(GAP);
        rootComponent.horizontalAlignment(HorizontalAlignment.CENTER);
        rootComponent.verticalAlignment(VerticalAlignment.TOP);
        rootComponent.padding(Insets.of(PADDING));

        LabelComponent title = Components.label(Component.translatable("screen.stashlight.highlightSettings"))
                .shadow(true);

        FlowLayout content = (FlowLayout) Containers.verticalFlow(Sizing.fill(100), Sizing.content())
                .gap(GAP * 2)
                .padding(Insets.of(PADDING));

        // --- General ---
        content.child(sectionLabel("gui.stashlight.label.highlightGeneral"));

        CheckboxComponent pulseCheckbox = (CheckboxComponent) Components
                .checkbox(Component.translatable("gui.stashlight.label.highlightPulse"))
                .checked(cfg.guiSlotPulse())
                .onChanged(v -> {
                    cfg.setGuiSlotPulse(v);
                    Config.save();
                })
                .margins(Insets.top(BORDER));
        content.child(pulseCheckbox);

        DiscreteSliderComponent durationSlider = Components.discreteSlider(Sizing.fixed(SLIDER_WIDTH), 1, 60);
        durationSlider.snap(true).decimalPlaces(0);
        durationSlider.setFromDiscreteValue(cfg.guiSlotDisplayTimeSeconds());
        durationSlider.message(s -> Component.translatable("gui.stashlight.label.highlightDuration",
                cfg.guiSlotDisplayTimeSeconds()));
        durationSlider.onChanged().subscribe(v -> {
            int seconds = (int) Math.round(v);
            if (seconds == cfg.guiSlotDisplayTimeSeconds()) return;
            cfg.setGuiSlotDisplayTimeSeconds(seconds);
            Config.save();
            durationSlider.message(s -> Component.translatable("gui.stashlight.label.highlightDuration",
                    cfg.guiSlotDisplayTimeSeconds()));
        });
        content.child(durationSlider);

        // --- Block Outline ---
        content.child(sectionLabel("gui.stashlight.label.highlightBlockOutline"));
        content.child(colorRow(
                Component.translatable("gui.stashlight.label.enabled"),
                cfg.blockOutlineEnabled(), cfg.blockOutlineColor(),
                v -> {
                    cfg.setBlockOutlineEnabled(v);
                    Config.save();
                },
                c -> {
                    cfg.setBlockOutlineColor(c);
                    Config.save();
                }
        ));

        // --- GUI Slot ---
        content.child(sectionLabel("gui.stashlight.label.highlightGuiSlot"));
        content.child(colorRow(
                Component.translatable("gui.stashlight.label.enabled"),
                cfg.guiSlotEnabled(), cfg.guiSlotColor(),
                v -> {
                    cfg.setGuiSlotEnabled(v);
                    Config.save();
                },
                c -> {
                    cfg.setGuiSlotColor(c);
                    Config.save();
                }
        ));

        // --- Nested Box ---
        content.child(sectionLabel("gui.stashlight.label.highlightNestedBox"));
        content.child(colorRow(
                Component.translatable("gui.stashlight.label.enabled"),
                cfg.nestedBoxEnabled(), cfg.nestedBoxColor(),
                v -> {
                    cfg.setNestedBoxEnabled(v);
                    Config.save();
                },
                c -> {
                    cfg.setNestedBoxColor(c);
                    Config.save();
                }
        ));

        // --- World Marker Beam ---
        content.child(sectionLabel("gui.stashlight.label.highlightWorldMarkerBeam"));
        content.child(colorRow(
                Component.translatable("gui.stashlight.label.enabled"),
                cfg.worldMarkerBeamEnabled(), cfg.worldMarkerBeamColor(),
                v -> {
                    cfg.setWorldMarkerBeamEnabled(v);
                    Config.save();
                },
                c -> {
                    cfg.setWorldMarkerBeamColor(c);
                    Config.save();
                }
        ));
        CheckboxComponent beamThroughWalls = (CheckboxComponent) Components
                .checkbox(Component.translatable("gui.stashlight.label.highlightBeamThroughWalls"))
                .checked(cfg.worldMarkerBeamThroughWalls())
                .onChanged(v -> {
                    cfg.setWorldMarkerBeamThroughWalls(v);
                    Config.save();
                })
                .margins(Insets.left(20));
        content.child(beamThroughWalls);

        // --- World Marker Box ---
        content.child(sectionLabel("gui.stashlight.label.highlightWorldMarkerBox"));
        content.child(colorRow(
                Component.translatable("gui.stashlight.label.enabled"),
                cfg.worldMarkerBoxEnabled(), cfg.worldMarkerBoxColor(),
                v -> {
                    cfg.setWorldMarkerBoxEnabled(v);
                    Config.save();
                },
                c -> {
                    cfg.setWorldMarkerBoxColor(c);
                    Config.save();
                }
        ));

        // ── v1.3 Features ──────────────────────────────────────────────

        content.child(sectionLabel("screen.stashlight.v1_3Settings"));

        // 1. Proximity Scan (modded)
        var ps = Config.get().proximityScan();
        content.child(makeCheckbox("gui.stashlight.label.proximityScan",
                ps.enabled(), v -> { ps.setEnabled(v); Config.save(); }));
        content.child(makeDiscreteSlider("gui.stashlight.label.proximityScanRadius",
                ps.radius(), 4, 32, v -> { ps.setRadius(v); Config.save(); }));
        content.child(makeDiscreteSlider("gui.stashlight.label.containerThreshold",
                ps.containerThreshold(), 1, 64, v -> { ps.setContainerThreshold(v); Config.save(); }));
        content.child(makeDiscreteSlider("gui.stashlight.label.scanInterval",
                ps.scanIntervalSeconds(), 1, 60, v -> { ps.setScanIntervalSeconds(v); Config.save(); }));

        // 2. Vanilla-fallback Scanner
        var vf = Config.get().vanillaFallback();
        content.child(makeCheckbox("gui.stashlight.label.vanillaFallback",
                vf.enabled(), v -> { vf.setEnabled(v); Config.save(); }));
        content.child(makeDiscreteSlider("gui.stashlight.label.loopInterval",
                vf.loopIntervalMillis(), 100, 5000, 100,
                v -> { vf.setLoopIntervalMillis(v); Config.save(); }));
        content.child(makeDiscreteSlider("gui.stashlight.label.maxContainersPerLoop",
                vf.maxContainersPerLoop(), 1, 256, v -> { vf.setMaxContainersPerLoop(v); Config.save(); }));

        // 3. Live Slot Highlight
        content.child(makeCheckbox("gui.stashlight.label.liveSlotHighlight",
                Config.get().liveSlotHighlight().enabled(),
                v -> { Config.get().liveSlotHighlight().setEnabled(v); Config.save(); }));

        // 4. Inventory Bar
        content.child(makeCheckbox("gui.stashlight.label.inventoryBar",
                Config.get().searchInventoryBar().enabled(),
                v -> { Config.get().searchInventoryBar().setEnabled(v); Config.save(); }));

        // 5. Remote Take
        var rt = Config.get().remoteTake();
        content.child(makeCheckbox("gui.stashlight.label.remoteTake",
                rt.enabled(), v -> { rt.setEnabled(v); Config.save(); }));
        content.child(makeDiscreteSlider("gui.stashlight.label.defaultQuantity",
                rt.defaultQuantity(), 1, 64, v -> { rt.setDefaultQuantity(v); Config.save(); }));

        ScrollContainer<FlowLayout> scroll = Containers
                .verticalScroll(Sizing.fill(100), Sizing.expand(100), content)
                .scrollbarThiccness(SCROLL_WIDTH)
                .scrollbar(ScrollContainer.Scrollbar.vanillaFlat());

        var doneButton = Components.button(Component.translatable("gui.done"), b -> onClose())
                .sizing(Sizing.fixed(80), Sizing.fixed(COMPONENT_HEIGHT));

        rootComponent.child(title).child(scroll).child(doneButton);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
    }

    private static LabelComponent sectionLabel(String key) {
        return (LabelComponent) Components.label(Component.translatable(key)).shadow(false);
    }

    private static FlowLayout colorRow(Component label, boolean enabled, int color,
                                       Consumer<Boolean> onToggle, Consumer<String> onColorChange) {
        FlowLayout row = (FlowLayout) Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(COMPONENT_HEIGHT))
                .gap(GAP)
                .alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        CheckboxComponent checkbox = (CheckboxComponent) Components
                .checkbox(label)
                .checked(enabled)
                .onChanged(v -> {
                    onToggle.accept(v);
                    Config.save();
                })
                .sizing(Sizing.content(), Sizing.fixed(COMPONENT_HEIGHT));

        String hex = String.format("#%08X", color);
        TextBoxComponent textBox = Components.textBox(Sizing.fixed(80), hex);
        textBox.setMaxLength(10);

        FlowLayout preview = (FlowLayout) Containers.verticalFlow(Sizing.fixed(16), Sizing.fixed(16))
                .surface(Surface.flat(color))
                .padding(Insets.of(0));

        textBox.onChanged().subscribe(s -> {
            Integer parsed = parseHexColor(s);
            if (parsed != null) {
                preview.surface(Surface.flat(parsed));
                onColorChange.accept(s);
                Config.save();
            } else {
                preview.surface(Surface.flat(0xFF555555));
            }
        });

        row.child(checkbox).child(textBox).child(preview);
        return row;
    }

    /**
     * Create a simple enabled/disabled checkbox row.
     */
    private static FlowLayout makeCheckbox(String labelKey, boolean checked,
                                           java.util.function.Consumer<Boolean> onChanged) {
        FlowLayout row = (FlowLayout) Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(COMPONENT_HEIGHT))
                .gap(GAP)
                .alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        CheckboxComponent checkbox = (CheckboxComponent) Components
                .checkbox(Component.translatable(labelKey))
                .checked(checked)
                .onChanged(v -> {
                    onChanged.accept(v);
                    Config.save();
                })
                .sizing(Sizing.content(), Sizing.fixed(COMPONENT_HEIGHT));

        row.child(checkbox);
        return row;
    }

    /**
     * Create a labelled discrete slider row (step=1).
     */
    private static FlowLayout makeDiscreteSlider(String labelKey, int value,
                                                  int min, int max,
                                                  java.util.function.Consumer<Integer> onChanged) {
        FlowLayout row = (FlowLayout) Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(COMPONENT_HEIGHT))
                .gap(GAP)
                .alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        row.child(Components.label(Component.translatable(labelKey)).shadow(true));

        int steps = max - min;
        DiscreteSliderComponent slider = Components.discreteSlider(Sizing.fixed(SLIDER_WIDTH), 0, steps);
        slider.snap(true).decimalPlaces(0);
        slider.setFromDiscreteValue(value - min);
        slider.onChanged().subscribe(v -> {
            int actual = min + (int) Math.round(v);
            if (actual == value) return;
            onChanged.accept(actual);
        });

        row.child(slider);
        return row;
    }

    /**
     * Create a labelled discrete slider row with a custom step.
     */
    private static FlowLayout makeDiscreteSlider(String labelKey, int value,
                                                  int min, int max, int step,
                                                  java.util.function.Consumer<Integer> onChanged) {
        FlowLayout row = (FlowLayout) Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(COMPONENT_HEIGHT))
                .gap(GAP)
                .alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        row.child(Components.label(Component.translatable(labelKey)).shadow(true));

        int steps = (max - min) / step;
        DiscreteSliderComponent slider = Components.discreteSlider(Sizing.fixed(SLIDER_WIDTH), 0, steps);
        slider.snap(true).decimalPlaces(0);
        slider.setFromDiscreteValue((value - min) / step);
        slider.onChanged().subscribe(v -> {
            int actual = min + (int) Math.round(v) * step;
            if (actual == value) return;
            onChanged.accept(actual);
        });

        row.child(slider);
        return row;
    }

    private static Integer parseHexColor(String value) {
        if (value == null) return null;
        String s = value.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.isEmpty()) return null;
        try {
            return (int) Long.parseLong(s, 16);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
