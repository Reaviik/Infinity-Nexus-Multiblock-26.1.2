package com.Infinity.Nexus.Multiblock.api.addon;

import java.util.List;

import com.Infinity.Nexus.Multiblock.api.MultiblockDefinition;
import com.Infinity.Nexus.Multiblock.recipe.MultiblockRecipeIngredient;
import com.Infinity.Nexus.Multiblock.recipe.MultiblockRecipeOutput;
import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.chat.Component;

/**
 * Describes one addon-provided resource kind.
 *
 * <p>Implementations must be registered through {@link MultiblockAddons} during mod
 * construction, before multiblock definitions are loaded.</p>
 */
public interface MultiblockResourceType {
    String id();

    MultiblockResourceStorage createStorage(MultiblockDefinition definition, JsonElement config, Runnable onChanged);

    List<ResourceTankInfo> describeTanks(JsonElement config);

    MapCodec<? extends MultiblockRecipeIngredient> ingredientCodec();

    MapCodec<? extends MultiblockRecipeOutput> outputCodec();

    DataResult<String> validateResourceId(String resourceId);

    Component displayName(String resourceId);

    int color(String resourceId);
}
