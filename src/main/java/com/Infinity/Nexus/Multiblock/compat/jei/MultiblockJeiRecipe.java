package com.Infinity.Nexus.Multiblock.compat.jei;

import com.Infinity.Nexus.Multiblock.api.MultiblockDefinition;
import com.Infinity.Nexus.Multiblock.recipe.MultiblockRecipe;
import net.minecraft.resources.Identifier;

public record MultiblockJeiRecipe(MultiblockDefinition definition, MultiblockRecipe recipe, Identifier id) {
}
