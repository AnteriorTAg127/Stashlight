package dev.strangequark.containerlookup.util;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.inventory.DoubleInventory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.Set;

public class Util {
    public static String getDimensionName(World world) {
        return world.getRegistryKey().getValue().getPath();
    }

    public static boolean isValidSearchableContainer(BlockState state) {
        Block block = state.getBlock();

        if (!(block instanceof BlockWithEntity)) {
            return false;
        }

        return !(block instanceof EnderChestBlock || block instanceof EnchantingTableBlock || block instanceof BeaconBlock);
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

    public static Set<BlockPos> resolveContainerPositions(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);

        if (!(state.getBlock() instanceof BlockWithEntity)) {
            return Set.of(pos);
        }

        if (state.getBlock() instanceof ChestBlock) {
            ChestType type = state.get(ChestBlock.CHEST_TYPE);

            if (type == ChestType.SINGLE) {
                return Set.of(pos);
            }

            Direction facing = state.get(ChestBlock.FACING);
            Direction offset = type == ChestType.LEFT ? facing.rotateYClockwise() : facing.rotateYCounterclockwise();
            BlockPos other = pos.offset(offset);

            if (world.getBlockState(other).getBlock() instanceof ChestBlock) {
                return Set.of(pos, other);
            }
        }

        return Set.of(pos);
    }
}
