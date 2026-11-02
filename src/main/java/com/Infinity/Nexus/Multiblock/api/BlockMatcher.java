package com.Infinity.Nexus.Multiblock.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface BlockMatcher {
    boolean matches(BlockState state);

    default boolean matches(BlockState state, Rotation rotation) {
        return matches(state);
    }

    default Optional<Display> display() {
        return Optional.empty();
    }

    static BlockMatcher of(Block block) {
        return new BlockMatcher() {
            private volatile @Nullable Display cached;

            @Override
            public boolean matches(BlockState state) {
                return state.is(block);
            }

            @Override
            public Optional<Display> display() {
                Display current = this.cached;
                if (current == null) {
                    current = new Display(
                            List.of(block.defaultBlockState()),
                            itemStacks(block),
                            null);
                    this.cached = current;
                }
                return Optional.of(current);
            }
        };
    }

    static BlockMatcher tag(TagKey<Block> tag) {
        return new BlockMatcher() {
            private volatile @Nullable Display cached;

            @Override
            public boolean matches(BlockState state) {
                return state.is(tag);
            }

            @Override
            public Optional<Display> display() {
                Display current = this.cached;
                if (current == null) {
                    List<BlockState> states = new ArrayList<>();
                    List<ItemStack> stacks = new ArrayList<>();
                    BuiltInRegistries.BLOCK.getTagOrEmpty(tag).forEach(holder -> {
                        Block block = holder.value();
                        states.add(block.defaultBlockState());
                        stacks.addAll(itemStacks(block));
                    });
                    current = new Display(
                            List.copyOf(states),
                            List.copyOf(stacks),
                            Component.literal("#" + tag.location()));
                    if (!states.isEmpty() || !stacks.isEmpty()) {
                        this.cached = current;
                    }
                }
                return Optional.of(current);
            }
        };
    }

    static BlockMatcher holderSet(HolderSet<Block> blocks) {
        return new BlockMatcher() {
            private volatile @Nullable Display cached;

            @Override
            public boolean matches(BlockState state) {
                return state.is(blocks);
            }

            @Override
            public Optional<Display> display() {
                Display current = this.cached;
                if (current == null) {
                    List<BlockState> states = new ArrayList<>();
                    List<ItemStack> stacks = new ArrayList<>();
                    blocks.forEach(holder -> {
                        Block block = holder.value();
                        states.add(block.defaultBlockState());
                        stacks.addAll(itemStacks(block));
                    });
                    current = new Display(List.copyOf(states), List.copyOf(stacks), null);
                    this.cached = current;
                }
                return Optional.of(current);
            }
        };
    }

    static BlockMatcher state(BlockState expected) {
        return new BlockMatcher() {
            private volatile @Nullable Display cached;

            @Override
            public boolean matches(BlockState state) {
                return state == expected;
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean matches(BlockState state, Rotation rotation) {
                return state == expected.rotate(rotation);
            }

            @Override
            public Optional<Display> display() {
                Display current = this.cached;
                if (current == null) {
                    current = new Display(
                            List.of(expected),
                            itemStacks(expected.getBlock()),
                            null);
                    this.cached = current;
                }
                return Optional.of(current);
            }
        };
    }

    record Display(
            List<BlockState> states,
            List<ItemStack> stacks,
            @Nullable Component description) {
        public Display {
            states = List.copyOf(states);
            stacks = List.copyOf(stacks);
        }
    }

    private static List<ItemStack> itemStacks(Block block) {
        Item item = block.asItem();
        return item == Items.AIR ? List.of() : List.of(new ItemStack(item));
    }
}
