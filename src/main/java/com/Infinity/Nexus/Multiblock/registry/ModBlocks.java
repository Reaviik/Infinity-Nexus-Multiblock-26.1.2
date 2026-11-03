package com.Infinity.Nexus.Multiblock.registry;

import java.util.List;
import java.util.Set;

import com.Infinity.Nexus.Multiblock.InfinityNexusMultiblock;
import com.Infinity.Nexus.Multiblock.block.MultiblockControllerBlock;
import com.Infinity.Nexus.Multiblock.block.MultiblockControllerBlockEntity;
import com.Infinity.Nexus.Multiblock.item.MultiblockControllerItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(InfinityNexusMultiblock.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(InfinityNexusMultiblock.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, InfinityNexusMultiblock.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MultiblockControllerBlockEntity>>
            CONTROLLER_BLOCK_ENTITY = BLOCK_ENTITIES.register(
                    "multiblock_controller", ModBlocks::createBlockEntityType);

    public static final DeferredBlock<MultiblockControllerBlock> CONTROLLER = BLOCKS.registerBlock(
            "multiblock_controller",
            properties -> new MultiblockControllerBlock(
                    () -> CONTROLLER_BLOCK_ENTITY.get(),
                    properties),
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .strength(4.0F)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(MultiblockControllerBlock.PROCESSING) ? 15 : 0));

    public static final DeferredItem<BlockItem> CONTROLLER_ITEM =
            ITEMS.registerItem("multiblock_controller",
                    properties -> new MultiblockControllerItem(CONTROLLER.get(), properties.useBlockDescriptionPrefix()));

    private ModBlocks() {}

    private static BlockEntityType<MultiblockControllerBlockEntity> createBlockEntityType() {
        return new BlockEntityType<>(
                (pos, state) -> new MultiblockControllerBlockEntity(
                        CONTROLLER_BLOCK_ENTITY.get(), pos, state),
                Set.<Block>of(CONTROLLER.get()));
    }

    public static List<DeferredItem<BlockItem>> controllerItems() {
        return List.of(CONTROLLER_ITEM);
    }

    public static ItemStack controllerWithDefinition(Identifier definitionId) {
        ItemStack stack = new ItemStack(CONTROLLER_ITEM.get());
        stack.set(ModDataComponents.MULTIBLOCK_DEFINITION.get(), definitionId);
        return stack;
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
    }
}
