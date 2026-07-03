package dev.strangequark.stashlight.net;

import dev.strangequark.stashlight.Stashlight;
import net.minecraft.resources.ResourceLocation;

public final class StashlightPayloads {
    public static final ResourceLocation HANDSHAKE = ResourceLocation.fromNamespaceAndPath(Stashlight.MOD_ID, "handshake");
    public static final ResourceLocation SCAN_REQUEST = ResourceLocation.fromNamespaceAndPath(Stashlight.MOD_ID, "scan_request");
    public static final ResourceLocation SCAN_RESPONSE = ResourceLocation.fromNamespaceAndPath(Stashlight.MOD_ID, "scan_response");
    public static final ResourceLocation SCAN_ERROR = ResourceLocation.fromNamespaceAndPath(Stashlight.MOD_ID, "scan_error");

    // v2: join-time push + incremental response
    public static final ResourceLocation CLIENT_READY = ResourceLocation.fromNamespaceAndPath(Stashlight.MOD_ID, "client_ready");
    public static final ResourceLocation CONTAINER_UPDATE = ResourceLocation.fromNamespaceAndPath(Stashlight.MOD_ID, "container_update");

    // v3: remote item take
    public static final ResourceLocation TAKE_ITEM_REQUEST = ResourceLocation.fromNamespaceAndPath(Stashlight.MOD_ID, "take_item_request");
    public static final ResourceLocation TAKE_ITEM_RESPONSE = ResourceLocation.fromNamespaceAndPath(Stashlight.MOD_ID, "take_item_response");

    public static final int PROTOCOL_VERSION = 3;

    private StashlightPayloads() {
    }
}
