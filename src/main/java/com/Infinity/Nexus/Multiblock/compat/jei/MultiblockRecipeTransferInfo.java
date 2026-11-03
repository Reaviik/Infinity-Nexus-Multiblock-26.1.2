package com.Infinity.Nexus.Multiblock.compat.jei;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.Infinity.Nexus.Multiblock.api.ItemSlotRole;
import com.Infinity.Nexus.Multiblock.api.MultiblockDefinition;
import com.Infinity.Nexus.Multiblock.api.MultiblockMenuLayout;
import com.Infinity.Nexus.Multiblock.api.MultiblockMenuLayout.SlotPlacement;
import com.Infinity.Nexus.Multiblock.menu.MultiblockMenu;
import com.Infinity.Nexus.Multiblock.registry.ModMenus;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

public final class MultiblockRecipeTransferInfo
        implements IRecipeTransferInfo<MultiblockMenu, MultiblockJeiRecipe> {
    private final IRecipeTransferHandlerHelper helper;
    private final IRecipeType<MultiblockJeiRecipe> recipeType;

    public MultiblockRecipeTransferInfo(
            IRecipeTransferHandlerHelper helper,
            IRecipeType<MultiblockJeiRecipe> recipeType) {
        this.helper = helper;
        this.recipeType = recipeType;
    }

    @Override
    public Class<? extends MultiblockMenu> getContainerClass() {
        return MultiblockMenu.class;
    }

    @Override
    public Optional<MenuType<MultiblockMenu>> getMenuType() {
        return Optional.of(ModMenus.CONTROLLER_MENU.get());
    }

    @Override
    public IRecipeType<MultiblockJeiRecipe> getRecipeType() {
        return this.recipeType;
    }

    @Override
    public boolean canHandle(MultiblockMenu container, MultiblockJeiRecipe recipe) {
        MultiblockDefinition definition = container.blockEntity().definition();
        if (definition == null || !definition.id().equals(recipe.definition().id())) {
            return false;
        }
        return matchSlots(container, recipe).size() == recipe.recipe().ingredients().size();
    }

    @Override
    public IRecipeTransferError getHandlingError(MultiblockMenu container, MultiblockJeiRecipe recipe) {
        MultiblockDefinition definition = container.blockEntity().definition();
        if (definition == null || !definition.id().equals(recipe.definition().id())) {
            return null;
        }
        return this.helper.createUserErrorWithTooltip(
                Component.translatable("gui.infinity_nexus_multiblock.jei.transfer.no_slots"));
    }

    @Override
    public List<Slot> getRecipeSlots(MultiblockMenu container, MultiblockJeiRecipe recipe) {
        return matchSlots(container, recipe);
    }

    @Override
    public List<Slot> getInventorySlots(MultiblockMenu container, MultiblockJeiRecipe recipe) {
        int start = container.layout().slots().size();
        List<Slot> slots = new ArrayList<>(36);
        for (int index = 0; index < 36; index++) {
            slots.add(container.getSlot(start + index));
        }
        return slots;
    }

    private static List<Slot> matchSlots(MultiblockMenu container, MultiblockJeiRecipe recipe) {
        List<Slot> available = inputSlots(container);
        List<Ingredient> ingredients = recipe.recipe().ingredients();
        if (ingredients.size() > available.size()) {
            return List.of();
        }

        int[] assignment = new int[ingredients.size()];
        Arrays.fill(assignment, -1);
        boolean[] used = new boolean[available.size()];
        if (!assign(0, ingredients, available, assignment, used)) {
            return List.of();
        }

        List<Slot> matched = new ArrayList<>(ingredients.size());
        for (int slotIndex : assignment) {
            matched.add(available.get(slotIndex));
        }
        return matched;
    }

    private static boolean assign(
            int ingredientIndex,
            List<Ingredient> ingredients,
            List<Slot> available,
            int[] assignment,
            boolean[] used) {
        if (ingredientIndex >= ingredients.size()) {
            return true;
        }

        Ingredient ingredient = ingredients.get(ingredientIndex);
        for (int slotIndex = 0; slotIndex < available.size(); slotIndex++) {
            if (used[slotIndex] || !accepts(available.get(slotIndex), ingredient)) {
                continue;
            }
            used[slotIndex] = true;
            assignment[ingredientIndex] = slotIndex;
            if (assign(ingredientIndex + 1, ingredients, available, assignment, used)) {
                return true;
            }
            used[slotIndex] = false;
            assignment[ingredientIndex] = -1;
        }
        return false;
    }

    private static List<Slot> inputSlots(MultiblockMenu container) {
        MultiblockMenuLayout layout = container.layout();
        List<Slot> slots = new ArrayList<>();
        int menuSlot = 0;
        for (SlotPlacement placement : layout.slots()) {
            if (placement.role() == ItemSlotRole.INPUT) {
                slots.add(container.getSlot(menuSlot));
            }
            menuSlot++;
        }
        return slots;
    }

    @SuppressWarnings("deprecation")
    private static boolean accepts(Slot slot, Ingredient ingredient) {
        if (!ingredient.isCustom()) {
            return ingredient.getValues().stream()
                    .anyMatch(item -> slot.mayPlace(new ItemStack(item.value())));
        }
        return ingredient.items()
                .anyMatch(item -> slot.mayPlace(new ItemStack(item.value())));
    }
}
