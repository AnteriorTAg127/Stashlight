package dev.strangequark.stashlight.scan;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * A masa-style combo keybind that self-polls GLFW key state each tick via
 * {@link InputConstants#isKeyDown}. Supports a main key + modifier keys
 * (Ctrl, Shift, Alt). Does <em>not</em> use vanilla {@code KeyMapping}, so it
 * avoids conflicts with other mods and supports chord combos.
 * <p>
 * Toggle semantics: when the combo transitions from {@code false} → {@code true}
 * (rising edge), the callback is invoked. The caller decides whether to start or
 * stop the associated action.
 */
public final class ComboKeybind {

    private final int mainKey;
    private final Set<Integer> modifiers;
    private boolean wasPressed = false;

    /**
     * @param mainKey   GLFW key code (e.g. {@link GLFW#GLFW_KEY_K}) or
     *                  {@link GLFW#GLFW_KEY_UNKNOWN} for "unbound"
     * @param modifiers set of modifier key codes, e.g. {@link GLFW#GLFW_KEY_LEFT_CONTROL}
     */
    public ComboKeybind(int mainKey, int... modifiers) {
        this.mainKey = mainKey;
        Set<Integer> mods = new HashSet<>();
        for (int m : modifiers) mods.add(m);
        this.modifiers = mods;
    }

    /**
     * Parse a combo from config strings.
     *
     * @param keyName   GLFW key name (e.g. "K", "F6") or "NONE" for unbound
     * @param modsList  comma-separated modifier names ("CTRL,SHIFT") or empty string
     */
    public static ComboKeybind fromConfig(String keyName, String modsList) {
        int key = GLFW.GLFW_KEY_UNKNOWN;
        if (keyName != null && !"NONE".equalsIgnoreCase(keyName.trim())) {
            key = InputConstants.getKey(keyName).getValue();
        }

        int[] mods = {};
        if (modsList != null && !modsList.isBlank()) {
            mods = Arrays.stream(modsList.split(","))
                    .map(String::trim)
                    .mapToInt(ComboKeybind::modKeyCode)
                    .filter(k -> k != 0)
                    .toArray();
        }

        return new ComboKeybind(key, mods);
    }

    private static int modKeyCode(String name) {
        return switch (name.toUpperCase()) {
            case "CTRL" -> GLFW.GLFW_KEY_LEFT_CONTROL;
            case "SHIFT" -> GLFW.GLFW_KEY_LEFT_SHIFT;
            case "ALT" -> GLFW.GLFW_KEY_LEFT_ALT;
            default -> 0;
        };
    }

    /**
     * Call every {@code END_CLIENT_TICK}. Returns {@code true} on the rising edge
     * (combo transitions from released → pressed). Idempotent within a tick.
     */
    public boolean consumeClick() {
        if (mainKey == GLFW.GLFW_KEY_UNKNOWN) return false;

        Window window = Minecraft.getInstance().getWindow();
        boolean pressed = InputConstants.isKeyDown(window, mainKey) && allModifiersDown(window);

        boolean edge = pressed && !wasPressed;
        wasPressed = pressed;
        return edge;
    }

    /**
     * Returns {@code true} if the combo is currently held (any state, not just
     * rising edge). Useful for interrupt checks.
     */
    public boolean isPressed() {
        if (mainKey == GLFW.GLFW_KEY_UNKNOWN) return false;
        Window window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, mainKey) && allModifiersDown(window);
    }

    private boolean allModifiersDown(Window window) {
        for (int mod : modifiers) {
            if (!InputConstants.isKeyDown(window, mod)) return false;
        }
        return true;
    }

    /**
     * Reset the rising-edge state (e.g. when the scanner stops).
     */
    public void reset() {
        wasPressed = false;
    }
}
