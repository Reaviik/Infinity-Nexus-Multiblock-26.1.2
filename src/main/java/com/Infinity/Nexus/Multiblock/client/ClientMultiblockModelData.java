package com.Infinity.Nexus.Multiblock.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class ClientMultiblockModelData {
    private ClientMultiblockModelData() {}

    public static void requestRefresh(BlockEntity blockEntity) {
        blockEntity.requestModelDataUpdate();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != blockEntity.getLevel() || minecraft.levelRenderer == null) {
            return;
        }
        BlockPos pos = blockEntity.getBlockPos();
        minecraft.levelRenderer.setSectionDirtyWithNeighbors(
                pos.getX() >> 4,
                pos.getY() >> 4,
                pos.getZ() >> 4);
    }
}
