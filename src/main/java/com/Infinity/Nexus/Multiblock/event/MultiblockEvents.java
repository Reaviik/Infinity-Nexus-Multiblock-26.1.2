package com.Infinity.Nexus.Multiblock.event;

import com.Infinity.Nexus.Multiblock.InfinityNexusMultiblock;
import com.Infinity.Nexus.Multiblock.block.MultiblockControllerBlock;
import com.Infinity.Nexus.Multiblock.block.MultiblockControllerBlockEntity;
import com.Infinity.Nexus.Multiblock.client.MultiblockClientCache;
import com.Infinity.Nexus.Multiblock.data.MultiblockSavedData;
import com.Infinity.Nexus.Multiblock.network.MultiblockDefinitionSyncPayload;
import com.Infinity.Nexus.Multiblock.registry.ModRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = InfinityNexusMultiblock.MOD_ID)
public final class MultiblockEvents {
    private MultiblockEvents() {}

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        event.sendRecipes(ModRecipes.types().toArray(RecipeType<?>[]::new));
        MultiblockDefinitionSyncPayload payload = MultiblockDefinitionSyncPayload.create();
        event.getRelevantPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, payload));
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        Player player = event.getEntity();
        if (player.isSecondaryUseActive()) {
            return;
        }

        Level level = event.getLevel();
        BlockPos clickedPos = event.getPos();
        if (level.getBlockState(clickedPos).getBlock() instanceof MultiblockControllerBlock) {
            return;
        }

        BlockPos controllerPos;
        if (level.isClientSide()) {
            controllerPos = MultiblockClientCache.getController(level, clickedPos);
        } else if (level instanceof ServerLevel serverLevel) {
            MultiblockSavedData data = MultiblockSavedData.getIfPresent(serverLevel);
            controllerPos = data == null ? null : data.getController(clickedPos);
        } else {
            return;
        }

        if (controllerPos == null) {
            return;
        }

        if (!(level.getBlockEntity(controllerPos) instanceof MultiblockControllerBlockEntity controller) || !controller.isFormed()) {
            return;
        }

        if (level.isClientSide()) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        } else if (player.openMenu(controller).isPresent()) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreakBlock(BreakBlockEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        MultiblockSavedData data = MultiblockSavedData.getIfPresent(level);
        if (data == null) {
            return;
        }

        BlockPos controllerPos = data.getController(event.getPos());
        if (controllerPos == null) {
            return;
        }

        if (level.getBlockEntity(controllerPos) instanceof MultiblockControllerBlockEntity controller) {
            controller.invalidateStructure();
        } else {
            data.unregisterController(controllerPos);
        }
    }
}
