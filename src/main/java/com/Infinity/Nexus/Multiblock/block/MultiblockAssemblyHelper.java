package com.Infinity.Nexus.Multiblock.block;

import com.Infinity.Nexus.Multiblock.api.BlockMatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public final class MultiblockAssemblyHelper {
    private MultiblockAssemblyHelper() {}

    public static @Nullable BlockState placementState(ItemStack stack, BlockMatcher matcher) {
        return placementState(stack, matcher, Rotation.NONE);
    }

    public static @Nullable BlockState placementState(ItemStack stack, BlockMatcher matcher, Rotation rotation) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
            return null;
        }
        Block block = blockItem.getBlock();
        BlockState defaultState = block.defaultBlockState();
        if (matcher.matches(defaultState, rotation)) {
            return defaultState;
        }
        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            if (matcher.matches(state, rotation)) {
                return state;
            }
        }
        return null;
    }

    public static @Nullable BlockState findPlacementState(Player player, BlockMatcher matcher) {
        return findPlacementState(player, matcher, Rotation.NONE);
    }

    public static @Nullable BlockState findPlacementState(Player player, BlockMatcher matcher, Rotation rotation) {
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            BlockState state = placementState(stack, matcher, rotation);
            if (state != null) {
                return state;
            }
        }
        return placementState(player.getOffhandItem(), matcher, rotation);
    }

    public static boolean hasPlacementItem(Player player, BlockMatcher matcher) {
        return hasPlacementItem(player, matcher, Rotation.NONE);
    }

    public static boolean hasPlacementItem(Player player, BlockMatcher matcher, Rotation rotation) {
        return player.getAbilities().instabuild || findPlacementState(player, matcher, rotation) != null;
    }

    public static @Nullable BlockState representativeState(BlockMatcher matcher) {
        return representativeState(matcher, Rotation.NONE);
    }

    public static @Nullable BlockState representativeState(BlockMatcher matcher, Rotation rotation) {
        for (Block block : BuiltInRegistries.BLOCK) {
            BlockState defaultState = block.defaultBlockState();
            if (matcher.matches(defaultState, rotation)) {
                return defaultState;
            }
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                if (matcher.matches(state, rotation)) {
                    return state;
                }
            }
        }
        return null;
    }
}
