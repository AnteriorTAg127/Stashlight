package dev.strangequark.stashlight;

import com.mojang.blaze3d.platform.InputConstants;
import dev.strangequark.stashlight.model.SlotStack;
import dev.strangequark.stashlight.net.*;
import dev.strangequark.stashlight.render.GuiSlotHighlighter;
import dev.strangequark.stashlight.render.HighlightRenderer;
import dev.strangequark.stashlight.repository.ContainerRepository;
import dev.strangequark.stashlight.scan.AutoIndexer;
import dev.strangequark.stashlight.screen.SearchScreen;
import dev.strangequark.stashlight.serializer.Serializer;
import dev.strangequark.stashlight.util.Util;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class Stashlight implements ClientModInitializer {
    public static final String MOD_ID = "stashlight";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static Stashlight INSTANCE;

    public static Stashlight getInstance() {
        return INSTANCE;
    }

    private Serializer serializer;
    private Serializer serverSerializer;
    private ContainerRepository repository;
    private AutoIndexer autoIndexer;
    private static KeyMapping searchKey;
    public static final KeyMapping.Category STASHLIGHT = KeyMapping.Category.register(ResourceLocation.fromNamespaceAndPath(MOD_ID, "stashlight"));

    private int tickCounter = 0;

    @Nullable
    private BlockPos lastOpened;
    @Nullable
    private BlockPos lastLookedAtContainer;

    // Server capability state (updated by handshake packet).
    private volatile boolean serverModPresent = false;
    private volatile boolean serverEnabled = false;
    private volatile int serverMaxRadius = 0;
    private volatile int serverProtocolVersion = 0;

    // F9: timestamp of the most recent server data arrival. Read by the search
    // screen to render "data: X seconds ago". Updated on the final chunk of
    // both ContainerUpdatePayload (v2) and ScanResponsePayload (v1 fallback).
    private volatile long lastServerDataTime = 0L;

    private final AtomicInteger scanNonceGenerator = new AtomicInteger(1);

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        Init.init();
        registerPayloads();
        registerNetworkHandlers();

        UseBlockCallback.EVENT.register(this::onBlockUsed);
        ClientPlayerBlockBreakEvents.AFTER.register(this::onBlockBreak);
        ScreenEvents.AFTER_INIT.register(this::onScreenInit);
        WorldRenderEvents.AFTER_ENTITIES.register(HighlightRenderer::render);

        searchKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.stashlight.search_menu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_5,
                STASHLIGHT
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (searchKey.consumeClick()) {
                if (client.level == null || repository == null) continue;

                if (client.screen instanceof SearchScreen) {
                    client.setScreen(null);
                } else {
                    client.setScreen(new SearchScreen(repository));
                }
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null || repository == null) return;

            var hit = client.hitResult;
            if (hit instanceof BlockHitResult blockHit) {
                BlockPos pos = blockHit.getBlockPos();
                BlockState state = client.level.getBlockState(pos);
                if (Util.isValidSearchableContainer(state)) {
                    lastLookedAtContainer = pos;
                }
            }

            tickCounter++;

            if (tickCounter % 100 == 0) {
                repository.runCleanup(client.level);
            }

            if (tickCounter % 3000 == 0) {
                repository.saveIfDirty();
                tickCounter = 0;
            }

            var cfg = dev.strangequark.stashlight.config.Config.get().autoIndex();
            if (cfg.enabled() && autoIndexer != null && tickCounter % cfg.scanIntervalTicks() == 0) {
                autoIndexer.tick(client);
            }
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            resetServerState();
            serializer = new Serializer(Init.getFileName(), handler.registryAccess());
            serverSerializer = dev.strangequark.stashlight.config.Config.get().dataSource().persistServerData()
                    ? new Serializer(Init.getServerFileName(), handler.registryAccess())
                    : null;
            repository = new ContainerRepository(serializer, serverSerializer);
            autoIndexer = new AutoIndexer(repository);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (repository != null) {
                repository.shutdown();
            }
            resetServerState();
            serializer = null;
            serverSerializer = null;
            repository = null;
            autoIndexer = null;
        });
    }

    private void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(HandshakePayload.TYPE, HandshakePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ScanRequestPayload.TYPE, ScanRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ScanResponsePayload.TYPE, ScanResponsePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ScanErrorPayload.TYPE, ScanErrorPayload.CODEC);

        // v2: join push + incremental response
        PayloadTypeRegistry.playC2S().register(ClientReadyPayload.TYPE, ClientReadyPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ContainerUpdatePayload.TYPE, ContainerUpdatePayload.CODEC);
    }

    private void registerNetworkHandlers() {
        ClientPlayNetworking.registerGlobalReceiver(HandshakePayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    serverModPresent = true;
                    serverEnabled = payload.enabled();
                    serverMaxRadius = payload.maxRadius();
                    serverProtocolVersion = payload.protocolVersion();

                    if (serverProtocolVersion >= 2 && serverEnabled && repository != null) {
                        var cached = buildCachedSignatures();
                        ClientPlayNetworking.send(new ClientReadyPayload(cached));
                    }
                })
        );

        ClientPlayNetworking.registerGlobalReceiver(ScanResponsePayload.TYPE, (payload, context) ->
                context.client().execute(() -> handleScanResponse(payload))
        );

        ClientPlayNetworking.registerGlobalReceiver(ScanErrorPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                })
        );

        ClientPlayNetworking.registerGlobalReceiver(ContainerUpdatePayload.TYPE, (payload, context) ->
                context.client().execute(() -> handleContainerUpdate(payload))
        );
    }

    private void resetServerState() {
        serverModPresent = false;
        serverEnabled = false;
        serverMaxRadius = 0;
        serverProtocolVersion = 0;
        lastServerDataTime = 0L;
    }

    public boolean isServerModPresent() {
        return serverModPresent;
    }

    public boolean isServerEnabled() {
        return serverEnabled;
    }

    public int getServerMaxRadius() {
        return serverMaxRadius;
    }

    public int getServerProtocolVersion() {
        return serverProtocolVersion;
    }

    public long getLastServerDataTime() {
        return lastServerDataTime;
    }

    public void requestServerScan() {
        sendScanRequest(false);
    }

    public void requestServerScanForceFull() {
        sendScanRequest(true);
    }

    private void sendScanRequest(boolean forceFull) {
        var client = Minecraft.getInstance();
        if (client.player == null || !serverModPresent || !serverEnabled) return;

        int radius = Math.min(serverMaxRadius, dev.strangequark.stashlight.config.Config.get().autoIndex().radius());
        int nonce = forceFull ? -1 : scanNonceGenerator.getAndIncrement();
        ClientPlayNetworking.send(new ScanRequestPayload(radius, "same", nonce));
    }

    private void handleScanResponse(ScanResponsePayload payload) {
        if (repository == null) return;

        for (ContainerSnapshotPayload container : payload.containers()) {
            List<SlotStack> slotStacks = new ArrayList<>();
            for (ContainerSnapshotPayload.SlotStackSnapshot slot : container.slotStacks()) {
                if (slot.stack() != null && !slot.stack().isEmpty()) {
                    slotStacks.add(new SlotStack(slot.slot(), slot.stack().copy()));
                }
            }

            repository.updateServer(
                    container.dimension(),
                    container.pos(),
                    container.containerName(),
                    container.capacity(),
                    slotStacks,
                    System.currentTimeMillis()
            );
        }

        if (payload.chunkIndex() == payload.chunkTotal() - 1) {
            lastServerDataTime = System.currentTimeMillis();
            var screen = Minecraft.getInstance().screen;
            if (screen instanceof SearchScreen searchScreen) {
                searchScreen.refreshFromServer();
            }
        }
    }

    private void handleContainerUpdate(ContainerUpdatePayload payload) {
        if (repository == null) return;

        long now = System.currentTimeMillis();

        for (ContainerSnapshotPayload container : payload.updated()) {
            List<SlotStack> slotStacks = new ArrayList<>();
            for (ContainerSnapshotPayload.SlotStackSnapshot slot : container.slotStacks()) {
                if (slot.stack() != null && !slot.stack().isEmpty()) {
                    slotStacks.add(new SlotStack(slot.slot(), slot.stack().copy()));
                }
            }
            repository.updateServer(
                    container.dimension(),
                    container.pos(),
                    container.containerName(),
                    container.capacity(),
                    slotStacks,
                    now
            );
        }

        for (ContainerRemovedEntry removed : payload.removed()) {
            repository.removeServer(removed.dimension(), removed.pos());
        }

        if (payload.chunkIndex() == payload.chunkTotal() - 1) {
            lastServerDataTime = now;
            var screen = Minecraft.getInstance().screen;
            if (screen instanceof SearchScreen searchScreen) {
                searchScreen.refreshFromServer();
            }
        }
    }

    private List<ClientReadyPayload.ClientDimensionSignatures> buildCachedSignatures() {
        if (repository == null) return List.of();
        var sigs = repository.computeServerSignatures();
        List<ClientReadyPayload.ClientDimensionSignatures> result = new ArrayList<>();
        for (var dimEntry : sigs.entrySet()) {
            List<ClientReadyPayload.ClientPositionSignature> entries = new ArrayList<>();
            for (var posEntry : dimEntry.getValue().entrySet()) {
                entries.add(new ClientReadyPayload.ClientPositionSignature(
                        posEntry.getKey().asLong(),
                        posEntry.getValue()
                ));
            }
            result.add(new ClientReadyPayload.ClientDimensionSignatures(dimEntry.getKey(), entries));
        }
        return result;
    }

    @Nullable
    private BlockPos resolveCurrentContainerPos(Minecraft client) {
        if (lastOpened != null) {
            return Util.getCanonicalPos(client.level, lastOpened);
        }

        var hit = client.hitResult;
        if (hit instanceof BlockHitResult blockHit) {
            BlockPos pos = blockHit.getBlockPos();
            BlockState state = client.level.getBlockState(pos);
            if (Util.isValidSearchableContainer(state)) {
                return Util.getCanonicalPos(client.level, pos);
            }
        }

        if (lastLookedAtContainer != null) {
            BlockState state = client.level.getBlockState(lastLookedAtContainer);
            if (Util.isValidSearchableContainer(state)) {
                return Util.getCanonicalPos(client.level, lastLookedAtContainer);
            }
        }
        return null;
    }

    private InteractionResult onBlockUsed(Player player, Level level, InteractionHand interactionHand, BlockHitResult blockHitResult) {
        BlockPos pos = blockHitResult.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (Util.isValidSearchableContainer(state)) {
            lastOpened = pos;
        }
        return InteractionResult.PASS;
    }

    private void onBlockBreak(ClientLevel clientLevel, LocalPlayer localPlayer, BlockPos blockPos, BlockState blockState) {
        if (repository == null || !(blockState.getBlock() instanceof EntityBlock)) {
            return;
        }

        // Use the canonical resolution to find the correct database key to delete.
        BlockPos canonicalPos = Util.getCanonicalPos(clientLevel, blockPos);
        String dimension = Util.getDimensionName(clientLevel);
        repository.remove(dimension, canonicalPos);
    }


    private void onScreenInit(Minecraft client, Screen screen, int w, int h) {
        if (client.level == null || screen instanceof CreativeModeInventoryScreen) {
            return;
        }

        if (screen instanceof AbstractContainerScreen<?> handled) {
            var handler = handled.getMenu();
            // Serialize on close to ensure the database reflects the final state of the inventory.
            ScreenEvents.remove(screen).register(closedScreen -> {
                serializeContainer(client, handler);
                GuiSlotHighlighter.clearCurrentContainerPos();
            });

            BlockPos containerPos = resolveCurrentContainerPos(client);
            if (containerPos != null) {
                lastOpened = containerPos;
                GuiSlotHighlighter.setCurrentContainerPos(containerPos);
            }

            ScreenEvents.afterRender(screen).register((s, graphics, mouseX, mouseY, tickDelta) ->
                    GuiSlotHighlighter.render((AbstractContainerScreen<?>) s, graphics, tickDelta));
        }
    }

    private void serializeContainer(Minecraft client, AbstractContainerMenu handler) {
        if (client.level == null || repository == null || lastOpened == null) {
            return;
        }

        String dimension = Util.getDimensionName(client.level);
        Set<BlockPos> pair = Util.resolveContainerPositions(client.level, lastOpened);
        BlockPos canonicalPos = Util.getCanonicalPos(client.level, pair.iterator().next());
        BlockState blockstate = client.level.getBlockState(canonicalPos);


        if (!Util.isValidSearchableContainer(blockstate)) {
            lastOpened = null;
            return;
        }

        var stacks = handler.getItems();
        int containerSize = stacks.size() - 36;
        if (containerSize <= 0) {
            lastOpened = null;
            return;
        }

        // HARD INVALIDATION — nuke positions data
        repository.remove(dimension, canonicalPos);
        for (BlockPos p : pair) {
            repository.remove(dimension, p);
        }

        // Single authoritative write
        List<SlotStack> slotStacks = new ArrayList<>();
        for (int i = 0; i < containerSize; i++) {
            ItemStack stack = stacks.get(i);
            if (stack != null && !stack.isEmpty()) {
                slotStacks.add(new SlotStack(i, stack.copy()));
            }
        }

        repository.update(
                dimension,
                canonicalPos,
                blockstate.getBlock().getName().getString(),
                containerSize,
                slotStacks
        );

        lastOpened = null;
    }
}
