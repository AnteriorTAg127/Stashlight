package dev.strangequark.stashlight.scan;

import dev.strangequark.stashlight.Stashlight;
import dev.strangequark.stashlight.config.Config;
import dev.strangequark.stashlight.mixin.SilentOpenManager;
import dev.strangequark.stashlight.repository.ContainerRepository;
import dev.strangequark.stashlight.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Vanilla-fallback scanner. When the server has no Stashlight mod, this
 * silently opens containers via mixin, captures their contents, and closes
 * them — all without showing a screen to the player.
 * <p>
 * Triggered by a masa-style combo keybind. Runs a non-blocking state machine
 * (WAIT_OPEN / WAIT_CONTENT / WAIT_INTERVAL) driven by the client tick.
 */
public final class VanillaScanner {

    private static final Logger LOGGER = LoggerFactory.getLogger("Stashlight/VanillaScanner");

    private enum State { IDLE, BUILD_CANDIDATES, WAIT_OPEN, WAIT_CONTENT, WAIT_INTERVAL, FINISHED }

    private State state = State.IDLE;
    private ComboKeybind comboKeybind;
    private List<BlockPos> candidates = new ArrayList<>();
    private int candidateIndex = 0;
    private int scannedCount = 0;
    private int tickCounter = 0;
    private boolean running = false;

    public VanillaScanner() {
        buildKeybind();
    }

    private void buildKeybind() {
        var cfg = Config.get().vanillaFallback();
        this.comboKeybind = ComboKeybind.fromConfig(cfg.scanComboKey(), cfg.scanComboMods());
    }

    /**
     * Reload the keybind from config (called after config change).
     */
    public void reloadKeybind() {
        buildKeybind();
    }

