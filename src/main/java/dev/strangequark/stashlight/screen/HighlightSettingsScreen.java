package dev.strangequark.stashlight.screen;

import dev.strangequark.stashlight.config.Config;
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
