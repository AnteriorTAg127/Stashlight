package dev.strangequark.stashlight.screen;

import dev.strangequark.stashlight.config.Config;
import dev.strangequark.stashlight.config.Config.VanillaFallbackConfig;
import dev.strangequark.stashlight.scan.ComboKeybind;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.CheckboxComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.DiscreteSliderComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static dev.strangequark.stashlight.gui.UIStyle.*;

/**
 * Screen for configuring highlight layers: toggles, colors, pulse and duration.
 * v1.3 additions: proximity scan, vanilla-fallback, live highlight, inventory bar, remote take,
 * and combo keybind picker for vanilla scanner.
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
        rootComponent.surface(Surface.VANILLA_TRANSLUCENT);

        LabelComponent title = Components.label(Component.translatable("screen.stashlight.highlightSettings"))
                .shadow(true);

        FlowLayout content = (FlowLayout) Containers.verticalFlow(Sizing.fill(100), Sizing.content())
                .gap(GAP * 2)
                .padding(Insets.of(PADDING));

        // --- General ---
        content.child(sectionCard("gui.stashlight.label.highlightGeneral", card -> {
            card.child(makeCheckbox("gui.stashlight.label.highlightPulse",
                    cfg.guiSlotPulse(), v -> { cfg.setGuiSlotPulse(v); Config.save(); }));
            card.child(makeSlider("gui.stashlight.label.highlightDuration",
                    cfg.guiSlotDisplayTimeSeconds(), 1, 60,
                    v -> { cfg.setGuiSlotDisplayTimeSeconds(v); Config.save(); }));
        }));

        // --- Block Outline ---
        content.child(sectionCard("gui.stashlight.label.highlightBlockOutline", card -> {
            card.child(colorRow(
                    Component.translatable("gui.stashlight.label.enabled"),
                    cfg.blockOutlineEnabled(), cfg.blockOutlineColor(),
                    v -> { cfg.setBlockOutlineEnabled(v); Config.save(); },
                    c -> { cfg.setBlockOutlineColor(c); Config.save(); }
            ));
        }));

        // --- GUI Slot ---
        content.child(sectionCard("gui.stashlight.label.highlightGuiSlot", card -> {
            card.child(colorRow(
                    Component.translatable("gui.stashlight.label.enabled"),
                    cfg.guiSlotEnabled(), cfg.guiSlotColor(),
                    v -> { cfg.setGuiSlotEnabled(v); Config.save(); },
                    c -> { cfg.setGuiSlotColor(c); Config.save(); }
            ));
        }));

        // --- Nested Box ---
        content.child(sectionCard("gui.stashlight.label.highlightNestedBox", card -> {
            card.child(colorRow(
                    Component.translatable("gui.stashlight.label.enabled"),
                    cfg.nestedBoxEnabled(), cfg.nestedBoxColor(),
                    v -> { cfg.setNestedBoxEnabled(v); Config.save(); },
                    c -> { cfg.setNestedBoxColor(c); Config.save(); }
            ));
        }));

        // --- World Marker Beam ---
        content.child(sectionCard("gui.stashlight.label.highlightWorldMarkerBeam", card -> {
            card.child(colorRow(
                    Component.translatable("gui.stashlight.label.enabled"),
                    cfg.worldMarkerBeamEnabled(), cfg.worldMarkerBeamColor(),
                    v -> { cfg.setWorldMarkerBeamEnabled(v); Config.save(); },
                    c -> { cfg.setWorldMarkerBeamColor(c); Config.save(); }
            ));
            card.child(makeCheckbox("gui.stashlight.label.highlightBeamThroughWalls",
                    cfg.worldMarkerBeamThroughWalls(),
                    v -> { cfg.setWorldMarkerBeamThroughWalls(v); Config.save(); }));
        }));

        // --- World Marker Box ---
        content.child(sectionCard("gui.stashlight.label.highlightWorldMarkerBox", card -> {
            card.child(colorRow(
                    Component.translatable("gui.stashlight.label.enabled"),
                    cfg.worldMarkerBoxEnabled(), cfg.worldMarkerBoxColor(),
                    v -> { cfg.setWorldMarkerBoxEnabled(v); Config.save(); },
                    c -> { cfg.setWorldMarkerBoxColor(c); Config.save(); }
            ));
        }));

        // ── v1.3 Features ──────────────────────────────────────────────

        content.child(sectionLabel("screen.stashlight.v1_3Settings"));

        // 1. Proximity Scan (modded)
        var ps = Config.get().proximityScan();
        content.child(sectionCard("gui.stashlight.label.proximityScan", card -> {
            card.child(makeCheckbox("gui.stashlight.label.enabled",
                    ps.enabled(), v -> { ps.setEnabled(v); Config.save(); }));
            card.child(makeSlider("gui.stashlight.label.proximityScanRadius",
                    ps.radius(), 4, 32, v -> { ps.setRadius(v); Config.save(); }));
            card.child(makeSlider("gui.stashlight.label.containerThreshold",
                    ps.containerThreshold(), 1, 64, v -> { ps.setContainerThreshold(v); Config.save(); }));
            card.child(makeSlider("gui.stashlight.label.scanInterval",
                    ps.scanIntervalSeconds(), 1, 60, v -> { ps.setScanIntervalSeconds(v); Config.save(); }));
        }));

        // 2. Vanilla-fallback Scanner (key is set in Controls menu)
        var vf = Config.get().vanillaFallback();

        content.child(sectionCard("gui.stashlight.label.vanillaFallback", card -> {
            card.child(makeCheckbox("gui.stashlight.label.enabled",
                    vf.enabled(), v -> { vf.setEnabled(v); Config.save(); }));
            card.child(makeSlider("gui.stashlight.label.loopInterval",
                    vf.loopIntervalMillis(), 100, 5000, 100,
                    v -> { vf.setLoopIntervalMillis(v); Config.save(); }));
            card.child(makeSlider("gui.stashlight.label.maxContainersPerLoop",
                    vf.maxContainersPerLoop(), 1, 256, v -> { vf.setMaxContainersPerLoop(v); Config.save(); }));
            card.child(buildBlockFilterRow(vf));
            // Combo keybind picker
            card.child(buildComboKeybindPicker(vf));
        }));

        // 2b. Vanilla-fallback Taker
        content.child(sectionCard("gui.stashlight.label.vanillaFallbackTaker", card -> {
            card.child(makeSlider("gui.stashlight.label.takeLoopInterval",
                    vf.takeIntervalMillis(), 100, 5000, 100,
                    v -> { vf.setTakeIntervalMillis(v); Config.save(); }));
            card.child(makeCheckbox("gui.stashlight.label.takeOnlyIndexed",
                    vf.takeOnlyIndexed(), v -> { vf.setTakeOnlyIndexed(v); Config.save(); }));
        }));

        // 3. Live Slot Highlight
        content.child(sectionCard("gui.stashlight.label.liveSlotHighlight", card -> {
            card.child(makeCheckbox("gui.stashlight.label.enabled",
                    Config.get().liveSlotHighlight().enabled(),
                    v -> { Config.get().liveSlotHighlight().setEnabled(v); Config.save(); }));
        }));

        // 4. Inventory Bar
        content.child(sectionCard("gui.stashlight.label.inventoryBar", card -> {
            card.child(makeCheckbox("gui.stashlight.label.enabled",
                    Config.get().searchInventoryBar().enabled(),
                    v -> { Config.get().searchInventoryBar().setEnabled(v); Config.save(); }));
        }));

        // 4b. Take Queue
        var tq = Config.get().takeQueue();
        content.child(sectionCard("gui.stashlight.label.takeQueue", card -> {
            card.child(makeCheckbox("gui.stashlight.label.enabled",
                    tq.enabled(), v -> { tq.setEnabled(v); Config.save(); }));
            card.child(makeSlider("gui.stashlight.label.queueCapacity",
                    tq.capacity(), 1, 64, v -> { tq.setCapacity(v); Config.save(); }));
        }));

        // 5. Remote Take
        var rt = Config.get().remoteTake();
        content.child(sectionCard("gui.stashlight.label.remoteTake", card -> {
            card.child(makeCheckbox("gui.stashlight.label.enabled",
                    rt.enabled(), v -> { rt.setEnabled(v); Config.save(); }));
            card.child(makeSlider("gui.stashlight.label.defaultQuantity",
                    rt.defaultQuantity(), 1, 64, v -> { rt.setDefaultQuantity(v); Config.save(); }));
            card.child(makeCheckbox("gui.stashlight.label.takeContainingBox",
                    rt.takeContainingBoxEnabled(), v -> { rt.setTakeContainingBoxEnabled(v); Config.save(); }));
        }));

        // 6. Crafting
        var cr = Config.get().crafting();
        content.child(sectionCard("gui.stashlight.label.crafting", card -> {
            card.child(makeCheckbox("gui.stashlight.label.enabled",
                    cr.enabled(), v -> { cr.setEnabled(v); Config.save(); }));
            card.child(makeCheckbox("gui.stashlight.label.showAllRecipes",
                    cr.showAllRecipes(), v -> { cr.setShowAllRecipes(v); Config.save(); }));
            card.child(makeSlider("gui.stashlight.label.maxCraftQuantity",
                    cr.maxCraftQuantity(), 1, 9999,
                    v -> { cr.setMaxCraftQuantity(v); Config.save(); }));
            card.child(makeCheckbox("gui.stashlight.label.takeBeforeCraft",
                    cr.takeBeforeCraft(), v -> { cr.setTakeBeforeCraft(v); Config.save(); }));
            card.child(makeCheckbox("gui.stashlight.label.keepScreenOnCraft",
                    cr.keepScreenOnCraft(), v -> { cr.setKeepScreenOnCraft(v); Config.save(); }));
            card.child(makeCheckbox("gui.stashlight.label.recoverDroppedMaterials",
                    cr.recoverDroppedMaterials(), v -> { cr.setRecoverDroppedMaterials(v); Config.save(); }));
            card.child(makeCheckbox("gui.stashlight.label.waitManualPickup",
                    cr.waitManualPickup(), v -> { cr.setWaitManualPickup(v); Config.save(); }));
        }));

        ScrollContainer<FlowLayout> scroll = Containers
                .verticalScroll(Sizing.fill(100), Sizing.expand(100), content)
                .scrollbarThiccness(SCROLL_WIDTH)
                .scrollbar(ScrollContainer.Scrollbar.vanillaFlat());

        var doneButton = Components.button(Component.translatable("gui.done"), b -> onClose())
                .sizing(Sizing.fixed(80), Sizing.fixed(COMPONENT_HEIGHT));

        rootComponent.child(title).child(scroll).child(doneButton);
    }

    // ── Combo keybind capture ─────────────────────────────────────────

    private boolean capturingKey = false;
    private ButtonComponent captureKeyBtn;

    /**
     * Build a combo keybind picker: a button showing the current key + modifiers.
     * Click to enter capture mode, press any key to bind, Esc to cancel.
     */
    private FlowLayout buildComboKeybindPicker(VanillaFallbackConfig vf) {
        FlowLayout row = (FlowLayout) Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(COMPONENT_HEIGHT))
                .gap(GAP)
                .alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        row.child(Components.label(
                Component.translatable("gui.stashlight.label.scanComboKey")
        ).shadow(true));

        captureKeyBtn = (ButtonComponent) Components.button(Component.literal(getCaptureDisplay(vf)), b -> {
            if (capturingKey) {
                // Cancel capture
                capturingKey = false;
                captureKeyBtn.setMessage(Component.literal(getCaptureDisplay(vf)));
            } else {
                capturingKey = true;
                captureKeyBtn.setMessage(Component.translatable("gui.stashlight.label.pressAnyKey"));
            }
        }).sizing(Sizing.fixed(140), Sizing.fixed(COMPONENT_HEIGHT));

        row.child(captureKeyBtn);
        return row;
    }

    private static String getCaptureDisplay(VanillaFallbackConfig vf) {
        String keyName = getKeyDisplayName(vf.scanComboKey());
        String mods = vf.scanComboMods();
        if (mods != null && !mods.isBlank()) {
            return mods + "+" + keyName;
        }
        return keyName;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (capturingKey) {
            int keyCode = event.key();
            int scanCode = event.scancode();
            int modifiers = event.modifiers();

            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                capturingKey = false;
                captureKeyBtn.setMessage(Component.literal(getCaptureDisplay(Config.get().vanillaFallback())));
                return true;
            }

            // Build modifier list from GLFW modifier bit flags
            StringBuilder modsBuilder = new StringBuilder();
            if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) modsBuilder.append("CTRL,");
            if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) modsBuilder.append("SHIFT,");
            if ((modifiers & GLFW.GLFW_MOD_ALT) != 0) modsBuilder.append("ALT,");

            String keyName = ComboKeybind.keyCodeToName(keyCode, scanCode);
            // Don't bind a modifier key alone as the main key
            if ("NONE".equals(keyName) || isModifierKey(keyCode)) {
                return true;
            }

            String modsStr = modsBuilder.length() > 0
                    ? modsBuilder.substring(0, modsBuilder.length() - 1)
                    : "";

            var vf = Config.get().vanillaFallback();
            vf.setScanComboKey(keyName);
            vf.setScanComboMods(modsStr);
            Config.save();

            // Reload the ComboKeybind in the scanner
            var stashlight = dev.strangequark.stashlight.Stashlight.getInstance();
            if (stashlight != null) {
                var scanner = stashlight.getVanillaScanner();
                if (scanner != null) scanner.reloadKeybind();
            }

            capturingKey = false;
            captureKeyBtn.setMessage(Component.literal(getCaptureDisplay(vf)));
            return true;
        }
        return super.keyPressed(event);
    }

    private static boolean isModifierKey(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_LEFT_CONTROL
                || keyCode == GLFW.GLFW_KEY_RIGHT_CONTROL
                || keyCode == GLFW.GLFW_KEY_LEFT_SHIFT
                || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT
                || keyCode == GLFW.GLFW_KEY_LEFT_ALT
                || keyCode == GLFW.GLFW_KEY_RIGHT_ALT;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        // Block text input while capturing a keybind
        if (capturingKey) return true;
        return super.charTyped(event);
    }

    private static String getKeyDisplayName(String keyName) {
        if (keyName == null || "NONE".equals(keyName)) return "None";
        return keyName;
    }

    /**
     * Build a block filter row with checkboxes for common container types.
     */
    private FlowLayout buildBlockFilterRow(VanillaFallbackConfig vf) {
        FlowLayout row = (FlowLayout) Containers.verticalFlow(Sizing.fill(100), Sizing.content())
                .gap(GAP);

        row.child(Components.label(
                Component.translatable("gui.stashlight.label.blockFilter")
        ).shadow(true));

        FlowLayout checkboxRow = (FlowLayout) Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(COMPONENT_HEIGHT))
                .gap(GAP)
                .alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        String currentFilter = vf.blockFilter();
        Set<String> allowed = new HashSet<>();
        if (currentFilter != null && !currentFilter.isBlank()) {
            allowed.addAll(Arrays.asList(currentFilter.toLowerCase().split(",")));
        }

        String[][] blockOptions = {
            {"chest", "gui.stashlight.blockFilter.chest"},
            {"barrel", "gui.stashlight.blockFilter.barrel"},
            {"shulker_box", "gui.stashlight.blockFilter.shulker_box"},
            {"trapped_chest", "gui.stashlight.blockFilter.trapped_chest"},
            {"hopper", "gui.stashlight.blockFilter.hopper"},
            {"dispenser", "gui.stashlight.blockFilter.dispenser"},
            {"dropper", "gui.stashlight.blockFilter.dropper"},
        };

        for (String[] opt : blockOptions) {
            CheckboxComponent cb = makeModCheckbox(opt[1], allowed.contains(opt[0]), v -> {
                updateBlockFilter(vf, opt[0], v);
            });
            checkboxRow.child(cb);
        }

        row.child(checkboxRow);
        return row;
    }

    private static void updateBlockFilter(VanillaFallbackConfig vf, String blockId, boolean add) {
        String raw = vf.blockFilter();
        Set<String> blocks = new HashSet<>();
        if (raw != null && !raw.isBlank()) {
            blocks.addAll(Arrays.asList(raw.toLowerCase().split(",")));
        }
        if (add) {
            blocks.add(blockId);
        } else {
            blocks.remove(blockId);
        }
        vf.setBlockFilter(String.join(",", blocks));
        Config.save();
    }

    private static void updateMods(VanillaFallbackConfig vf, String mod, boolean add) {
        String raw = vf.scanComboMods();
        Set<String> mods = new HashSet<>();
        if (raw != null && !raw.isBlank()) {
            mods.addAll(Arrays.asList(raw.toUpperCase().split(",")));
        }
        if (add) {
            mods.add(mod);
        } else {
            mods.remove(mod);
        }
        vf.setScanComboMods(String.join(",", mods));
        Config.save();
    }

    // ── Section card helper ───────────────────────────────────────────

    /**
     * Create a visually grouped card section with title label and indented content.
     */
    private static FlowLayout sectionCard(String titleKey, Consumer<FlowLayout> contentBuilder) {
        FlowLayout card = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        card.gap(GAP);
        card.surface(Surface.outline(GRID_BORDER));
        card.padding(Insets.of(PADDING));

        LabelComponent sectionTitle = (LabelComponent) Components.label(Component.translatable(titleKey))
                .shadow(true);
        card.child(sectionTitle);

        FlowLayout inner = (FlowLayout) Containers.verticalFlow(Sizing.fill(100), Sizing.content())
                .gap(GAP)
                .margins(Insets.left(PADDING));
        contentBuilder.accept(inner);
        card.child(inner);

        return card;
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

    private static FlowLayout makeCheckbox(String labelKey, boolean checked,
                                           Consumer<Boolean> onChanged) {
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

    // ── Slider helpers ────────────────────────────────────────────────

    /**
     * Create a labelled discrete slider row with live value display (step=1).
     */
    private static FlowLayout makeSlider(String labelKey, int value,
                                          int min, int max,
                                          Consumer<Integer> onChanged) {
        return makeSlider(labelKey, value, min, max, 1, onChanged);
    }

    /**
     * Create a labelled discrete slider row with live value display and custom step.
     */
    private static FlowLayout makeSlider(String labelKey, int value,
                                          int min, int max, int step,
                                          Consumer<Integer> onChanged) {
        FlowLayout row = (FlowLayout) Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(COMPONENT_HEIGHT))
                .gap(GAP)
                .alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        row.child(Components.label(Component.translatable(labelKey)).shadow(true));

        LabelComponent valueLabel = (LabelComponent) Components.label(
                Component.literal(String.valueOf(value))
        ).shadow(true);
        row.child(valueLabel);

        int steps = (max - min) / step;
        DiscreteSliderComponent slider = Components.discreteSlider(Sizing.fixed(SLIDER_WIDTH), 0, steps);
        slider.snap(true).decimalPlaces(0);
        slider.setFromDiscreteValue((value - min) / step);
        slider.onChanged().subscribe(v -> {
            int actual = min + (int) Math.round(v) * step;
            if (actual == value) return;
            valueLabel.text(Component.literal(String.valueOf(actual)));
            onChanged.accept(actual);
        });

        row.child(slider);
        return row;
    }

    private static CheckboxComponent makeModCheckbox(String text, boolean checked, Consumer<Boolean> onChanged) {
        return (CheckboxComponent) Components
                .checkbox(Component.translatable(text))
                .checked(checked)
                .onChanged(onChanged)
                .sizing(Sizing.content(), Sizing.fixed(COMPONENT_HEIGHT));
    }

    private static CheckboxComponent makeLiteralCheckbox(String text, boolean checked, Consumer<Boolean> onChanged) {
        return (CheckboxComponent) Components
                .checkbox(Component.literal(text))
                .checked(checked)
                .onChanged(onChanged)
                .sizing(Sizing.content(), Sizing.fixed(COMPONENT_HEIGHT));
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
