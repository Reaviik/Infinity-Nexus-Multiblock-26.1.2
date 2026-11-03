package com.Infinity.Nexus.Multiblock.tab;

import java.util.Collection;

import com.Infinity.Nexus.Multiblock.InfinityNexusMultiblock;
import com.Infinity.Nexus.Multiblock.api.MultiblockDefinition;
import com.Infinity.Nexus.Multiblock.data.MultiblockDefinitions;
import com.Infinity.Nexus.Multiblock.registry.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTab {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, InfinityNexusMultiblock.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MULTIBLOCK =
            CREATIVE_MODE_TABS.register("multiblock", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.infinity_nexus_multiblock"))
                    .icon(ModCreativeTab::icon)
                    .displayItems((parameters, output) -> {
                        Collection<MultiblockDefinition> definitions = MultiblockDefinitions.all();
                        if (definitions.isEmpty()) {
                            output.accept(ModBlocks.CONTROLLER_ITEM.get());
                            return;
                        }
                        for (MultiblockDefinition definition : definitions) {
                            output.accept(ModBlocks.controllerWithDefinition(definition.id()));
                        }
                    })
                    .build());

    private ModCreativeTab() {}

    private static ItemStack icon() {
        return MultiblockDefinitions.all().stream()
                .findFirst()
                .map(definition -> ModBlocks.controllerWithDefinition(definition.id()))
                .orElseGet(() -> new ItemStack(ModBlocks.CONTROLLER_ITEM.get()));
    }

    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
