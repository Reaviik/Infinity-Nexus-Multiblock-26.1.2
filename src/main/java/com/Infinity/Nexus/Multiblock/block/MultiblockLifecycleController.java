package com.Infinity.Nexus.Multiblock.block;

import java.util.ArrayList;
import java.util.List;

import com.Infinity.Nexus.Multiblock.client.ClientMultiblockModelData;
import com.Infinity.Nexus.Multiblock.client.MultiblockClientCache;
import com.Infinity.Nexus.Multiblock.data.MultiblockSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

final class MultiblockLifecycleController {
    private final MultiblockControllerBlockEntity owner;

    MultiblockLifecycleController(MultiblockControllerBlockEntity owner) {
        this.owner = owner;
    }

    void setLevel(Level level) {
        this.owner.ensureConfigured();
        if (level.isClientSide()) {
            this.updateClientCache();
            ClientMultiblockModelData.requestRefresh(this.owner);
        }
    }

    void onLoad() {
        this.owner.ensureConfigured();
        if (this.owner.getLevel() != null && this.owner.getLevel().isClientSide()) {
            ClientMultiblockModelData.requestRefresh(this.owner);
        }
    }

    void setRemoved() {
        if (this.owner.getLevel() != null && this.owner.getLevel().isClientSide()) {
            MultiblockClientCache.removeController(this.owner.getLevel(), this.owner.getBlockPos());
        }
    }

    void preRemoveSideEffects() {
        this.owner.stopMotions();
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
        this.owner.invalidateCapabilities(previousMembers);
    }

    void afterLoad() {
        if (this.owner.getLevel() != null && this.owner.getLevel().isClientSide()) {
            this.updateClientCache();
            ClientMultiblockModelData.requestRefresh(this.owner);
        }
    }

    private void updateClientCache() {
        if (this.owner.getLevel() == null || !this.owner.getLevel().isClientSide()) {
            return;
        }
        if (this.owner.formed && !this.owner.members.isEmpty()) {
            MultiblockClientCache.track(this.owner.getLevel(), this.owner.getBlockPos(), this.owner.members);
        } else {
            MultiblockClientCache.removeController(this.owner.getLevel(), this.owner.getBlockPos());
        }
    }
}
