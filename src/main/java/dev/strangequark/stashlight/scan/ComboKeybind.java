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
     * @param keyName  GLFW key name (e.g. "K", "F6") or "NONE" for unbound
     * @param modsList comma-separated modifier names ("CTRL,SHIFT") or empty string
     */
    public static ComboKeybind fromConfig(String keyName, String modsList) {
        int key = parseKeyName(keyName);

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

    /**
     * Convert a GLFW key code + scancode to a human-readable/config-storable key name.
     * This is the inverse of {@link #parseKeyName(String)} for round-trip safety.
     */
    public static String keyCodeToName(int keyCode, int scancode) {
        if (keyCode == GLFW.GLFW_KEY_UNKNOWN) return "NONE";

        // glfwGetKeyName works for letters, numbers, and printable symbols
        String glfwName = GLFW.glfwGetKeyName(keyCode, scancode);
        if (glfwName != null && !glfwName.isEmpty()) {
            return glfwName.toUpperCase();
        }

        // F-keys (GLFW_KEY_F1 through GLFW_KEY_F25 are consecutive)
        if (keyCode >= GLFW.GLFW_KEY_F1 && keyCode <= GLFW.GLFW_KEY_F25) {
            return "F" + (keyCode - GLFW.GLFW_KEY_F1 + 1);
        }

        return switch (keyCode) {
            case GLFW.GLFW_KEY_SPACE -> "SPACE";
            case GLFW.GLFW_KEY_ESCAPE -> "ESCAPE";
            case GLFW.GLFW_KEY_ENTER -> "ENTER";
            case GLFW.GLFW_KEY_TAB -> "TAB";
            case GLFW.GLFW_KEY_BACKSPACE -> "BACKSPACE";
            case GLFW.GLFW_KEY_DELETE -> "DELETE";
            case GLFW.GLFW_KEY_INSERT -> "INSERT";
            case GLFW.GLFW_KEY_HOME -> "HOME";
            case GLFW.GLFW_KEY_END -> "END";
            case GLFW.GLFW_KEY_PAGE_UP -> "PAGE_UP";
            case GLFW.GLFW_KEY_PAGE_DOWN -> "PAGE_DOWN";
            case GLFW.GLFW_KEY_UP -> "UP";
            case GLFW.GLFW_KEY_DOWN -> "DOWN";
            case GLFW.GLFW_KEY_LEFT -> "LEFT";
            case GLFW.GLFW_KEY_RIGHT -> "RIGHT";
            case GLFW.GLFW_KEY_CAPS_LOCK -> "CAPS_LOCK";
            case GLFW.GLFW_KEY_SCROLL_LOCK -> "SCROLL_LOCK";
            case GLFW.GLFW_KEY_NUM_LOCK -> "NUM_LOCK";
            case GLFW.GLFW_KEY_PRINT_SCREEN -> "PRINT_SCREEN";
            case GLFW.GLFW_KEY_PAUSE -> "PAUSE";
            case GLFW.GLFW_KEY_KP_0 -> "KP_0";
            case GLFW.GLFW_KEY_KP_1 -> "KP_1";
            case GLFW.GLFW_KEY_KP_2 -> "KP_2";
            case GLFW.GLFW_KEY_KP_3 -> "KP_3";
            case GLFW.GLFW_KEY_KP_4 -> "KP_4";
            case GLFW.GLFW_KEY_KP_5 -> "KP_5";
            case GLFW.GLFW_KEY_KP_6 -> "KP_6";
            case GLFW.GLFW_KEY_KP_7 -> "KP_7";
            case GLFW.GLFW_KEY_KP_8 -> "KP_8";
            case GLFW.GLFW_KEY_KP_9 -> "KP_9";
            case GLFW.GLFW_KEY_KP_DECIMAL -> "KP_DECIMAL";
            case GLFW.GLFW_KEY_KP_DIVIDE -> "KP_DIVIDE";
            case GLFW.GLFW_KEY_KP_MULTIPLY -> "KP_MULTIPLY";
            case GLFW.GLFW_KEY_KP_SUBTRACT -> "KP_SUBTRACT";
            case GLFW.GLFW_KEY_KP_ADD -> "KP_ADD";
            case GLFW.GLFW_KEY_KP_ENTER -> "KP_ENTER";
            case GLFW.GLFW_KEY_COMMA -> "COMMA";
            case GLFW.GLFW_KEY_PERIOD -> "PERIOD";
            case GLFW.GLFW_KEY_SLASH -> "SLASH";
            case GLFW.GLFW_KEY_SEMICOLON -> "SEMICOLON";
            case GLFW.GLFW_KEY_APOSTROPHE -> "APOSTROPHE";
            case GLFW.GLFW_KEY_MINUS -> "MINUS";
            case GLFW.GLFW_KEY_EQUAL -> "EQUALS";
            case GLFW.GLFW_KEY_LEFT_BRACKET -> "LEFT_BRACKET";
            case GLFW.GLFW_KEY_RIGHT_BRACKET -> "RIGHT_BRACKET";
            case GLFW.GLFW_KEY_BACKSLASH -> "BACKSLASH";
            case GLFW.GLFW_KEY_GRAVE_ACCENT -> "GRAVE";
            default -> "NONE";
        };
    }

    /**
     * Parse a key name from config (inverse of {@link #keyCodeToName}).
     */
    static int parseKeyName(String name) {
        if (name == null || "NONE".equalsIgnoreCase(name.trim())) return GLFW.GLFW_KEY_UNKNOWN;
        name = name.trim().toUpperCase();

        // Single character: letter or digit
        if (name.length() == 1) {
            char c = name.charAt(0);
            if (c >= 'A' && c <= 'Z') return GLFW.GLFW_KEY_A + (c - 'A');
            if (c >= '0' && c <= '9') return GLFW.GLFW_KEY_0 + (c - '0');
        }

        // F-keys
        if (name.startsWith("F") && name.length() <= 3) {
            try {
                int n = Integer.parseInt(name.substring(1));
                if (n >= 1 && n <= 25) return GLFW.GLFW_KEY_F1 + (n - 1);
            } catch (NumberFormatException ignored) {}
        }

        return switch (name) {
            case "SPACE" -> GLFW.GLFW_KEY_SPACE;
            case "ESCAPE", "ESC" -> GLFW.GLFW_KEY_ESCAPE;
            case "ENTER" -> GLFW.GLFW_KEY_ENTER;
            case "TAB" -> GLFW.GLFW_KEY_TAB;
            case "BACKSPACE" -> GLFW.GLFW_KEY_BACKSPACE;
            case "DELETE", "DEL" -> GLFW.GLFW_KEY_DELETE;
            case "INSERT", "INS" -> GLFW.GLFW_KEY_INSERT;
            case "HOME" -> GLFW.GLFW_KEY_HOME;
            case "END" -> GLFW.GLFW_KEY_END;
            case "PAGE_UP", "PGUP" -> GLFW.GLFW_KEY_PAGE_UP;
            case "PAGE_DOWN", "PGDN" -> GLFW.GLFW_KEY_PAGE_DOWN;
            case "UP" -> GLFW.GLFW_KEY_UP;
            case "DOWN" -> GLFW.GLFW_KEY_DOWN;
            case "LEFT" -> GLFW.GLFW_KEY_LEFT;
            case "RIGHT" -> GLFW.GLFW_KEY_RIGHT;
            case "CAPS_LOCK" -> GLFW.GLFW_KEY_CAPS_LOCK;
            case "NUM_LOCK" -> GLFW.GLFW_KEY_NUM_LOCK;
            case "SCROLL_LOCK" -> GLFW.GLFW_KEY_SCROLL_LOCK;
            case "PRINT_SCREEN", "PRTSC" -> GLFW.GLFW_KEY_PRINT_SCREEN;
            case "PAUSE" -> GLFW.GLFW_KEY_PAUSE;
            case "KP_0" -> GLFW.GLFW_KEY_KP_0;
            case "KP_1" -> GLFW.GLFW_KEY_KP_1;
            case "KP_2" -> GLFW.GLFW_KEY_KP_2;
            case "KP_3" -> GLFW.GLFW_KEY_KP_3;
            case "KP_4" -> GLFW.GLFW_KEY_KP_4;
            case "KP_5" -> GLFW.GLFW_KEY_KP_5;
            case "KP_6" -> GLFW.GLFW_KEY_KP_6;
            case "KP_7" -> GLFW.GLFW_KEY_KP_7;
            case "KP_8" -> GLFW.GLFW_KEY_KP_8;
            case "KP_9" -> GLFW.GLFW_KEY_KP_9;
            case "KP_DECIMAL" -> GLFW.GLFW_KEY_KP_DECIMAL;
            case "KP_DIVIDE" -> GLFW.GLFW_KEY_KP_DIVIDE;
            case "KP_MULTIPLY" -> GLFW.GLFW_KEY_KP_MULTIPLY;
            case "KP_SUBTRACT" -> GLFW.GLFW_KEY_KP_SUBTRACT;
            case "KP_ADD" -> GLFW.GLFW_KEY_KP_ADD;
            case "KP_ENTER" -> GLFW.GLFW_KEY_KP_ENTER;
            case "COMMA" -> GLFW.GLFW_KEY_COMMA;
            case "PERIOD" -> GLFW.GLFW_KEY_PERIOD;
            case "SLASH" -> GLFW.GLFW_KEY_SLASH;
            case "SEMICOLON" -> GLFW.GLFW_KEY_SEMICOLON;
            case "APOSTROPHE" -> GLFW.GLFW_KEY_APOSTROPHE;
            case "MINUS" -> GLFW.GLFW_KEY_MINUS;
            case "EQUALS" -> GLFW.GLFW_KEY_EQUAL;
            case "LEFT_BRACKET" -> GLFW.GLFW_KEY_LEFT_BRACKET;
            case "RIGHT_BRACKET" -> GLFW.GLFW_KEY_RIGHT_BRACKET;
            case "BACKSLASH" -> GLFW.GLFW_KEY_BACKSLASH;
            case "GRAVE" -> GLFW.GLFW_KEY_GRAVE_ACCENT;
            default -> GLFW.GLFW_KEY_UNKNOWN;
        };
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
