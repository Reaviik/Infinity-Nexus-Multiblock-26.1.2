package com.Infinity.Nexus.Multiblock.registry;

import java.util.List;

import com.Infinity.Nexus.Multiblock.InfinityNexusMultiblock;
import com.Infinity.Nexus.Multiblock.api.addon.StoredResource;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENT_TYPES =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, InfinityNexusMultiblock.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Identifier>> MULTIBLOCK_DEFINITION =
            DATA_COMPONENT_TYPES.register("multiblock_definition",
                    () -> DataComponentType.<Identifier>builder()
                            .persistent(Identifier.CODEC)
                            .networkSynchronized(Identifier.STREAM_CODEC)
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<StoredResource>>> MULTIBLOCK_RESOURCES =
            DATA_COMPONENT_TYPES.register("multiblock_resources",
                    () -> DataComponentType.<List<StoredResource>>builder()
                            .persistent(StoredResource.CODEC.listOf())
                            .networkSynchronized(StoredResource.LIST_STREAM_CODEC)
                            .build());

    private ModDataComponents() {}

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENT_TYPES.register(modEventBus);
    }
}
