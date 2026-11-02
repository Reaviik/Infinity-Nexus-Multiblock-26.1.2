package com.Infinity.Nexus.Multiblock.registry;

import com.Infinity.Nexus.Multiblock.InfinityNexusMultiblock;
import com.Infinity.Nexus.Multiblock.menu.MultiblockMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, InfinityNexusMultiblock.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<MultiblockMenu>> CONTROLLER_MENU =
            MENUS.register("controller", () -> IMenuTypeExtension.create(MultiblockMenu::new));

    private ModMenus() {}

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
