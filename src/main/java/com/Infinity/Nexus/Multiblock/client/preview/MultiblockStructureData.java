package com.Infinity.Nexus.Multiblock.client.preview;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.Infinity.Nexus.Multiblock.api.BlockMatcher;
import com.Infinity.Nexus.Multiblock.api.MultiblockDefinition;
import com.Infinity.Nexus.Multiblock.api.MultiblockPattern;
import com.Infinity.Nexus.Multiblock.block.MultiblockAssemblyHelper;
import com.Infinity.Nexus.Multiblock.block.MultiblockControllerBlock;
import com.Infinity.Nexus.Multiblock.registry.ModBlocks;
import com.Infinity.Nexus.Multiblock.registry.ModDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public record MultiblockStructureData(
        MultiblockDefinition definition,
        List<Cell> cells,
        List<Material> materials,
        ItemStack controller) {

    public MultiblockStructureData {
        cells = List.copyOf(cells);
        materials = List.copyOf(materials);
        controller = controller.copy();
    }

    public record Cell(
            int x,
            int y,
            int z,
            boolean anchor,
            List<BlockState> states,
            List<ItemStack> stacks) {

        public Cell {
            states = List.copyOf(states);
            stacks = copyStacks(stacks);
        }

        public int layer() {
            return this.y;
        }
    }

    public record Material(
            List<ItemStack> stacks,
            int count,
            @Nullable Component description) {

        public Material {
            stacks = copyStacks(stacks);
        }
    }

    public static MultiblockStructureData create(MultiblockDefinition definition) {
        return create(definition, ModBlocks.CONTROLLER.get());
    }

    public static MultiblockStructureData create(
            MultiblockDefinition definition,
            @Nullable Block controllerBlock) {
        List<Cell> cells = new ArrayList<>();
        Map<Object, MaterialBuilder> materials = new LinkedHashMap<>();
        ItemStack controller = controllerStack(controllerBlock, definition.id());
        List<BlockState> controllerStates = controllerStates(controllerBlock);

        for (MultiblockPattern.PatternCell cell : definition.pattern().cells()) {
            if (cell.anchor()) {
                List<ItemStack> controllerStacks = controller.isEmpty()
                        ? List.of()
                        : List.of(controller.copy());
                cells.add(new Cell(
                        cell.x(), cell.y(), cell.z(), true, controllerStates, controllerStacks));
                continue;
            }

            BlockMatcher matcher = cell.matcher();
            List<BlockState> states = displayStates(matcher);
            List<ItemStack> stacks = displayStacks(matcher);
            if (states.isEmpty() && stacks.isEmpty()) {
                BlockState representative = MultiblockAssemblyHelper.representativeState(matcher, Rotation.NONE);
                if (representative != null) {
                    states = List.of(representative);
                    stacks = itemStacks(representative.getBlock());
                }
            }

            cells.add(new Cell(cell.x(), cell.y(), cell.z(), false, states, stacks));

            List<ItemStack> materialStacks = stacks.isEmpty() ? itemStacks(states) : stacks;
            List<ItemStack> resolvedMaterialStacks = materialStacks.isEmpty()
                    ? List.of(new ItemStack(Items.BARRIER))
                    : materialStacks;
            Object key = materialKey(matcher, states, resolvedMaterialStacks);
            MaterialBuilder material = materials.computeIfAbsent(key,
                    ignored -> new MaterialBuilder(resolvedMaterialStacks, description(matcher)));
            material.count++;
        }

        List<Material> result = new ArrayList<>(materials.size());
        for (MaterialBuilder material : materials.values()) {
            result.add(new Material(material.stacks, material.count, material.description));
        }
        return new MultiblockStructureData(definition, cells, result, controller);
    }

    private static @Nullable Component description(BlockMatcher matcher) {
        return matcher.display().map(BlockMatcher.Display::description).orElse(null);
    }

    private static List<BlockState> displayStates(BlockMatcher matcher) {
        return matcher.display().map(BlockMatcher.Display::states).orElse(List.of());
    }

    private static List<ItemStack> displayStacks(BlockMatcher matcher) {
        return matcher.display().map(BlockMatcher.Display::stacks).orElse(List.of()).stream()
                .filter(stack -> !stack.isEmpty() && stack.getItem() != Items.AIR)
                .map(ItemStack::copy)
                .toList();
    }

    private static List<BlockState> controllerStates(@Nullable Block block) {
        if (!(block instanceof MultiblockControllerBlock controller)) {
            return List.of();
        }
        BlockState state = controller.defaultBlockState();
        if (state.hasProperty(MultiblockControllerBlock.FORMED)) {
            state = state.setValue(MultiblockControllerBlock.FORMED, false);
        }
        if (state.hasProperty(MultiblockControllerBlock.PROCESSING)) {
            state = state.setValue(MultiblockControllerBlock.PROCESSING, false);
        }
        return List.of(state);
    }

    private static ItemStack controllerStack(@Nullable Block block, Identifier definitionId) {
        if (block == null) {
            return ItemStack.EMPTY;
        }
        Item item = block.asItem();
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item);
        if (block == ModBlocks.CONTROLLER.get()) {
            stack.set(ModDataComponents.MULTIBLOCK_DEFINITION.get(), definitionId);
        }
        return stack;
    }

    private static List<ItemStack> itemStacks(Block block) {
        Item item = block.asItem();
        return item == Items.AIR ? List.of() : List.of(new ItemStack(item));
    }

    private static List<ItemStack> itemStacks(List<BlockState> states) {
        List<ItemStack> stacks = new ArrayList<>();
        for (BlockState state : states) {
            Item item = state.getBlock().asItem();
            if (item != Items.AIR) {
                ItemStack stack = new ItemStack(item);
                if (stacks.stream().noneMatch(existing -> existing.is(item))) {
                    stacks.add(stack);
                }
            }
        }
        return List.copyOf(stacks);
    }

    private static Object materialKey(
            BlockMatcher matcher,
            List<BlockState> states,
            List<ItemStack> stacks) {
        if (!stacks.isEmpty()) {
            ItemStack stack = stacks.getFirst();
            if (!stack.isEmpty() && stack.getItem() != Items.AIR && stack.getItem() != Items.BARRIER) {
                return stack.getItem();
            }
        }
        if (!states.isEmpty()) {
            return states.getFirst().getBlock();
        }
        return matcher;
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        return stacks.stream().map(ItemStack::copy).toList();
    }

    private static final class MaterialBuilder {
        private final List<ItemStack> stacks;
        private final @Nullable Component description;
        private int count;

        private MaterialBuilder(List<ItemStack> stacks, @Nullable Component description) {
            this.stacks = copyStacks(stacks);
            this.description = description;
        }
    }
}
