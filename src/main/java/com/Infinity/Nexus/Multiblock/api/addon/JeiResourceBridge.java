package com.Infinity.Nexus.Multiblock.api.addon;

import com.Infinity.Nexus.Multiblock.recipe.MultiblockRecipeIngredient;
import com.Infinity.Nexus.Multiblock.recipe.MultiblockRecipeOutput;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;

/**
 * Optional JEI adapter supplied by an addon so the base category can display a
 * custom resource kind without importing the owning mod.
 */
public interface JeiResourceBridge {
    void addInput(IRecipeSlotBuilder slot, MultiblockRecipeIngredient.Resource input);

    void addOutput(IRecipeSlotBuilder slot, MultiblockRecipeOutput.Resource output);

    default void addInput(
            IRecipeSlotBuilder slot,
            MultiblockRecipeIngredient.Resource input,
            int width,
            int height) {
        addInput(slot, input);
    }

    default void addOutput(
            IRecipeSlotBuilder slot,
            MultiblockRecipeOutput.Resource output,
            int width,
            int height) {
        addOutput(slot, output);
    }

    void addInvisibleInput(IRecipeLayoutBuilder builder, MultiblockRecipeIngredient.Resource input);

    void addInvisibleOutput(IRecipeLayoutBuilder builder, MultiblockRecipeOutput.Resource output);
}
