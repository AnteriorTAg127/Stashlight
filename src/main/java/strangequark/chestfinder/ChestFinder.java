package strangequark.chestfinder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.DoubleInventory;
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
import strangequark.chestfinder.search.SearchScreen;
import strangequark.chestfinder.serializer.Serializer;

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
        ScreenEvents.AFTER_INIT.register(this::onScreenInit);

        // BEFORE is critical: we must resolve the chest's identity while it still exists in the world.
        PlayerBlockBreakEvents.BEFORE.register(this::onBlockBreak);

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
                serializer.write(repository.getDatabase());
            }
            serializer = null;
            repository = null;
        });
    }

    @SuppressWarnings("SameReturnValue")
    private boolean onBlockBreak(World world, PlayerEntity playerEntity, BlockPos blockPos, BlockState blockState, @Nullable BlockEntity blockEntity) {
        if (repository == null || !(blockState.getBlock() instanceof ChestBlock)) {
            return true;
        }

        // Use the same canonical resolution used during saving to find the correct database key to delete.
        BlockPos canonicalPos = getCanonicalChestPos(world, blockPos);
        String dimension = world.getRegistryKey().getValue().toString();
        repository.remove(dimension, canonicalPos);
        return true;
    }

    private ActionResult onBlockUsed(PlayerEntity playerEntity, World world, Hand hand, BlockHitResult blockHitResult) {
        BlockPos pos = blockHitResult.getBlockPos();
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof BlockWithEntity) {
            lastOpened = pos;
        }
        return ActionResult.PASS;
    }

    private void onScreenInit(MinecraftClient client, Screen screen, int w, int h) {
        if (screen instanceof CreativeInventoryScreen || client.world == null) {
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

        var stacks = handler.getStacks();
        int containerSize = stacks.size() - 36; // Standard survival inventory assumption
        if (containerSize <= 0) return;

        String dimension = client.world.getRegistryKey().getValue().toString();
        BlockPos raw = lastOpened;
        BlockPos canonical = getCanonicalChestPos(client.world, raw);

        // INVARIANT: Always nuke both 'raw' and 'canonical' keys.
        // This handles cases where a single chest was just merged into a double chest,
        // or a double chest was split, ensuring no "ghost" records remain at the old coordinates.
        repository.remove(dimension, raw);
        repository.remove(dimension, canonical);

        BlockState state = client.world.getBlockState(canonical);
        repository.update(
                dimension,
                canonical,
                state.getBlock().getName().getString(),
                containerSize,
                stacks.subList(0, containerSize)
        );

        lastOpened = null;
    }

    private BlockPos getCanonicalChestPos(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock chest)) return pos;

        // Ask the vanilla ChestBlock to resolve the inventory. This is our Source of Truth.
        var inv = ChestBlock.getInventory(chest, state, world, pos, true);

        if (inv instanceof DoubleInventory di) {
            try {
                // We use reflection to find the 'first' half of the DoubleInventory.
                // This aligns our database key with Minecraft's internal 'Master' half.
                var f = DoubleInventory.class.getDeclaredField("first");
                f.setAccessible(true);
                var first = f.get(di);
                if (first instanceof BlockEntity be) {
                    return be.getPos();
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        return pos;
    }
}