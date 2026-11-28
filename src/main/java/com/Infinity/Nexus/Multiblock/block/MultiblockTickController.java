package com.Infinity.Nexus.Multiblock.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

final class MultiblockTickController {
    private final MultiblockControllerBlockEntity owner;

    MultiblockTickController(MultiblockControllerBlockEntity owner) {
        this.owner = owner;
    }

    void serverTick() {
        if (this.owner.getLevel() == null || this.owner.getLevel().isClientSide()) {
            return;
        }
        this.owner.ensureConfigured();
        if (this.owner.capabilitySyncPending && this.owner.getLevel().getGameTime() % 5L == 0L) {
            this.owner.capabilitySyncPending = false;
            BlockState state = this.owner.getBlockState();
            this.owner.getLevel().sendBlockUpdated(
                    this.owner.getBlockPos(), state, state, Block.UPDATE_CLIENTS);
        }
        if (!this.owner.isAssembling()
                && (!this.owner.structureValidated || this.owner.getLevel().getGameTime() % 20L == 0L)) {
            this.owner.validateStructure();
        }
        this.owner.recipe().tick();
        if (this.owner.serverLevel() != null) {
            this.owner.motions().tickStorage(this.owner.serverLevel(), this.owner.definition());
        }
        this.owner.assembly().tick();
        this.updateBuildingState();
        this.updateProcessingState();
    }

    void clientTick() {
        if (this.owner.getLevel() == null || !this.owner.getLevel().isClientSide()) {
            return;
        }
        this.owner.ensureConfigured();
    }

    void updateBuildingState() {
        if (this.owner.getLevel() == null || this.owner.getLevel().isClientSide()) {
            return;
        }
        boolean building = this.owner.isAssembling();
        BlockState state = this.owner.getBlockState();
        if (!state.hasProperty(MultiblockControllerBlock.BUILDING)
                || state.getValue(MultiblockControllerBlock.BUILDING) == building) {
            return;
        }
        BlockState updated = state.setValue(MultiblockControllerBlock.BUILDING, building);
        this.owner.getLevel().setBlock(
                this.owner.getBlockPos(), updated, Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
        this.owner.getLevel().sendBlockUpdated(
                this.owner.getBlockPos(), state, updated, Block.UPDATE_CLIENTS);
    }

    void updateProcessingState() {
        if (this.owner.getLevel() == null || this.owner.getLevel().isClientSide()) {
            return;
        }
        boolean processing = this.owner.recipe().isProcessing();
        BlockState state = this.owner.getBlockState();
        if (state.getValue(MultiblockControllerBlock.PROCESSING) == processing) {
            return;
        }
        BlockState updated = state.setValue(MultiblockControllerBlock.PROCESSING, processing);
        this.owner.getLevel().setBlock(
                this.owner.getBlockPos(), updated, Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
        this.owner.getLevel().sendBlockUpdated(
                this.owner.getBlockPos(), state, updated, Block.UPDATE_CLIENTS);
    }
}
