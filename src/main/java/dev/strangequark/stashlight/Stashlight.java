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
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
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

    // Block position of a searchable container the player just used (right-clicked),
    // pending the corresponding container screen opening. Set by onBlockUsed, consumed
    // by onScreenInit. Cleared by any non-searchable block or entity interaction so a
    // stale pending (e.g. a sneak-placement that didn't open a chest) can't leak onto
    // the next unrelated menu (ender chest, anvil, horse inventory, etc.).
    @Nullable
    private BlockPos pendingOpenedContainer;

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
        UseEntityCallback.EVENT.register(this::onEntityUsed);
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

    private InteractionResult onBlockUsed(Player player, Level level, InteractionHand interactionHand, BlockHitResult blockHitResult) {
        BlockPos pos = blockHitResult.getBlockPos();
        BlockState state = level.getBlockState(pos);
        // Track a searchable container the player actually opened; clear pending for
        // any other block so a stale pending (e.g. a sneak-placement that didn't open
        // a chest) can't leak onto the next unrelated menu.
        if (Util.isValidSearchableContainer(state)) {
            pendingOpenedContainer = pos;
        } else {
            pendingOpenedContainer = null;
        }
        return InteractionResult.PASS;
    }

    private InteractionResult onEntityUsed(Player player, Level level, InteractionHand interactionHand, Entity entity, EntityHitResult entityHitResult) {
        // Interacting with an entity (horse/llama inventory, villager trade, chest
        // minecart, etc.) is never a searchable block container — drop any stale
        // block pending so it can't be written to whatever chest was last used.
        pendingOpenedContainer = null;
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

        if (!(screen instanceof AbstractContainerScreen<?> handled)) {
            return;
        }

        var handler = handled.getMenu();

        // Only treat this screen as a searchable-container open if the player actually
        // used one (onBlockUsed). This excludes ender chests, anvils, crafting tables,
        // enchanting tables, beacons, the player's own inventory, horse/villager menus,
        // etc. — none of which should be indexed, and several of which used to leak
        // onto whatever chest the player happened to be facing.
        final BlockPos containerPos = pendingOpenedContainer != null
                ? Util.getCanonicalPos(client.level, pendingOpenedContainer)
                : null;
        pendingOpenedContainer = null;

        if (containerPos != null) {
            GuiSlotHighlighter.setCurrentContainerPos(containerPos);
            ScreenEvents.afterRender(screen).register((s, graphics, mouseX, mouseY, tickDelta) ->
                    GuiSlotHighlighter.render((AbstractContainerScreen<?>) s, graphics, tickDelta));
        }

        // Serialize on close. containerPos is captured per-screen, so closing one chest
        // right as another opens can't write the first menu's contents to the second block.
        ScreenEvents.remove(screen).register(closedScreen -> {
            if (containerPos != null) {
                serializeContainer(client, handler, containerPos);
            }
            GuiSlotHighlighter.clearCurrentContainerPos();
        });
    }

    private void serializeContainer(Minecraft client, AbstractContainerMenu handler, BlockPos openedPos) {
        if (client.level == null || repository == null || openedPos == null) {
            return;
        }

        // The player's own inventory menu (crafting grid, armor, offhand, backpack)
        // is never a searchable block container — bail before it can be indexed.
        if (handler instanceof InventoryMenu) {
            return;
        }

        String dimension = Util.getDimensionName(client.level);
        Set<BlockPos> pair = Util.resolveContainerPositions(client.level, openedPos);
        BlockPos canonicalPos = Util.getCanonicalPos(client.level, pair.iterator().next());
        BlockState blockstate = client.level.getBlockState(canonicalPos);


        if (!Util.isValidSearchableContainer(blockstate)) {
            return;
        }

        // Read only the real container slots directly from the menu, skipping any
        // slot backed by the player's inventory (main, hotbar, armor, offhand).
        // The flat handler.getItems() list plus a "size - 36" heuristic wrongly
        // captured armor/crafting slots whenever the menu wasn't a plain chest.
        var player = client.player;
        List<SlotStack> slotStacks = new ArrayList<>();
        int containerSize = 0;
        for (Slot slot : handler.slots) {
            if (player != null && slot.container == player.getInventory()) {
                continue;
            }
            containerSize++;
            ItemStack stack = slot.getItem();
            if (stack != null && !stack.isEmpty()) {
                slotStacks.add(new SlotStack(slot.index, stack.copy()));
            }
        }

        if (containerSize <= 0) {
            return;
        }

        // HARD INVALIDATION — nuke positions data
        repository.remove(dimension, canonicalPos);
        for (BlockPos p : pair) {
            repository.remove(dimension, p);
        }

        repository.update(
                dimension,
                canonicalPos,
                blockstate.getBlock().getName().getString(),
                containerSize,
                slotStacks
        );
    }
}
