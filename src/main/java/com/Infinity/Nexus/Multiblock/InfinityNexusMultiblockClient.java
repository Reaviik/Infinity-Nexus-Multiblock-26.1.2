package com.Infinity.Nexus.Multiblock;

import com.Infinity.Nexus.Multiblock.client.model.ControllerBlockStateModel;
import com.Infinity.Nexus.Multiblock.client.model.ControllerSpecialRenderer;
import com.Infinity.Nexus.Multiblock.client.preview.MultiblockDiorama;
import com.Infinity.Nexus.Multiblock.client.preview.MultiblockPreview;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = InfinityNexusMultiblock.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = InfinityNexusMultiblock.MOD_ID, value = Dist.CLIENT)
public class InfinityNexusMultiblockClient {
    public InfinityNexusMultiblockClient(ModContainer container, IEventBus modBus) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(MultiblockDiorama::register);
        modBus.addListener(ControllerBlockStateModel.Unbaked::register);
        modBus.addListener(ControllerSpecialRenderer.Unbaked::register);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (event.isCanceled() || event.getMouseButton() != 0) {
            return;
        }
        MultiblockPreview preview = MultiblockPreview.hoveredOn(event.getScreen());
        if (preview == null) {
            return;
        }
        preview.drag(event.getDragX());
        event.setCanceled(true);
    }

    @SubscribeEvent
    static void onScreenClosing(ScreenEvent.Closing event) {
        MultiblockPreview.clearHovered(event.getScreen());
    }
}
