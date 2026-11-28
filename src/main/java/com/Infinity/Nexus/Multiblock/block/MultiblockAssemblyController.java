package com.Infinity.Nexus.Multiblock.block;

import com.Infinity.Nexus.Multiblock.api.MultiblockDefinition;
import com.Infinity.Nexus.Multiblock.config.Config;
import com.Infinity.Nexus.Multiblock.network.MultiblockBlockedHighlightPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

final class MultiblockAssemblyController {
    private final MultiblockControllerBlockEntity owner;
    private @Nullable MultiblockAssemblyJob job;

    MultiblockAssemblyController(MultiblockControllerBlockEntity owner) {
        this.owner = owner;
    }

    boolean isAssembling() {
        return this.job != null;
    }

    float progress() {
        return this.job == null ? 0.0F : this.job.progress();
    }

    void reset() {
        this.job = null;
    }

    boolean tryAssemble(Player player) {
        ServerLevel serverLevel = this.owner.serverLevel();
        if (serverLevel == null || this.owner.formed || player.isSpectator()) {
            return false;
        }
        if (this.job != null) {
            player.sendOverlayMessage(Component.translatable("message.infinity_nexus_multiblock.assembly_in_progress"));
            return false;
        }
        this.owner.ensureConfigured();
        MultiblockDefinition definition = this.owner.definition();
        if (definition == null) {
            return false;
        }

        Rotation rotation = MultiblockControllerBlock.rotationFromState(this.owner.getBlockState());
        this.job = new MultiblockAssemblyJob(
                definition,
                this.owner.getBlockPos(),
                rotation,
                player.getUUID(),
                player.getAbilities().instabuild);
        player.sendOverlayMessage(Component.translatable("message.infinity_nexus_multiblock.assembly_started"));
        this.owner.updateBuildingState();
        this.owner.setChanged();
        return true;
    }

    void tick() {
        MultiblockAssemblyJob activeJob = this.job;
        if (activeJob == null) {
            return;
        }
        ServerLevel serverLevel = this.owner.serverLevel();
        if (serverLevel == null) {
            return;
        }
        ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(activeJob.playerId());
        MultiblockAssemblyJob.TickResult result = activeJob.tick(
                serverLevel,
                player,
                Config.ASSEMBLY_BLOCKS_PER_TICK.get(),
                Config.ASSEMBLY_MAX_FAILED_SCANS_PER_TICK.get());
        if (result == MultiblockAssemblyJob.TickResult.RUNNING) {
            return;
        }
        this.job = null;
        this.owner.validateStructure();
        if (player != null) {
            this.sendAssemblyResult(player, activeJob);
            this.sendBlockedHighlight(serverLevel, player, activeJob);
        }
    }

    private void sendBlockedHighlight(ServerLevel level, ServerPlayer player, MultiblockAssemblyJob activeJob) {
        PacketDistributor.sendToPlayer(player, new MultiblockBlockedHighlightPayload(
                level.dimension().identifier(),
                this.owner.getBlockPos(),
                activeJob.blockedPositions(),
                Config.ASSEMBLY_BLOCKED_HIGHLIGHT_SECONDS.get() * 1000L));
    }

    private void sendAssemblyResult(ServerPlayer player, MultiblockAssemblyJob activeJob) {
        if (this.owner.formed) {
            player.sendOverlayMessage(Component.translatable("message.infinity_nexus_multiblock.assembled"));
            return;
        }
        int remaining = activeJob.pendingCount() + activeJob.blocked() + activeJob.failed();
        if (activeJob.placed() > 0) {
            player.sendOverlayMessage(Component.translatable(
                    "message.infinity_nexus_multiblock.partially_assembled", activeJob.placed(), remaining));
            return;
        }
        if (activeJob.pendingCount() > 0) {
            player.sendOverlayMessage(Component.translatable(
                    "message.infinity_nexus_multiblock.missing_blocks", activeJob.pendingCount()));
            return;
        }
        if (activeJob.blocked() > 0) {
            player.sendOverlayMessage(Component.translatable(
                    "message.infinity_nexus_multiblock.blocked_cells_highlighted",
                    activeJob.blocked() + activeJob.failed()));
            return;
        }
        if (activeJob.failed() > 0) {
            player.sendOverlayMessage(Component.translatable(
                    "message.infinity_nexus_multiblock.blocked_cells", activeJob.failed()));
            return;
        }
        player.sendOverlayMessage(Component.translatable("message.infinity_nexus_multiblock.assembly_failed"));
    }
}
