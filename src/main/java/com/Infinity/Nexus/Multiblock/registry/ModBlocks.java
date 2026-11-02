package com.Infinity.Nexus.Multiblock.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.Infinity.Nexus.Multiblock.InfinityNexusMultiblock;
import com.Infinity.Nexus.Multiblock.block.MultiblockControllerBlock;
import com.Infinity.Nexus.Multiblock.block.MultiblockControllerBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
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

    public static final List<DynamicControllerSpec> DYNAMIC_CONTROLLER_SPECS = DynamicControllerRegistry.specs();

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MultiblockControllerBlockEntity>> DYNAMIC_CONTROLLER_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("dynamic_controller", ModBlocks::createDynamicBlockEntityType);

    public static final Map<Identifier, DeferredBlock<MultiblockControllerBlock>> DYNAMIC_CONTROLLERS =
            registerDynamicControllers();

    public static final Map<Identifier, DeferredItem<BlockItem>> DYNAMIC_CONTROLLER_ITEMS =
            registerDynamicItems();

    private static Map<Identifier, DeferredBlock<MultiblockControllerBlock>> registerDynamicControllers() {
        Map<Identifier, DeferredBlock<MultiblockControllerBlock>> blocks = new LinkedHashMap<>();
        for (DynamicControllerSpec spec : DYNAMIC_CONTROLLER_SPECS) {
            DeferredBlock<MultiblockControllerBlock> block = BLOCKS.registerBlock(
                    spec.blockId().getPath(),
                    properties -> new MultiblockControllerBlock(
                            spec.definitionId(),
                            () -> DYNAMIC_CONTROLLER_BLOCK_ENTITY.get(),
                            properties),
                    properties -> properties
                            .mapColor(MapColor.METAL)
                            .strength(4.0F)
                            .requiresCorrectToolForDrops()
                            .lightLevel(state -> state.getValue(MultiblockControllerBlock.PROCESSING)
                                    ? spec.lightLevel()
                                    : 0));
            blocks.put(spec.blockId(), block);
        }
        return Collections.unmodifiableMap(blocks);
    }

    private static Map<Identifier, DeferredItem<BlockItem>> registerDynamicItems() {
        Map<Identifier, DeferredItem<BlockItem>> items = new LinkedHashMap<>();
        DYNAMIC_CONTROLLERS.forEach((id, block) ->
                items.put(id, ITEMS.registerSimpleBlockItem(id.getPath(), block)));
        return Collections.unmodifiableMap(items);
    }

    private static BlockEntityType<MultiblockControllerBlockEntity> createDynamicBlockEntityType() {
        Set<Block> validBlocks = DYNAMIC_CONTROLLERS.values().stream()
                .map(holder -> (Block) holder.get())
                .collect(Collectors.toUnmodifiableSet());
        return new BlockEntityType<>(
                (pos, state) -> new MultiblockControllerBlockEntity(
                        DYNAMIC_CONTROLLER_BLOCK_ENTITY.get(), pos, state),
                validBlocks);
    }

    private ModBlocks() {}

    public static List<DeferredItem<BlockItem>> dynamicControllerItems() {
        return List.copyOf(DYNAMIC_CONTROLLER_ITEMS.values());
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
    }
}
