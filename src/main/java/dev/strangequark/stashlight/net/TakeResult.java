package dev.strangequark.stashlight.net;

/**
 * Result codes for {@link TakeItemResponsePayload}.
 * Serialized as ordinal ({@code writeVarInt}) — do NOT reorder.
 */
public enum TakeResult {
    SUCCESS,
    OUT_OF_RANGE,
    NO_CONTAINER,
    ITEM_MISMATCH,
    INVENTORY_FULL,
    DISABLED,
    RATE_LIMITED
}
