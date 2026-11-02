package com.Infinity.Nexus.Multiblock.client;

import com.Infinity.Nexus.Multiblock.InfinityNexusMultiblock;
import com.Infinity.Nexus.Multiblock.data.MultiblockDefinitions;
import com.Infinity.Nexus.Multiblock.registry.ModMenus;
import net.minecraft.world.item.crafting.RecipeMap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

@EventBusSubscriber(modid = InfinityNexusMultiblock.MOD_ID, value = Dist.CLIENT)
public final class ClientMultiblockEvents {
    private static volatile RecipeMap recipes = RecipeMap.EMPTY;

    private ClientMultiblockEvents() {}

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.CONTROLLER_MENU.get(), MultiblockScreen::new);
    }

    @SubscribeEvent
    public static void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
        MultiblockPreviewRenderer.onSubmit(event);
    }

    @SubscribeEvent
    public static void onRecipesReceived(RecipesReceivedEvent event) {
        recipes = event.getRecipeMap();
        notifyJei();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        recipes = RecipeMap.EMPTY;
        MultiblockClientCache.clear();
        MultiblockDefinitions.replaceAll(java.util.Map.of());
    }

    public static RecipeMap recipes() {
        return recipes;
    }

    private static void notifyJei() {
        if (!ModList.get().isLoaded("jei")) {
            return;
        }
        try {
            Class.forName("com.Infinity.Nexus.Multiblock.compat.jei.MultiblockJeiPlugin")
                    .getMethod("onRecipesUpdated")
                    .invoke(null);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
