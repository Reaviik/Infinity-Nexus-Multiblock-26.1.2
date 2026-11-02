package com.Infinity.Nexus.Multiblock.registry;

import com.Infinity.Nexus.Multiblock.api.MultiblockControllerConfig;
import com.Infinity.Nexus.Multiblock.api.MultiblockControllerConfig.FormedTextures;
import com.Infinity.Nexus.Multiblock.api.MultiblockControllerRecipe;
import com.Infinity.Nexus.Multiblock.api.MultiblockType;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public record DynamicControllerSpec(
        Identifier definitionId,
        Identifier blockId,
        MultiblockType type,
        MultiblockControllerConfig.Textures textures,
        @Nullable FormedTextures formedTextures,
        @Nullable Identifier particle,
        int lightLevel,
        @Nullable MultiblockControllerRecipe recipe) {
}
