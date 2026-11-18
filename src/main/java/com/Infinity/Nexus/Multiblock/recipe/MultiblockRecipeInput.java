package com.Infinity.Nexus.Multiblock.recipe;

import java.util.List;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.neoforged.neoforge.fluids.FluidStack;

import com.Infinity.Nexus.Multiblock.api.addon.StoredResource;

public record MultiblockRecipeInput(
        List<ItemStack> items,
        List<FluidStack> fluids,
        List<StoredResource> resources) implements RecipeInput {
    public MultiblockRecipeInput {
        items = List.copyOf(items);
        fluids = List.copyOf(fluids);
        resources = List.copyOf(resources);
    }

    public MultiblockRecipeInput(List<ItemStack> items, List<FluidStack> fluids) {
        this(items, fluids, List.of());
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
        for (StoredResource resource : this.resources) {
            if (resource.amount() > 0L && !resource.id().isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
