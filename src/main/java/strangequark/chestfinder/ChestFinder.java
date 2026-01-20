package strangequark.chestfinder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.ChestType;
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
import strangequark.chestfinder.repository.ContainerRepository;
import strangequark.chestfinder.search.SearchScreenOwo;
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
        PlayerBlockBreakEvents.AFTER.register(this::onBlockBreak);

        searchKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Search",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_5,
                CHEST_FINDER
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (searchKey.wasPressed()) {
                client.setScreen(new SearchScreenOwo(repository));
            }
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            serializer = new Serializer(Init.getFileName(), handler.getRegistryManager());
            repository = new ContainerRepository(serializer);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (repository != null && serializer != null) {
                serializer.write(repository.getDatabase());
            }
            serializer = null;
            repository = null;
        });
    }

    private void onBlockBreak(World world, PlayerEntity playerEntity, BlockPos blockPos, BlockState blockState, @Nullable BlockEntity blockEntity) {
        if (repository == null) return;
        BlockPos targetPos = getNormalizedPos(blockState, blockPos);
        String dimension = world.getRegistryKey().getValue().getPath();
        repository.remove(dimension, targetPos);
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
            BlockPos rawPos = lastOpened.pop();
            BlockPos finalPos = getNormalizedPos(client.world.getBlockState(rawPos), rawPos);
            Block block = client.world.getBlockState(finalPos).getBlock();
            repository.update(dimension, finalPos, block.getName().getString(), containerStacks);
            lastOpened.clear();
        }
    }

    private BlockPos getNormalizedPos(BlockState state, BlockPos pos) {
        if (state.getBlock() instanceof ChestBlock) {
            ChestType type = state.get(ChestBlock.CHEST_TYPE);

            // If we clicked the RIGHT half, we want to swap to the LEFT half's position
            // so the data always stays on the same block.
            if (type == ChestType.RIGHT) {
                net.minecraft.util.math.Direction facing = state.get(ChestBlock.FACING);
                // The "Left" half is always Counter-Clockwise from the "Right" half's facing direction
                return pos.offset(facing.rotateYCounterclockwise());
            }
        }
        return pos;
    }
}