    /**
     * Called every END_CLIENT_TICK. Drives the non-blocking state machine.
     */
    public void tick(Minecraft client) {
        var cfg = Config.get().vanillaFallback();
        var stashlight = Stashlight.getInstance();
        if (stashlight == null) return;

        // Toggle on combo rising edge
        if (comboKeybind.consumeClick()) {
            if (running) {
                stop("Keybind toggle off");
                return;
            } else if (cfg.enabled() && !stashlight.isModdedScanAvailable()) {
                start(client);
            }
        }

        if (!running) return;

        // Timeout check for silent open
        int timeoutContainerId = SilentOpenManager.timeoutCheck();
        if (timeoutContainerId >= 0) {
            LOGGER.warn("Silent open timed out for container {}", timeoutContainerId);
            closeContainer(client, timeoutContainerId);
        }

        tickCounter++;

        switch (state) {
            case IDLE -> {
                // Nothing to do
            }
            case BUILD_CANDIDATES -> {
                buildCandidates(client);
                if (candidates.isEmpty()) {
                    stop("No candidates found");
                    return;
                }
                candidateIndex = 0;
                scannedCount = 0;
                transitionTo(State.WAIT_OPEN, 0);
            }
            case WAIT_OPEN -> {
                if (tickCounter < 1) break; // Wait at least 1 tick before opening
                if (candidateIndex >= candidates.size()
                        || scannedCount >= cfg.maxContainersPerLoop()) {
                    transitionTo(State.FINISHED, 0);
                    break;
                }
                if (interruptCheck(client)) {
                    stop("Interrupted");
                    return;
                }
                BlockPos pos = candidates.get(candidateIndex);
                // Check reach
                if (!isInReach(client.player, pos)) {
                    candidateIndex++;
                    transitionTo(State.WAIT_OPEN, 0);
                    break;
                }
                openContainer(client, pos);
                transitionTo(State.WAIT_CONTENT, 0);
            }
            case WAIT_CONTENT -> {
                if (tickCounter < 2) break; // Give the server a couple of ticks to respond
                if (SilentOpenManager.isContentReady()) {
                    // Content has arrived — fire the callback, close, advance
                    ContainerRepository repo = stashlight.getRepository();
                    if (repo != null && SilentOpenManager.getPendingPos() != null) {
                        BlockPos pos = SilentOpenManager.getPendingPos();
                        String dim = Util.getDimensionName(client.level);
                        BlockPos canonical = Util.getCanonicalPos(client.level, pos);
                        BlockState blockState = client.level.getBlockState(canonical);
                        String name = blockState.getBlock().getName().getString();

                        SilentOpenManager.onContentReady();

                        // Read content from menu before close
                        int menuId = SilentOpenManager.getExpectedContainerId();
                        closeContainer(client, menuId);
                        scannedCount++;
                    } else {
                        // Content ready but no pending pos — just close
                        int menuId = SilentOpenManager.getExpectedContainerId();
                        closeContainer(client, menuId);
                    }
                    candidateIndex++;
                    transitionTo(State.WAIT_INTERVAL, 0);
                } else if (tickCounter > 40) {
                    // Timeout waiting for content — close and skip
                    LOGGER.warn("Timeout waiting for container content, skipping");
                    int menuId = SilentOpenManager.getExpectedContainerId();
                    closeContainer(client, menuId);
                    candidateIndex++;
                    transitionTo(State.WAIT_INTERVAL, 0);
                }
            }
            case WAIT_INTERVAL -> {
                int intervalTicks = Math.max(1, cfg.loopIntervalMillis() / 50);
                if (tickCounter >= intervalTicks) {
                    transitionTo(State.WAIT_OPEN, 0);
                }
            }
            case FINISHED -> {
                String msg = "Scanned " + scannedCount + " containers";
                if (client.player != null) {
                    client.player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal(msg), true);
                }
                LOGGER.info(msg);
                running = false;
                state = State.IDLE;
            }
        }
    }

    private void start(Minecraft client) {
        if (client.level == null || client.player == null) return;

        // Close search screen if open
        if (client.screen instanceof dev.strangequark.stashlight.screen.SearchScreen) {
            client.setScreen(null);
        }

        running = true;
        state = State.BUILD_CANDIDATES;
        tickCounter = 0;
        LOGGER.info("VanillaScanner started");
    }

    private void stop(String reason) {
        LOGGER.info("VanillaScanner stopped: {}", reason);
        int menuId = SilentOpenManager.getExpectedContainerId();
        if (menuId >= 0) {
            closeContainer(Minecraft.getInstance(), menuId);
        }
        SilentOpenManager.finish();
        running = false;
        state = State.IDLE;
        candidates.clear();
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("Scanner: " + reason), true);
        }
    }

    private void buildCandidates(Minecraft client) {
        var cfg = Config.get().vanillaFallback();
        Set<String> allowedBlocks = parseBlockFilter(cfg.blockFilter());
        candidates = new ArrayList<>();

        Level level = client.level;
        LocalPlayer player = client.player;
        if (level == null || player == null) return;

        double reachSq = 4.5 * 4.5; // Vanilla reach ~4.5 blocks
        BlockPos center = player.blockPosition();

        // Scan within reach range
        int radius = 5; // ~4.5 blocks rounded up
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -2; dy <= 3; dy++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (pos.distSqr(center) > reachSq) continue;
                    if (!level.isLoaded(pos)) continue;

                    BlockState state = level.getBlockState(pos);
                    if (Util.isValidSearchableContainer(state)) {
                        String blockId = state.getBlock().builtInRegistryHolder().key().location().getPath();
                        if (allowedBlocks.contains(blockId)) {
                            candidates.add(pos);
                        }
                    }
                }
            }
        }
    }

    private boolean interruptCheck(Minecraft client) {
        // Re-toggle keybind
        if (comboKeybind.isPressed()) return true;
        // Player hurt
        if (client.player != null && client.player.hurtTime > 0) return true;
        // Search key pressed
        if (Stashlight.getInstance() != null) {
            var key = Stashlight.getSearchKey();
            if (key != null && key.consumeClick()) return true;
        }
        return false;
    }

    private void openContainer(Minecraft client, BlockPos pos) {
        SilentOpenManager.begin(pos, slots -> {
            // Content callback: content will be handled via isContentReady path
        });
        client.gameMode.useItemOn(
                client.player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)
        );
    }

    private void closeContainer(Minecraft client, int containerId) {
        if (containerId < 0) return;
        if (client.player == null) return;
        try {
            // Close via player — this sends ServerboundContainerClosePacket
            client.player.closeContainer();
        } catch (Exception e) {
            LOGGER.warn("Failed to close container {}", containerId, e);
        }
    }

    private boolean isInReach(LocalPlayer player, BlockPos pos) {
        return player.blockPosition().distSqr(pos) <= 4.5 * 4.5;
    }

    private static Set<String> parseBlockFilter(String filter) {
        if (filter == null || filter.isBlank()) {
            return new HashSet<>(Arrays.asList("chest", "barrel", "shulker_box"));
        }
        return new HashSet<>(Arrays.asList(filter.split(",")));
    }

    private void transitionTo(State newState, int tickDelay) {
        state = newState;
        tickCounter = 0;
    }

    public boolean isRunning() {
        return running;
    }
}
