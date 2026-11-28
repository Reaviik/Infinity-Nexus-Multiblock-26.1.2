package com.Infinity.Nexus.Multiblock.block;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.Infinity.Nexus.Multiblock.InfinityNexusMultiblock;
import com.Infinity.Nexus.Multiblock.api.MultiblockDefinition;
import com.Infinity.Nexus.Multiblock.data.MultiblockSavedData;
import com.Infinity.Nexus.Multiblock.data.MultiblockStructureExporter;
import com.Infinity.Nexus.Multiblock.network.MultiblockExportPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

final class MultiblockStructureController {
    private final MultiblockControllerBlockEntity owner;

    MultiblockStructureController(MultiblockControllerBlockEntity owner) {
        this.owner = owner;
    }

    void onNeighborChanged() {
        if (this.owner.getLevel() == null || this.owner.getLevel().isClientSide() || this.owner.isAssembling()) {
            return;
        }
        long gameTime = this.owner.getLevel().getGameTime();
        if (gameTime == this.owner.lastValidationTick) {
            return;
        }
        this.owner.lastValidationTick = gameTime;
        this.owner.validateStructure();
    }

    void validate() {
        ServerLevel serverLevel = this.owner.serverLevel();
        if (serverLevel == null) {
            return;
        }
        this.owner.ensureConfigured();
        MultiblockDefinition definition = this.owner.definition();
        if (definition == null) {
            this.owner.invalidateStructure();
            this.owner.structureValidated = true;
            return;
        }

        Rotation newRotation = this.rotationFromState();
        boolean matched = definition.matches(
                serverLevel,
                this.owner.getBlockPos(),
                newRotation,
                this.owner.getBlockState().getBlock(),
                this.owner.motionKeysFor(definition));
        List<BlockPos> newMembers = matched
                ? definition.members(this.owner.getBlockPos(), newRotation)
                : List.of();
        List<BlockPos> previousMembers = this.owner.members;
        boolean membersChanged = !previousMembers.equals(newMembers);

        if (matched) {
            MultiblockSavedData.Registration registration = MultiblockSavedData.get(serverLevel)
                    .register(this.owner.getBlockPos(), newMembers, definition.priority());
            if (!registration.accepted()) {
                matched = false;
                newMembers = List.of();
            } else {
                this.invalidateEvictedControllers(serverLevel, registration.evicted());
            }
        }

        if (!matched) {
            MultiblockSavedData data = MultiblockSavedData.getIfPresent(serverLevel);
            if (data != null) {
                data.unregisterController(this.owner.getBlockPos());
            }
        }

        BlockPos newOrigin = definition.origin(this.owner.getBlockPos(), newRotation);
        boolean changed = this.owner.formed != matched;
        boolean dataChanged = changed
                || this.owner.rotation != newRotation
                || !this.owner.origin.equals(newOrigin)
                || !this.owner.members.equals(newMembers);
        this.owner.formed = matched;
        this.owner.rotation = newRotation;
        this.owner.origin = newOrigin;
        this.owner.members = newMembers;

        int[] previousUpgradeCounts = this.owner.upgradeCounts();
        this.owner.refreshUpgrades(matched ? definition : null);
        boolean upgradesChanged = !java.util.Arrays.equals(previousUpgradeCounts, this.owner.upgradeCounts());
        if (upgradesChanged) {
            this.owner.resetRecipe();
            dataChanged = true;
        }

        MultiblockCapabilityController.Snapshot previousCapabilities =
                this.owner.capabilities().snapshot();
        this.owner.capabilities().update(
                serverLevel,
                definition,
                newOrigin,
                newRotation,
                matched,
                this.owner.resourceStorages);
        if (this.owner.capabilities().changedFrom(previousCapabilities)) {
            Set<BlockPos> invalidated = new HashSet<>();
            invalidated.addAll(previousCapabilities.items());
            invalidated.addAll(previousCapabilities.energy());
            invalidated.addAll(previousCapabilities.fluids());
            invalidated.addAll(this.owner.capabilities().itemPorts());
            invalidated.addAll(this.owner.capabilities().energyPorts());
            invalidated.addAll(this.owner.capabilities().fluidPorts());
            invalidated.addAll(previousCapabilities.fluidTanks().keySet());
            invalidated.addAll(this.owner.capabilities().fluidPortTanks().keySet());
            previousCapabilities.resources().values().forEach(invalidated::addAll);
            this.owner.capabilities().resourcePorts().values().forEach(invalidated::addAll);
            previousCapabilities.resourceTanks().values().forEach(map -> invalidated.addAll(map.keySet()));
            this.owner.capabilities().resourcePortTanks().values().forEach(map -> invalidated.addAll(map.keySet()));
            this.owner.invalidateCapabilities(List.copyOf(invalidated));
        }

        BlockState current = this.owner.getBlockState();
        boolean stateChanged = current.getValue(MultiblockControllerBlock.FORMED) != matched;
        if (stateChanged) {
            BlockState updated = current.setValue(MultiblockControllerBlock.FORMED, matched);
            serverLevel.setBlock(this.owner.getBlockPos(), updated, Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
            serverLevel.sendBlockUpdated(this.owner.getBlockPos(), current, updated, Block.UPDATE_CLIENTS);
        } else if (dataChanged) {
            serverLevel.sendBlockUpdated(this.owner.getBlockPos(), current, current, Block.UPDATE_CLIENTS);
        }

        if (dataChanged) {
            this.owner.setChanged();
        }
        if (membersChanged) {
            this.owner.invalidateCapabilities(previousMembers);
            this.owner.invalidateCapabilities(newMembers);
        }
        this.owner.structureValidated = true;
    }

    private void invalidateEvictedControllers(ServerLevel level, List<BlockPos> controllers) {
        for (BlockPos controllerPos : controllers) {
            if (this.owner.getBlockPos().equals(controllerPos)) {
                continue;
            }
            if (level.getBlockEntity(controllerPos) instanceof MultiblockControllerBlockEntity controller) {
                controller.invalidateStructure();
            }
        }
    }

    void export(Player player) {
        ServerLevel serverLevel = this.owner.serverLevel();
        if (serverLevel == null || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        try {
            MultiblockStructureExporter.Result result =
                    MultiblockStructureExporter.export(serverLevel, this.owner.getBlockPos());
            PacketDistributor.sendToPlayer(serverPlayer, new MultiblockExportPayload(result.json()));
            Component message = result.truncated()
                    ? Component.translatable(
                            "message.infinity_nexus_multiblock.exported_truncated",
                            result.scanned(),
                            result.limit())
                    : Component.translatable(
                            "message.infinity_nexus_multiblock.exported",
                            result.scanned());
            player.sendOverlayMessage(message);
            serverLevel.playSound(
                    null,
                    player.blockPosition(),
                    SoundEvents.NOTE_BLOCK_PLING.value(),
                    SoundSource.PLAYERS,
                    0.8F,
                    1.2F);
        } catch (RuntimeException exception) {
            InfinityNexusMultiblock.LOGGER.error(
                    "Failed to export multiblock structure at {}", this.owner.getBlockPos(), exception);
            player.sendOverlayMessage(Component.translatable("message.infinity_nexus_multiblock.export_failed"));
        }
    }

    void invalidate() {
        ServerLevel serverLevel = this.owner.serverLevel();
        if (serverLevel == null) {
            return;
        }

        List<BlockPos> previousMembers = new ArrayList<>(this.owner.members);
        MultiblockSavedData data = MultiblockSavedData.getIfPresent(serverLevel);
        if (data != null) {
            for (BlockPos member : data.getMembers(this.owner.getBlockPos())) {
                if (!previousMembers.contains(member)) {
                    previousMembers.add(member);
                }
            }
            data.unregisterController(this.owner.getBlockPos());
        }
        this.owner.members = List.of();
        this.owner.refreshUpgrades(null);
        this.owner.clearCapabilityPorts();
        this.owner.stopMotions();
        this.owner.resetRecipe();

        if (this.owner.formed) {
            this.owner.formed = false;
            BlockState current = this.owner.getBlockState();
            BlockState updated = current.setValue(MultiblockControllerBlock.FORMED, false);
            serverLevel.setBlock(this.owner.getBlockPos(), updated, Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
            serverLevel.sendBlockUpdated(this.owner.getBlockPos(), current, updated, Block.UPDATE_CLIENTS);
            this.owner.setChanged();
        }
        this.owner.updateProcessingState();
        if (!previousMembers.contains(this.owner.getBlockPos())) {
            previousMembers.add(this.owner.getBlockPos());
        }
        this.owner.invalidateCapabilities(previousMembers);
    }

    private Rotation rotationFromState() {
        return MultiblockControllerBlock.rotationFromState(this.owner.getBlockState());
    }
}
