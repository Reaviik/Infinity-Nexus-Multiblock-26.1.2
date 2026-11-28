package com.Infinity.Nexus.Multiblock.block;

import java.util.Objects;

import com.Infinity.Nexus.Multiblock.api.MultiblockDefinition;
import com.Infinity.Nexus.Multiblock.client.ClientMultiblockModelData;
import com.Infinity.Nexus.Multiblock.data.MultiblockDefinitions;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

final class MultiblockDefinitionController {
    private final MultiblockControllerBlockEntity owner;

    MultiblockDefinitionController(MultiblockControllerBlockEntity owner) {
        this.owner = owner;
    }

    void configure(@Nullable MultiblockDefinition definition) {
        if (definition == null) {
            this.invalidateCapabilityPorts();
            this.owner.clearCapabilityPorts();
            this.owner.resourceStorages.clear();
            this.owner.pendingResources.clear();
            this.owner.configuredDefinition = null;
            this.owner.upgrades().reset();
            this.owner.structureValidated = false;
            return;
        }

        boolean changed = this.owner.configuredDefinition != definition;
        if (changed) {
            this.invalidateCapabilityPorts();
            this.owner.clearCapabilityPorts();
            this.owner.upgrades().reset();
            this.owner.structureValidated = false;
        }
        this.owner.configuredDefinition = definition;
        this.owner.slots().configure(definition, changed);
        this.owner.storage().configure(definition, changed);
        this.owner.slots().sanitizeFluidSlots();
        this.owner.storage().applyPending();
    }

    void ensureConfigured() {
        MultiblockDefinition definition = this.definition();
        if (definition != null && definition != this.owner.configuredDefinition) {
            this.configure(definition);
        }
        this.owner.storage().applyPending();
    }

    @Nullable MultiblockDefinition definition() {
        return this.owner.definitionId == null ? null : MultiblockDefinitions.get(this.owner.definitionId);
    }

    @Nullable Identifier definitionId() {
        return this.owner.definitionId;
    }

    void setDefinitionId(Identifier definitionId) {
        if (Objects.equals(this.owner.definitionId, definitionId)) {
            return;
        }
        this.owner.stopMotions();
        this.owner.definitionId = definitionId;
        this.owner.assembly().reset();
        MultiblockDefinition definition = this.definition();
        if (definition != null) {
            this.configure(definition);
        }
        this.owner.setChanged();
        if (this.owner.getLevel() != null && !this.owner.getLevel().isClientSide()) {
            BlockState state = this.owner.getBlockState();
            this.owner.getLevel().sendBlockUpdated(
                    this.owner.getBlockPos(), state, state, Block.UPDATE_CLIENTS);
        } else if (this.owner.getLevel() != null) {
            ClientMultiblockModelData.requestRefresh(this.owner);
        }
    }

    private void invalidateCapabilityPorts() {
        if (this.owner.getLevel() != null) {
            this.owner.capabilities().invalidate(this.owner.getLevel(), this.owner.getBlockPos());
        }
    }
}
