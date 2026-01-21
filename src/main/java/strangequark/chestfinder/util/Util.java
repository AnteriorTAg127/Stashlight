package strangequark.chestfinder.util;

import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.DoubleInventory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class Util {
    public static String getDimensionName(World world) {
        return world.getRegistryKey().getValue().getPath();
    }

    public static BlockPos getCanonicalPos(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock chest)) {
            return pos;
        }

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
