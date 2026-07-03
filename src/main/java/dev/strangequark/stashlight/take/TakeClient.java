package dev.strangequark.stashlight.take;

import dev.strangequark.stashlight.Stashlight;
import dev.strangequark.stashlight.config.Config;
import dev.strangequark.stashlight.model.DisplayItem;
import dev.strangequark.stashlight.model.IndexedItem;
import dev.strangequark.stashlight.net.TakeItemRequestPayload;
import dev.strangequark.stashlight.net.TakeItemResponsePayload;
import dev.strangequark.stashlight.net.TakeResult;
import dev.strangequark.stashlight.scan.VanillaTaker;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Client-side handler for modded-mode remote item taking.
 * <p>
 * Sends {@link TakeItemRequestPayload} to the server and matches responses
 * via nonce. Falls back to {@link VanillaTaker} when modded take is unavailable.
 */
public final class TakeClient {

    private final AtomicInteger nonceGen = new AtomicInteger(1);
    private final Map<Integer, Consumer<TakeItemResponsePayload>> pending = new ConcurrentHashMap<>();

    private int totalTaken = 0;
    private int totalWanted = 0;

    /**
     * Initiate a take operation for the given item and quantity.
     * Falls back to {@link VanillaTaker} when modded take is unavailable.
     */
    public void startTake(DisplayItem item, int count) {
        var stashlight = Stashlight.getInstance();
        if (stashlight == null) return;

        // Vanilla fallback
        if (!stashlight.isModdedTakeAvailable()) {
            VanillaTaker.start(item, count);
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Close any open screen (required for take flow)
        mc.setScreen(null);

        totalTaken = 0;
        totalWanted = count;

        for (IndexedItem source : item.sources()) {
            if (totalTaken >= count) break;

            int want = Math.min(count - totalTaken, Config.get().remoteTake().defaultQuantity());
            int nonce = nonceGen.getAndIncrement();

            final int takenBefore = totalTaken;
            pending.put(nonce, response -> {
                if (response.result() == TakeResult.SUCCESS.ordinal()) {
                    totalTaken += response.taken();
                    showProgress(totalTaken, totalWanted);
                } else {
                    mc.player.displayClientMessage(
                            Component.literal("Take failed: " + TakeResult.values()[response.result()].name()),
                            true);
                }
            });

            ClientPlayNetworking.send(new TakeItemRequestPayload(
                    source.pos(), source.path().topSlot(),
                    source.stack(), want, nonce
            ));
        }
    }

    /**
     * Called from {@code Stashlight.handleTakeItemResponse} when a response arrives.
     */
    public void handleResponse(TakeItemResponsePayload payload) {
        Consumer<TakeItemResponsePayload> cb = pending.remove(payload.nonce());
        if (cb != null) {
            cb.accept(payload);
        }
    }

    private static void showProgress(int taken, int wanted) {
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(
                    Component.literal("Taking: " + taken + "/" + wanted),
                    true);
        }
    }

    /**
     * Reset state (e.g. on disconnect).
     */
    public void reset() {
        pending.clear();
        totalTaken = 0;
        totalWanted = 0;
    }
}
