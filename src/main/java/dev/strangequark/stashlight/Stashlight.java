package dev.strangequark.stashlight;

import dev.strangequark.stashlight.render.HighlightRenderer;
import dev.strangequark.stashlight.repository.ContainerRepository;
import dev.strangequark.stashlight.screen.SearchScreen;
import dev.strangequark.stashlight.serializer.Serializer;
import dev.strangequark.stashlight.util.Util;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class Stashlight implements ClientModInitializer {
    public static final String MOD_ID = "stashlight";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private Serializer serializer;
    private ContainerRepository repository;
    private static KeyBinding searchKey;
    public static final KeyBinding.Category STASHLIGHT = KeyBinding.Category.create(Identifier.of(MOD_ID, "stashlight"));

    private int tickCounter = 0;

    @Nullable
    private BlockPos lastOpened;

    @Override
    public void onInitializeClient() {
        Init.init();
        UseBlockCallback.EVENT.register(this::onBlockUsed);
        ClientPlayerBlockBreakEvents.AFTER.register(this::onBlockBreak);
        ScreenEvents.AFTER_INIT.register(this::onScreenInit);
        WorldRenderEvents.AFTER_ENTITIES.register(HighlightRenderer::render);

        searchKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.stashlight.search_menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_5,
                STASHLIGHT
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (searchKey.wasPressed()) {
                client.setScreen(new SearchScreen(repository));
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || repository == null) return;

            tickCounter++;

            if (tickCounter % 100 == 0) {
                repository.runCleanup(client.world);
            }

            if (tickCounter % 3000 == 0) {
                repository.saveIfDirty();
                tickCounter = 0;
            }
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            serializer = new Serializer(Init.getFileName(), handler.getRegistryManager());
            repository = new ContainerRepository(serializer);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (repository != null) {
                repository.shutdown();

            }
            serializer = null;
            repository = null;
        });
    }

    private ActionResult onBlockUsed(PlayerEntity playerEntity, World world, Hand hand, BlockHitResult blockHitResult) {
        BlockPos pos = blockHitResult.getBlockPos();
        BlockState state = world.getBlockState(pos);
        if (Util.isValidSearchableContainer(state)) {
            lastOpened = pos;
        }
        return ActionResult.PASS;
    }

    private void onBlockBreak(ClientWorld clientWorld, ClientPlayerEntity clientPlayerEntity, BlockPos blockPos, BlockState blockState) {
        if (repository == null || !(blockState.getBlock() instanceof BlockWithEntity)) {
            return;
        }

        // Use the canonical resolution to find the correct database key to delete.
        BlockPos canonicalPos = Util.getCanonicalPos(clientWorld, blockPos);
        String dimension = Util.getDimensionName(clientWorld);
        repository.remove(dimension, canonicalPos);
    }


    private void onScreenInit(MinecraftClient client, Screen screen, int w, int h) {
        if (client.world == null || screen instanceof CreativeInventoryScreen) {
            return;
        }

        if (screen instanceof HandledScreen<?> handled) {
            var handler = handled.getScreenHandler();
            // Serialize on close to ensure the database reflects the final state of the inventory.
            ScreenEvents.remove(screen).register(closedScreen -> serializeContainer(client, handler));
        }
    }

    private void serializeContainer(MinecraftClient client, ScreenHandler handler) {
        if (client.world == null || repository == null || lastOpened == null) {
            return;
        }

        String dimension = Util.getDimensionName(client.world);
        Set<BlockPos> pair = Util.resolveContainerPositions(client.world, lastOpened);
        BlockPos canonicalPos = Util.getCanonicalPos(client.world, pair.iterator().next());
        BlockState blockstate = client.world.getBlockState(canonicalPos);


        if (!Util.isValidSearchableContainer(blockstate)) {
            lastOpened = null;
            return;
        }

        var stacks = handler.getStacks();
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
        repository.update(
                dimension,
                canonicalPos,
                blockstate.getBlock().getName().getString(),
                containerSize,
                stacks.subList(0, containerSize)
        );

        lastOpened = null;
    }
}