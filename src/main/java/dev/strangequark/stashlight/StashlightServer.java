package dev.strangequark.stashlight;

import dev.strangequark.stashlight.config.ServerConfig;
import dev.strangequark.stashlight.net.*;
import dev.strangequark.stashlight.server.ContainerSignatureStore;
import dev.strangequark.stashlight.server.RateLimiter;
import dev.strangequark.stashlight.server.ServerScanner;
import dev.strangequark.stashlight.util.SignatureUtil;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class StashlightServer implements ModInitializer {

    // Dedicated-server-side logger. Do NOT use Stashlight.LOGGER here — that class
    // is the client entry point and referencing it on the server triggers loading
    // of client-only classes (KeyMapping, Screen, ...) which crashes the server.
    private static final Logger LOGGER = LoggerFactory.getLogger("Stashlight");

    private final RateLimiter rateLimiter = new RateLimiter();
    private final RateLimiter takeRateLimiter = new RateLimiter();
    private final AtomicInteger nonceGenerator = new AtomicInteger(0);

    // v2 state — join push + incremental response
    private final ContainerSignatureStore signatureStore = new ContainerSignatureStore();
    private final Set<UUID> v2Players = new HashSet<>();
    private final Map<UUID, ResourceKey<Level>> lastPlayerDimension = new HashMap<>();

    @Override
    public void onInitialize() {
        ServerConfig.get();

        registerPayloads();

        ServerPlayConnectionEvents.JOIN.register(this::onPlayerJoin);
        ServerPlayConnectionEvents.DISCONNECT.register(this::onPlayerDisconnect);

        ServerPlayNetworking.registerGlobalReceiver(ScanRequestPayload.TYPE, (payload, context) -> {
                    int tickCount = context.server().getTickCount();
                    context.server().execute(() -> handleScanRequest(payload, context.player(), tickCount));
                }
        );

        ServerPlayNetworking.registerGlobalReceiver(ClientReadyPayload.TYPE, (payload, context) -> {
                    context.server().execute(() -> handleClientReady(payload, context.player()));
                }
        );

        // v3: take item
        ServerPlayNetworking.registerGlobalReceiver(TakeItemRequestPayload.TYPE, (payload, context) -> {
                    context.server().execute(() -> handleTakeItem(payload, context.player()));
                }
        );

        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("stashlight")
                        .then(Commands.literal("reload")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> {
                                    ServerConfig.reload();
                                    context.getSource().sendSuccess(() ->
                                            Component.translatable("commands.stashlight.reload.success"), true);
                                    return 1;
                                })))
        );
    }

    private void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(HandshakePayload.TYPE, HandshakePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ScanRequestPayload.TYPE, ScanRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ScanResponsePayload.TYPE, ScanResponsePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ScanErrorPayload.TYPE, ScanErrorPayload.CODEC);

        // v2
        PayloadTypeRegistry.playC2S().register(ClientReadyPayload.TYPE, ClientReadyPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ContainerUpdatePayload.TYPE, ContainerUpdatePayload.CODEC);

        // v3
        PayloadTypeRegistry.playC2S().register(TakeItemRequestPayload.TYPE, TakeItemRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TakeItemResponsePayload.TYPE, TakeItemResponsePayload.CODEC);
    }

    private void onPlayerJoin(ServerGamePacketListenerImpl handler, PacketSender sender, MinecraftServer server) {
        var cfg = ServerConfig.get();
        sender.sendPacket(new HandshakePayload(
                cfg.enabled(),
                cfg.scan().maxRadius(),
                StashlightPayloads.PROTOCOL_VERSION,
                cfg.take().enabled()
        ));
    }

    private void onPlayerDisconnect(ServerGamePacketListenerImpl handler, MinecraftServer server) {
        UUID uuid = handler.getPlayer().getUUID();
        v2Players.remove(uuid);
        lastPlayerDimension.remove(uuid);
        signatureStore.clear(uuid);
    }

    private void handleClientReady(ClientReadyPayload payload, ServerPlayer player) {
        if (!ServerConfig.get().enabled()) return;
        if (!ServerConfig.get().push().enabled()) return;

        UUID uuid = player.getUUID();
        v2Players.add(uuid);

        for (ClientReadyPayload.ClientDimensionSignatures dim : payload.cachedSignatures()) {
            for (ClientReadyPayload.ClientPositionSignature entry : dim.entries()) {
                BlockPos pos = BlockPos.of(entry.posAsLong());
                signatureStore.put(uuid, dim.dimension(), pos, entry.signature());
            }
        }

        ServerScanner.IncrementalScanResult result =
                ServerScanner.incrementalScanAround(player, signatureStore, false);
        sendContainerUpdate(player, result.updated(), result.removed());
    }

    private void handleScanRequest(ScanRequestPayload payload, ServerPlayer player, int serverTickCount) {
        var cfg = ServerConfig.get();
        if (!cfg.enabled()) {
            sendError(player, payload.nonce(), "DISABLED");
            return;
        }

        var rateResult = rateLimiter.allowRequest(player, serverTickCount);
        if (rateResult == RateLimiter.RateLimitResult.DENY_RATE_LIMITED) {
            sendError(player, payload.nonce(), "RATE_LIMITED");
            return;
        }

        boolean isV2 = v2Players.contains(player.getUUID());
        boolean pushEnabled = cfg.push().enabled();
        boolean forceFull = payload.nonce() < 0;

        // v1 clients (never sent ClientReady) and disabled push both fall back to
        // the legacy full ScanResponsePayload behaviour.
        if (!isV2 || !pushEnabled) {
            sendLegacyFullScan(player, payload.nonce());
            return;
        }

        ServerScanner.IncrementalScanResult result =
                ServerScanner.incrementalScanAround(player, signatureStore, forceFull);
        sendContainerUpdate(player, result.updated(), result.removed());
    }

    private void sendLegacyFullScan(ServerPlayer player, int nonce) {
        ServerScanner.ScanResult result = ServerScanner.scanAround(player);
        List<ContainerSnapshotPayload> containers = result.containers();

        int chunkSize = 32;
        int total = Math.max(1, (containers.size() + chunkSize - 1) / chunkSize);
        for (int i = 0; i < total; i++) {
            int start = i * chunkSize;
            int end = Math.min(start + chunkSize, containers.size());
            List<ContainerSnapshotPayload> chunk = new ArrayList<>(containers.subList(start, end));
            boolean isLast = (i == total - 1);
            ServerPlayNetworking.send(player, new ScanResponsePayload(
                    nonce,
                    i,
                    total,
                    chunk,
                    isLast && result.truncated()
            ));
        }
    }

    /**
     * Split a ContainerUpdatePayload across multiple chunks if needed.
     * The combined list of (updated + removed) entries is sliced at chunkSize=32
     * so total payload size stays bounded; the client reassembles via chunkTotal.
     */
    private void sendContainerUpdate(ServerPlayer player,
                                     List<ContainerSnapshotPayload> updated,
                                     List<ContainerRemovedEntry> removed) {
        int total = updated.size() + removed.size();
        int chunkSize = 32;
        int chunkTotal = Math.max(1, (total + chunkSize - 1) / chunkSize);

        int uIdx = 0;
        int rIdx = 0;
        for (int chunk = 0; chunk < chunkTotal; chunk++) {
            List<ContainerSnapshotPayload> chunkUpdated = new ArrayList<>();
            List<ContainerRemovedEntry> chunkRemoved = new ArrayList<>();

            int remaining = chunkSize;
            while (remaining > 0 && uIdx < updated.size()) {
                chunkUpdated.add(updated.get(uIdx++));
                remaining--;
            }
            while (remaining > 0 && rIdx < removed.size()) {
                chunkRemoved.add(removed.get(rIdx++));
                remaining--;
            }

            ServerPlayNetworking.send(player, new ContainerUpdatePayload(
                    chunkUpdated,
                    chunkRemoved,
                    chunk,
                    chunkTotal
            ));
        }
    }

    private void sendError(ServerPlayer player, int nonce, String reason) {
        ServerPlayNetworking.send(player, new ScanErrorPayload(nonce, reason));
    }

    // ── v3: take item ──────────────────────────────────────────────────────

    private void handleTakeItem(TakeItemRequestPayload p, ServerPlayer player) {
        var cfg = ServerConfig.get();
        int nonce = p.nonce();
        ServerLevel level = (ServerLevel) player.level();

        // 1. Enabled check
        if (!cfg.take().enabled()) {
            respondTake(player, nonce, TakeResult.DISABLED, 0);
            return;
        }

        // 2. Rate limit
        if (takeRateLimiter.allowRequest(player, level.getServer().getTickCount()) != RateLimiter.RateLimitResult.ALLOW) {
            // Reuse RATE_LIMITED from TakeResult
            respondTake(player, nonce, TakeResult.RATE_LIMITED, 0);
            return;
        }

        // 3. Range check
        if (!inRange(player, p.pos(), cfg.take().maxRadius())) {
            respondTake(player, nonce, TakeResult.OUT_OF_RANGE, 0);
            return;
        }

        // 4. Get container
        BlockState state = level.getBlockState(p.pos());
        Container container = ServerScanner.getContainer(level, p.pos(), state);
        if (container == null) {
            respondTake(player, nonce, TakeResult.NO_CONTAINER, 0);
            return;
        }

        // 5. Item match
        ItemStack slotStack = container.getItem(p.slot());
        if (!ItemStack.isSameItemSameComponents(slotStack, p.target())) {
            respondTake(player, nonce, TakeResult.ITEM_MISMATCH, 0);
            return;
        }

        // 6. Take
        int takeCount = Math.min(p.count(), slotStack.getCount());
        ItemStack removed = container.removeItem(p.slot(), takeCount);

        if (!player.getInventory().add(removed)) {
            // Inventory full — drop at player's feet instead of failing
            player.drop(removed, false);
            respondTake(player, nonce, TakeResult.SUCCESS, takeCount);
            return;
        }

        // 7. Sync
        container.setChanged();
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();

        // 8. Mark signature dirty for next scan push
        if (signatureStore != null) {
            signatureStore.put(player.getUUID(),
                    level.dimension().location().toString(), p.pos(),
                    SignatureUtil.computeSignature(container));
        }

        respondTake(player, nonce, TakeResult.SUCCESS, takeCount);
    }

    private void respondTake(ServerPlayer player, int nonce, TakeResult result, int taken) {
        ServerPlayNetworking.send(player,
                new TakeItemResponsePayload(nonce, result.ordinal(), taken));
    }

    private static boolean inRange(ServerPlayer player, BlockPos pos, int maxRadius) {
        return player.position().distanceToSqr(Vec3.atCenterOf(pos)) <= (double) maxRadius * maxRadius;
    }

    private void onServerTick(MinecraftServer server) {
        // F6: dimension change auto-push. Cheap every-tick check (map lookup +
        // comparison per v2 player). Only fires when push is enabled.
        if (!ServerConfig.get().push().enabled()) return;
        if (v2Players.isEmpty()) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            if (!v2Players.contains(uuid)) continue;

            ResourceKey<Level> current = player.level().dimension();
            ResourceKey<Level> last = lastPlayerDimension.get(uuid);
            if (last == null) {
                // First tick after ClientReady (handleClientReady doesn't set this)
                // or after a disconnect/reconnect — record and continue.
                lastPlayerDimension.put(uuid, current);
                continue;
            }
            if (last.equals(current)) continue;

            // Dimension changed — push new dimension's containers (incremental
            // against any prior signatures the player has for that dimension).
            lastPlayerDimension.put(uuid, current);
            ServerScanner.IncrementalScanResult result =
                    ServerScanner.incrementalScanAround(player, signatureStore, false);
            sendContainerUpdate(player, result.updated(), result.removed());

            LOGGER.info("Stashlight dimension push to {} ({}): {} updated, {} removed",
                    player.getName().getString(),
                    current.location().getPath(),
                    result.updated().size(),
                    result.removed().size());
        }
    }
}
