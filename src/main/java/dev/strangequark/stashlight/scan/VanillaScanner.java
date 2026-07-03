package dev.strangequark.stashlight.scan;

import dev.strangequark.stashlight.Stashlight;
import dev.strangequark.stashlight.config.Config;
import dev.strangequark.stashlight.repository.ContainerRepository;
import dev.strangequark.stashlight.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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
 * Triggered by a ComboKeybind (like Masa mods) configured in the settings screen.
 * Runs a non-blocking state machine driven by the client tick.
 */
public final class VanillaScanner {

    private static final Logger LOGGER = LoggerFactory.getLogger("Stashlight/VanillaScanner");

    private enum State { IDLE, BUILD_CANDIDATES, WAIT_OPEN, WAIT_CONTENT, WAIT_INTERVAL, FINISHED }

    private State state = State.IDLE;
    private List<BlockPos> candidates = new ArrayList<>();
    private int candidateIndex = 0;
    private int scannedCount = 0;
    private int tickCounter = 0;
    private boolean running = false;

    private ComboKeybind comboKeybind;

    public VanillaScanner() {
        reloadKeybind();
    }

    /**
     * Rebuild the ComboKeybind from current config. Called by the settings screen
     * after the user binds a new key.
     */
    public void reloadKeybind() {
        var cfg = Config.get().vanillaFallback();
        String keyName = cfg.scanComboKey();
        String mods = cfg.scanComboMods();
        ComboKeybind newBind = ComboKeybind.fromConfig(keyName, mods);

        // Preserve rising-edge state if the bind actually changed
        if (comboKeybind != null && comboKeybind.isPressed()) {
            // Don't reset while held — let the player release first
        }
        this.comboKeybind = newBind;
    }

    /**
     * Called every END_CLIENT_TICK. Drives the non-blocking state machine.
     */
    public void tick(Minecraft client) {
        var cfg = Config.get().vanillaFallback();
        var stashlight = Stashlight.getInstance();
        if (stashlight == null) return;

        // Toggle on ComboKeybind rising edge (like Masa mods)
        if (comboKeybind != null && comboKeybind.consumeClick()) {
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
                if (tickCounter < 1) {
                    LOGGER.debug("WAIT_OPEN tick={} pos={}/{}", tickCounter, candidateIndex, candidates.size());
                    break;
                }
                if (candidateIndex >= candidates.size()
                        || scannedCount >= cfg.maxContainersPerLoop()) {
                    LOGGER.debug("WAIT_OPEN done, scanned={}/{}", scannedCount, cfg.maxContainersPerLoop());
                    transitionTo(State.FINISHED, 0);
                    break;
                }
                if (interruptCheck(client)) {
                    stop("Interrupted");
                    return;
                }
                BlockPos pos = candidates.get(candidateIndex);
                if (!isInReach(client.player, pos)) {
                    LOGGER.debug("WAIT_OPEN pos {} out of reach, skip", pos);
                    candidateIndex++;
                    transitionTo(State.WAIT_OPEN, 0);
                    break;
                }
                LOGGER.info("Opening container {} at {}", candidateIndex, pos);
                openContainer(client, pos);
                transitionTo(State.WAIT_CONTENT, 0);
            }
            case WAIT_CONTENT -> {
                LOGGER.debug("WAIT_CONTENT tick={} silent={} sc={}", tickCounter,
                        SilentOpenManager.isSilent(), scannedCount);
                if (tickCounter < 2) break; // Give the server a couple of ticks to respond
                if (SilentOpenManager.isContentReady()) {
                    int menuId = SilentOpenManager.getExpectedContainerId();
                    BlockPos pos = SilentOpenManager.getPendingPos();
                    LOGGER.info("Content ready for pos={} menuId={}, closing", pos, menuId);

                    // Read container contents from the menu while still in silent mode
                    ContainerRepository repo = stashlight.getRepository();
                    if (repo != null && pos != null) {
                        String dim = Util.getDimensionName(client.level);
                        BlockPos canonical = Util.getCanonicalPos(client.level, pos);
                        BlockState blockState = client.level.getBlockState(canonical);
                        String name = blockState.getBlock().getName().getString();

                        // Extract only container slots (non-player-inventory)
                        var mc = Minecraft.getInstance();
                        List<dev.strangequark.stashlight.model.SlotStack> slots = new ArrayList<>();
                        int containerSize = 0;
                        if (mc.player != null) {
                            for (var slot : mc.player.containerMenu.slots) {
                                if (slot.container == mc.player.getInventory()) continue;
                                containerSize++;
                                var stack = slot.getItem();
                                if (!stack.isEmpty()) {
                                    slots.add(new dev.strangequark.stashlight.model.SlotStack(slot.index, stack.copy()));
                                }
                            }
                        }

                        repo.remove(dim, canonical);
                        repo.update(dim, canonical, name, containerSize, slots);
                    }

                    // Close container BEFORE finishing silent mode — this prevents
                    // the chest GUI from popping up when closeContainer triggers setScreen
                    closeContainer(client, menuId);
                    scannedCount++;

                    // Now safe to finish
                    SilentOpenManager.finish();
                    candidateIndex++;
                    transitionTo(State.WAIT_INTERVAL, 0);
                } else if (tickCounter > 40) {
                    // Timeout waiting for content — close and skip
                    LOGGER.warn("Timeout waiting for container content, skipping");
                    int menuId = SilentOpenManager.getExpectedContainerId();
                    closeContainer(client, menuId);
                    SilentOpenManager.finish();
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
                if (client.player != null) {
                    client.player.displayClientMessage(
                            Component.translatable("gui.stashlight.message.scanned", scannedCount), true);
                }
                LOGGER.info("Scanned {} containers", scannedCount);
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
        LOGGER.info("stop: reason={}, silent={}, menuId={}", reason,
                SilentOpenManager.isSilent(), SilentOpenManager.getExpectedContainerId());
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
                    Component.translatable("gui.stashlight.message.scannerStop", reason), true);
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
        java.util.HashSet<BlockPos> seen = new java.util.HashSet<>();

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
                        if (matchesAllowed(blockId, allowedBlocks)) {
                            BlockPos canonical = Util.getCanonicalPos(level, pos);
                            if (seen.add(canonical)) {
                                candidates.add(canonical);
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean interruptCheck(Minecraft client) {
        // Re-toggle ComboKeybind
        if (comboKeybind != null && comboKeybind.consumeClick()) return true;
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
        LOGGER.info("openContainer: begin silent at {}, silent={}", pos, SilentOpenManager.isSilent());
        SilentOpenManager.begin(pos, slots -> {
            // Content callback: content will be handled via isContentReady path
            LOGGER.debug("openContainer: callback fired with {} slots (should not happen)", slots.size());
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
        LOGGER.info("closeContainer: id={}, silent={}", containerId, SilentOpenManager.isSilent());
        try {
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

    /**
     * Match a blockId against the allowed-token set. A token matches when it
     * equals the blockId, or the blockId ends with {@code "_" + token} — so a
     * single {@code shulker_box} token covers all 17 dyed variants
     * (orange_shulker_box, white_shulker_box, ...), and {@code chest} also
     * covers trapped_chest.
     */
    private static boolean matchesAllowed(String blockId, Set<String> allowed) {
        if (allowed.contains(blockId)) return true;
        for (String token : allowed) {
            if (blockId.endsWith("_" + token)) return true;
        }
        return false;
    }

    private void transitionTo(State newState, int tickDelay) {
        state = newState;
        tickCounter = 0;
    }

    public boolean isRunning() {
        return running;
    }
}
