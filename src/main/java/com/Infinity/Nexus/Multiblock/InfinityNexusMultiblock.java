package com.Infinity.Nexus.Multiblock;

import com.Infinity.Nexus.Multiblock.api.CapabilityPorts;
import com.Infinity.Nexus.Multiblock.block.MultiblockControllerBlockEntity;
import com.Infinity.Nexus.Multiblock.command.MultiblockCommands;
import com.Infinity.Nexus.Multiblock.config.Config;
import com.Infinity.Nexus.Multiblock.data.MultiblockReloadListeners;
import com.Infinity.Nexus.Multiblock.data.MultiblockSavedData;
import com.Infinity.Nexus.Multiblock.entity.ModEntities;
import com.Infinity.Nexus.Multiblock.gametest.MultiblockGameTests;
import com.Infinity.Nexus.Multiblock.network.*;
import com.Infinity.Nexus.Multiblock.registry.ModBlocks;
import com.Infinity.Nexus.Multiblock.registry.ModDataComponents;
import com.Infinity.Nexus.Multiblock.registry.ModMenus;
import com.Infinity.Nexus.Multiblock.registry.ModRecipes;
import com.Infinity.Nexus.Multiblock.resource.ControllerRecipePack;
import com.Infinity.Nexus.Multiblock.tab.ModCreativeTab;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Mod(InfinityNexusMultiblock.MOD_ID)
public class InfinityNexusMultiblock {
    public static final String MOD_ID = "infinity_nexus_multiblock";
    public static final Logger LOGGER = LogUtils.getLogger();
    public InfinityNexusMultiblock(IEventBus modEventBus, ModContainer modContainer) {
        ModDataComponents.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModEntities.register(modEventBus);
        ModMenus.register(modEventBus);
        ModRecipes.register(modEventBus);
        ModCreativeTab.register(modEventBus);
        modEventBus.addListener(EventPriority.HIGHEST, this::registerCapabilities);
        modEventBus.addListener(this::registerPayloads);
        modEventBus.addListener(MultiblockGameTests::register);
        modEventBus.addListener(ControllerRecipePack::addTo);
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.addListener(MultiblockReloadListeners::onAddServerReloadListeners);
        NeoForge.EVENT_BUS.addListener(MultiblockCommands::register);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        registerMultiblockCapabilities(event, ModBlocks.CONTROLLER_BLOCK_ENTITY.get());
        Block[] blocks = BuiltInRegistries.BLOCK.stream().toArray(Block[]::new);
        event.registerBlock(
                Capabilities.Item.BLOCK,
                InfinityNexusMultiblock::getMemberItemHandler,
                blocks);
        event.registerBlock(
                Capabilities.Energy.BLOCK,
                InfinityNexusMultiblock::getMemberEnergyHandler,
                blocks);
        event.registerBlock(
                Capabilities.Fluid.BLOCK,
                InfinityNexusMultiblock::getMemberFluidHandler,
                blocks);
    }

    private static @Nullable ResourceHandler<ItemResource> getMemberItemHandler(
            Level level,
            BlockPos pos,
            BlockState state,
            @Nullable BlockEntity blockEntity,
            @Nullable Direction side) {
        MultiblockControllerBlockEntity controller = getFormedController(level, pos);
        if (controller == null || !controller.allowsCapability(pos, CapabilityPorts.CapabilityType.ITEM)) {
            return null;
        }
        if (side == null) {
            return VanillaContainerWrapper.of(controller.getInternalItemHandler());
        }
        return new WorldlyContainerWrapper(controller.getInternalItemHandler(), side);
    }

    private static @Nullable EnergyHandler getMemberEnergyHandler(
            Level level,
            BlockPos pos,
            BlockState state,
            @Nullable BlockEntity blockEntity,
            @Nullable Direction side) {
        MultiblockControllerBlockEntity controller = getFormedController(level, pos);
        if (controller == null || !controller.allowsCapability(pos, CapabilityPorts.CapabilityType.ENERGY)) {
            return null;
        }
        return controller.getEnergyHandler(side);
    }

    private static @Nullable ResourceHandler<FluidResource> getMemberFluidHandler(
            Level level,
            BlockPos pos,
            BlockState state,
            @Nullable BlockEntity blockEntity,
            @Nullable Direction side) {
        MultiblockControllerBlockEntity controller = getFormedController(level, pos);
        if (controller == null || !controller.allowsCapability(pos, CapabilityPorts.CapabilityType.FLUID)) {
            return null;
        }
        return controller.getFluidHandler(pos, side);
    }

