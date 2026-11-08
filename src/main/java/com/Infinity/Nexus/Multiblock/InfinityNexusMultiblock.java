package com.Infinity.Nexus.Multiblock;

import com.Infinity.Nexus.Multiblock.api.CapabilityPorts;
import com.Infinity.Nexus.Multiblock.block.MultiblockControllerBlockEntity;
import com.Infinity.Nexus.Multiblock.command.MultiblockCommands;
import com.Infinity.Nexus.Multiblock.config.Config;
import com.Infinity.Nexus.Multiblock.data.MultiblockReloadListeners;
import com.Infinity.Nexus.Multiblock.data.MultiblockSavedData;
import com.Infinity.Nexus.Multiblock.gametest.MultiblockGameTests;
import com.Infinity.Nexus.Multiblock.network.MultiblockDefinitionSyncPayload;
import com.Infinity.Nexus.Multiblock.network.MultiblockExportPayload;
import com.Infinity.Nexus.Multiblock.network.SetFluidSlotTankPayload;
import com.Infinity.Nexus.Multiblock.network.SetSlotLockPayload;
import com.Infinity.Nexus.Multiblock.registry.ModBlocks;
import com.Infinity.Nexus.Multiblock.registry.ModDataComponents;
import com.Infinity.Nexus.Multiblock.registry.ModMenus;
import com.Infinity.Nexus.Multiblock.registry.ModRecipes;
import com.Infinity.Nexus.Multiblock.resource.ControllerRecipePack;
import com.Infinity.Nexus.Multiblock.tab.ModCreativeTab;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;

@Mod(InfinityNexusMultiblock.MOD_ID)
public class InfinityNexusMultiblock {
    public static final String MOD_ID = "infinity_nexus_multiblock";
    public static final Logger LOGGER = LogUtils.getLogger();
    public InfinityNexusMultiblock(IEventBus modEventBus, ModContainer modContainer) {
        ModDataComponents.register(modEventBus);
        ModBlocks.register(modEventBus);
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
        event.registrar("1").playToServer(
                SetSlotLockPayload.TYPE,
                SetSlotLockPayload.CODEC,
                SetSlotLockPayload::handle);
        event.registrar("1").playToServer(
                SetFluidSlotTankPayload.TYPE,
                SetFluidSlotTankPayload.CODEC,
                SetFluidSlotTankPayload::handle);
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


    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
    }
}
