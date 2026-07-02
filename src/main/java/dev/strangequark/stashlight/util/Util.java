package dev.strangequark.stashlight.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.Set;

public class Util {
    public static String getDimensionName(Level level) {
        return level.dimension().location().getPath();
    }

    public static boolean isValidSearchableContainer(BlockState state) {
        Block block = state.getBlock();

        if (!(block instanceof EntityBlock)) {
            return false;
        }

        return !(block instanceof EnderChestBlock || block instanceof EnchantingTableBlock || block instanceof BeaconBlock);
    }

    public static BlockPos getCanonicalPos(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock)) {
            return pos;
        }

        Set<BlockPos> positions = resolveContainerPositions(level, pos);
        if (positions.size() <= 1) {
            return pos;
        }

        // Deterministic canonical key for double chests: the smaller BlockPos.
        // Prevents mismatches when the player opens either half of the chest.
        return positions.stream().min(BlockPos::compareTo).orElse(pos);
    }

    public static Set<BlockPos> resolveContainerPositions(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);

        if (!(state.getBlock() instanceof EntityBlock)) {
            return Set.of(pos);
        }

        if (state.getBlock() instanceof ChestBlock) {
            ChestType type = state.getValue(ChestBlock.TYPE);

            if (type == ChestType.SINGLE) {
                return Set.of(pos);
            }

            Direction facing = state.getValue(ChestBlock.FACING);
            Direction offset = type == ChestType.LEFT ? facing.getClockWise() : facing.getCounterClockWise();
            BlockPos other = pos.relative(offset);

            if (level.getBlockState(other).getBlock() instanceof ChestBlock) {
                return Set.of(pos, other);
            }
        }

        return Set.of(pos);
    }

    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "X"};

    public static String toRoman(int level) {
        if (level >= 1 && level <= ROMAN.length) {
            return ROMAN[level - 1];
        }
        return String.valueOf(level);
    }
}
