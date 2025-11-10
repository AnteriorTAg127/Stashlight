package strangequark.chestfinder;

import net.fabricmc.api.ClientModInitializer;
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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import strangequark.chestfinder.serializer.Serializer;

import java.util.ArrayDeque;
import java.util.Deque;

public class ChestFinder implements ClientModInitializer {
    public static final String MOD_ID = "chestfinder";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private final Deque<BlockPos> lastOpened = new ArrayDeque<>();
    private Serializer serializer;

    @Override
    public void onInitializeClient() {
        Init.init();
        UseBlockCallback.EVENT.register(this::onBlockUsed);
        ScreenEvents.AFTER_INIT.register(this::onScreenInit);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                serializer = new Serializer(Init.getFileName())
        );

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                serializer = null
        );
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

            ScreenEvents.remove(screen).register(closedScreen -> logContainer(client, handler));
        }
    }

    private void logContainer(MinecraftClient client, ScreenHandler handler) {
        if (client.world == null) return;

        var stacks = handler.getStacks();
        int containerSize = stacks.size() - 36;
        if (containerSize <= 0) return;

        var containerStacks = stacks.subList(0, containerSize);
        String dimension = client.world.getRegistryKey().getValue().getPath();

        if (!lastOpened.isEmpty()) {
            BlockPos pos = lastOpened.pop();
            BlockState state = client.world.getBlockState(pos);
            Block block = state.getBlock();
            int[] posArray = new int[]{pos.getX(), pos.getY(), pos.getZ()};
            serializer.saveContainer(dimension, String.valueOf(Registries.ITEM.getId(block.asItem()).getPath()), posArray, containerStacks);
            lastOpened.clear();
        }
    }
}
