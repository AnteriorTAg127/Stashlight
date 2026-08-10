package dev.strangequark.stashlight.screen;

/**
 * Top-level pages of the search screen, switched by the bottom page bar.
 * Each page occupies the whole main content area; the search page itself
 * is further split into ITEM / ENCHANT modes via {@link SearchMode}.
 */
public enum SearchPage {
    SEARCH,
    CRAFT
}
