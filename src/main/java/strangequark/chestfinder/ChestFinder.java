package strangequark.chestfinder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.EnchantingTableBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
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
import strangequark.chestfinder.render.HighlightRenderer;
import strangequark.chestfinder.repository.ContainerRepository;
import strangequark.chestfinder.screen.SearchScreen;
import strangequark.chestfinder.serializer.Serializer;
import strangequark.chestfinder.util.Util;

public class ChestFinder implements ClientModInitializer {
    public static final String MOD_ID = "chestfinder";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private Serializer serializer;
    private ContainerRepository repository;
    private static KeyBinding searchKey;
    public static final KeyBinding.Category CHEST_FINDER = KeyBinding.Category.create(Identifier.of(MOD_ID, "chestfinder"));

    @Nullable
    private BlockPos lastOpened;

    @Override
    public void onInitializeClient() {
        Init.init();
        UseBlockCallback.EVENT.register(this::onBlockUsed);
        // BEFORE is critical: we must resolve the chest's identity while it still exists in the world.
        PlayerBlockBreakEvents.BEFORE.register(this::onBlockBreak);
        ScreenEvents.AFTER_INIT.register(this::onScreenInit);
        WorldRenderEvents.AFTER_ENTITIES.register(HighlightRenderer::render);

        searchKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Search",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_5,
                CHEST_FINDER
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (searchKey.wasPressed()) {
                client.setScreen(new SearchScreen(repository));
            }
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            serializer = new Serializer(Init.getFileName(), handler.getRegistryManager());
            repository = new ContainerRepository(serializer);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (repository != null && serializer != null) {
                serializer.write(repository.getContainerEntriesMap());
            }
            serializer = null;
            repository = null;
        });
    }

    private ActionResult onBlockUsed(PlayerEntity playerEntity, World world, Hand hand, BlockHitResult blockHitResult) {
        BlockPos pos = blockHitResult.getBlockPos();
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof BlockWithEntity) {
            lastOpened = pos;
        }
        return ActionResult.PASS;
    }

    @SuppressWarnings("SameReturnValue")
    private boolean onBlockBreak(World world, PlayerEntity playerEntity, BlockPos blockPos, BlockState blockState, @Nullable BlockEntity blockEntity) {
        if (repository == null || !(blockState.getBlock() instanceof BlockWithEntity)) {
            return true;
        }

        // Use the canonical resolution to find the correct database key to delete.
        BlockPos canonicalPos = Util.getCanonicalPos(world, blockPos);
        String dimension = Util.getDimensionName(world);
        repository.remove(dimension, canonicalPos);
        return true;
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

        BlockPos rawPos = lastOpened;
        BlockPos canonicalPos = Util.getCanonicalPos(client.world, rawPos);
        String dimension = Util.getDimensionName(client.world);

        BlockState state = client.world.getBlockState(canonicalPos);

        if (!(state.getBlock() instanceof BlockWithEntity) || state.getBlock() instanceof EnderChestBlock || state.getBlock() instanceof EnchantingTableBlock) {
            lastOpened = null;
            return;
        }

        var stacks = handler.getStacks();
        int containerSize = stacks.size() - 36; // Standard survival inventory assumption
        if (containerSize <= 0) return;


        // INVARIANT: Always nuke both 'raw' and 'canonical' keys.
        // This handles cases where a single chest was just merged into a double chest,
        // or a double chest was split, ensuring no "ghost" records remain at the old coordinates.
        repository.remove(dimension, rawPos);
        repository.remove(dimension, canonicalPos);

        repository.update(
                dimension,
                canonicalPos,
                state.getBlock().getName().getString(),
                containerSize,
                stacks.subList(0, containerSize)
        );

        lastOpened = null;
    }
}