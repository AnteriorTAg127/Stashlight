package strangequark.chestfinder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import strangequark.chestfinder.repository.ContainerRepository;
import strangequark.chestfinder.search.SearchScreen;
import strangequark.chestfinder.serializer.Serializer;

import java.util.ArrayDeque;
import java.util.Deque;

public class ChestFinder implements ClientModInitializer {
    public static final String MOD_ID = "chestfinder";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private Serializer serializer;
    private ContainerRepository repository;
    private static KeyBinding searchKey;
    public static final KeyBinding.Category CHEST_FINDER = KeyBinding.Category.create(Identifier.of(MOD_ID, "chestfinder"));

    private final Deque<BlockPos> lastOpened = new ArrayDeque<>();

    @Override
    public void onInitializeClient() {
        Init.init();
        UseBlockCallback.EVENT.register(this::onBlockUsed);
        ScreenEvents.AFTER_INIT.register(this::onScreenInit);

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
            serializer = new Serializer(Init.getFileName());
            repository = new ContainerRepository(serializer);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            serializer = null;
            repository = null;
        });
    }

    private ActionResult onBlockUsed(PlayerEntity playerEntity, World world, Hand hand, BlockHitResult blockHitResult) {
        BlockPos pos = blockHitResult.getBlockPos();
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (block instanceof BlockWithEntity) {
            lastOpened.push(pos);
        }
        return ActionResult.PASS;
    }

    private void onScreenInit(MinecraftClient client, Screen screen, int w, int h) {
        if (screen instanceof CreativeInventoryScreen) return;
        if (client.world == null) return;

        if (screen instanceof HandledScreen<?> handled) {
            var handler = handled.getScreenHandler();
            ScreenEvents.remove(screen).register(closedScreen -> serializeContainer(client, handler));
        }
    }

    private void serializeContainer(MinecraftClient client, ScreenHandler handler) {
        if (client.world == null) return;

        var stacks = handler.getStacks();
        int containerSize = stacks.size() - 36;
        if (containerSize <= 0) return;

        var containerStacks = stacks.subList(0, containerSize);
        String dimension = client.world.getRegistryKey().getValue().getPath();

        if (!lastOpened.isEmpty()) {
            BlockPos pos = lastOpened.pop();
            Block block = client.world.getBlockState(pos).getBlock();
            repository.save(dimension, Registries.BLOCK.getId(block).getPath(), pos, containerStacks);
            lastOpened.clear();
        }
    }
}
