package dev.strangequark.stashlight.logic.filter;

import dev.strangequark.stashlight.model.EnchantEntry;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a query like "@ench:sharpness smite>=3" into a map of enchantment
 * ids to level ranges.
 */
public final class EnchantQueryParser {
    private static final Pattern TOKEN = Pattern.compile("([\\w:]+)(?:(>=|<=|>|<|=)(\\d+))?");

    private EnchantQueryParser() {
    }

    public static Map<ResourceLocation, EnchantFilterState.LevelRange> parse(String query, List<EnchantEntry> available) {
        Map<ResourceLocation, EnchantFilterState.LevelRange> result = new HashMap<>();
        if (query == null || query.isBlank()) return result;

        String trimmed = query.startsWith("@ench:") ? query.substring(6) : query;
        if (trimmed.isBlank()) return result;

        Matcher matcher = TOKEN.matcher(trimmed);
        while (matcher.find()) {
            String name = matcher.group(1);
            String op = matcher.group(2);
            String levelStr = matcher.group(3);

            EnchantEntry matched = findMatch(name, available);
            if (matched == null) continue;

            int level = levelStr != null ? Integer.parseInt(levelStr) : 1;
            int min, max;
            if (op == null) {
                min = 1;
                max = 255;
            } else if (op.equals("=")) {
                min = level;
                max = level;
            } else if (op.equals(">")) {
                min = level + 1;
                max = 255;
            } else if (op.equals(">=")) {
                min = level;
                max = 255;
            } else if (op.equals("<")) {
                min = 1;
                max = level - 1;
            } else { // "<="
                min = 1;
                max = level;
            }
            result.put(matched.id(), new EnchantFilterState.LevelRange(min, max));
        }

        return result;
    }

    private static EnchantEntry findMatch(String name, List<EnchantEntry> available) {
        String lower = name.toLowerCase(Locale.ROOT);
        EnchantEntry fallback = null;

        for (EnchantEntry entry : available) {
            if (entry.id().toString().equalsIgnoreCase(name)) {
                return entry;
            }
            if (entry.id().getPath().equalsIgnoreCase(name)) {
                fallback = entry;
            }
        }

        if (fallback != null) return fallback;

        for (EnchantEntry entry : available) {
            String display = entry.displayName().getString().toLowerCase(Locale.ROOT);
            if (display.contains(lower)) {
                return entry;
            }
        }

        return null;
    }
}
