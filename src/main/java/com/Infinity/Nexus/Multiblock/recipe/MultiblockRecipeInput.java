package com.Infinity.Nexus.Multiblock.recipe;

import java.util.List;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.neoforged.neoforge.fluids.FluidStack;

public record MultiblockRecipeInput(List<ItemStack> items, List<FluidStack> fluids) implements RecipeInput {
    public MultiblockRecipeInput {
        items = List.copyOf(items);
        fluids = List.copyOf(fluids);
    }

    @Override
    public ItemStack getItem(int index) {
        return this.items.get(index);
    }

    @Override
    public int size() {
        return this.items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack item : this.items) {
            if (!item.isEmpty()) {
                return false;
            }
        }
        for (FluidStack fluid : this.fluids) {
            if (!fluid.isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