    private static @Nullable MultiblockControllerBlockEntity getFormedController(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        MultiblockSavedData data = MultiblockSavedData.getIfPresent(serverLevel);
        BlockPos controllerPos = data == null ? null : data.getController(pos);
        if (controllerPos == null) {
            return null;
        }
        if (serverLevel.getBlockEntity(controllerPos) instanceof MultiblockControllerBlockEntity controller
                && controller.isFormed()) {
            return controller;
        }
        return null;
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(
                MultiblockDefinitionSyncPayload.TYPE,
                MultiblockDefinitionSyncPayload.CODEC,
                MultiblockDefinitionSyncPayload::handle);
        event.registrar("1").playToClient(
                MultiblockExportPayload.TYPE,
                MultiblockExportPayload.CODEC,
                MultiblockExportPayload::handle);
        event.registrar("1").playToClient(
                MultiblockBlockedHighlightPayload.TYPE,
                MultiblockBlockedHighlightPayload.CODEC,
                MultiblockBlockedHighlightPayload::handle);
        event.registrar("1").playToClient(
                BoosterHighlightPayload.TYPE,
                BoosterHighlightPayload.CODEC,
                BoosterHighlightPayload::handle);
        event.registrar("1").playToServer(
                RequestBoosterHighlightPayload.TYPE,
                RequestBoosterHighlightPayload.CODEC,
                RequestBoosterHighlightPayload::handle);
        event.registrar("1").playToServer(
                SetSlotLockPayload.TYPE,
                SetSlotLockPayload.CODEC,
                SetSlotLockPayload::handle);
        event.registrar("1").playToServer(
                SetFluidSlotTankPayload.TYPE,
                SetFluidSlotTankPayload.CODEC,
                SetFluidSlotTankPayload::handle);
        event.registrar("1").playToServer(
                TankInteractPayload.TYPE,
                TankInteractPayload.CODEC,
                TankInteractPayload::handle);
    }

    private static void registerMultiblockCapabilities(RegisterCapabilitiesEvent event,
            BlockEntityType<MultiblockControllerBlockEntity> type) {
        event.registerBlockEntity(
                Capabilities.Item.BLOCK,
                type,
                (blockEntity, context) -> {
                    if (!blockEntity.allowsCapability(blockEntity.getBlockPos(), CapabilityPorts.CapabilityType.ITEM)) {
                        return null;
                    }
                    if (context == null) {
                        return VanillaContainerWrapper.of(blockEntity.getInternalItemHandler());
                    }
                    return new WorldlyContainerWrapper(blockEntity.getInternalItemHandler(), context);
                });
        event.registerBlockEntity(
                Capabilities.Energy.BLOCK,
                type,
                (blockEntity, context) -> blockEntity.allowsCapability(
                        blockEntity.getBlockPos(), CapabilityPorts.CapabilityType.ENERGY)
                                ? blockEntity.getEnergyHandler(context)
                                : null);
        event.registerBlockEntity(
                Capabilities.Fluid.BLOCK,
                type,
                (blockEntity, context) -> blockEntity.allowsCapability(
                        blockEntity.getBlockPos(), CapabilityPorts.CapabilityType.FLUID)
                                ? blockEntity.getFluidHandler(blockEntity.getBlockPos(), context)
                                : null);
    }


    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() || event.getEntity().isSecondaryUseActive()) {
            return;
        }
        Player player = event.getEntity();
        ItemStack held = event.getItemStack();
        if (held.isEmpty()) {
            return;
        }
        if (fluidItemAccess(player, event.getHand())
                .getCapability(Capabilities.Fluid.ITEM) == null) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        MultiblockSavedData data = MultiblockSavedData.getIfPresent(level);
        BlockPos controllerPos = data == null ? null : data.getController(event.getPos());
        if (controllerPos == null
                || !(level.getBlockEntity(controllerPos) instanceof MultiblockControllerBlockEntity controller)
                || !controller.isFormed()) {
            return;
        }

        boolean fluidPort = controller.allowsCapability(
                event.getPos(), CapabilityPorts.CapabilityType.FLUID);
        if (!fluidPort) {
            if (controller.definition() != null && controller.definition().capabilityPorts()
                    .isDefined(CapabilityPorts.CapabilityType.FLUID)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
            return;
        }

        ResourceHandler<FluidResource> handler = controller.getManualFluidHandler(event.getPos(), event.getFace());
        if (handler == null) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        FluidInteraction interaction;
        try (Transaction transaction = Transaction.open(null)) {
            interaction = moveFluid(player, event.getHand(), handler, transaction);
            if (interaction != null) {
                transaction.commit();
            }
        }
        if (interaction != null) {
            FluidUtil.triggerSoundAndGameEvent(
                    interaction.resource(),
                    level,
                    Vec3.atCenterOf(event.getPos()),
                    player,
                    interaction.pickup());
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    private static ItemAccess fluidItemAccess(Player player, InteractionHand hand) {
        int slot = hand == InteractionHand.MAIN_HAND
                ? player.getInventory().getSelectedSlot()
                : Inventory.SLOT_OFFHAND;
        return ItemAccess.forPlayerSlot(player, slot).oneByOne();
    }

    private static @Nullable FluidInteraction moveFluid(
            Player player,
            InteractionHand hand,
            ResourceHandler<FluidResource> handler,
            Transaction transaction) {
        ResourceHandler<FluidResource> itemFluids = fluidItemAccess(player, hand)
                .getCapability(Capabilities.Fluid.ITEM);
        if (itemFluids == null) {
            return null;
        }
        ResourceStack<FluidResource> moved = ResourceHandlerUtil.moveFirst(
                handler, itemFluids, resource -> true, Integer.MAX_VALUE, transaction);
        boolean pickup = moved != null;
        if (moved == null) {
            moved = ResourceHandlerUtil.moveFirst(
                    itemFluids, handler, resource -> true, Integer.MAX_VALUE, transaction);
        }
        return moved == null ? null : new FluidInteraction(moved.resource(), pickup);
    }

    private record FluidInteraction(FluidResource resource, boolean pickup) {
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
    }
}
