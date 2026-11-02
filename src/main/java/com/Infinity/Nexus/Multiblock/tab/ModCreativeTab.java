package com.Infinity.Nexus.Multiblock.tab;

import com.Infinity.Nexus.Multiblock.InfinityNexusMultiblock;
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
                    .displayItems((parameters, output) ->
                            ModBlocks.dynamicControllerItems().forEach(item -> output.accept(item.get())))
                    .build());

    private ModCreativeTab() {}

    private static ItemStack icon() {
        return ModBlocks.dynamicControllerItems().stream()
                .findFirst()
                .map(item -> new ItemStack(item.get()))
                .orElse(ItemStack.EMPTY);
    }

    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
