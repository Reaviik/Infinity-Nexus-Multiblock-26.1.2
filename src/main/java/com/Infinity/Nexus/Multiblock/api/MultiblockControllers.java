package com.Infinity.Nexus.Multiblock.api;

import com.Infinity.Nexus.Multiblock.block.MultiblockControllerBlockEntity;
import com.Infinity.Nexus.Multiblock.data.MultiblockSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public final class MultiblockControllers {
    private MultiblockControllers() {
    }

    public static @Nullable MultiblockControllerBlockEntity getFormedController(Level level, BlockPos pos) {
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
}
