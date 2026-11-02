package com.Infinity.Nexus.Multiblock.compat.jei;

import java.util.List;
import java.util.Optional;

import com.Infinity.Nexus.Multiblock.menu.MultiblockMenu;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferContext;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;

public final class MultiblockRecipeTransferHandler
        implements IRecipeTransferHandler<MultiblockMenu, MultiblockJeiRecipe> {
    private final IRecipeTransferHandlerHelper helper;
    private final IRecipeTransferInfo<MultiblockMenu, MultiblockJeiRecipe> info;
    private final IRecipeTransferHandler<MultiblockMenu, MultiblockJeiRecipe> delegate;

    public MultiblockRecipeTransferHandler(
            IRecipeTransferHandlerHelper helper,
            IRecipeTransferInfo<MultiblockMenu, MultiblockJeiRecipe> info) {
        this.helper = helper;
        this.info = info;
        this.delegate = helper.createUnregisteredRecipeTransferHandler(info);
    }

    @Override
    public Class<? extends MultiblockMenu> getContainerClass() {
        return this.info.getContainerClass();
    }

    @Override
    public Optional<MenuType<MultiblockMenu>> getMenuType() {
        return this.info.getMenuType();
    }

    @Override
    public IRecipeType<MultiblockJeiRecipe> getRecipeType() {
        return this.info.getRecipeType();
    }

    @SuppressWarnings("removal")
    @Override
    public IRecipeTransferError transferRecipe(
            MultiblockMenu container,
            MultiblockJeiRecipe recipe,
            IRecipeSlotsView recipeSlots,
            Player player,
            boolean maxTransfer,
            boolean doTransfer) {
        List<IRecipeSlotView> inputs = itemInputs(recipeSlots);
        if (inputs.isEmpty()) {
            return this.helper.createUserErrorWithTooltip(
                    Component.translatable("gui.infinity_nexus_multiblock.jei.transfer.no_items"));
        }
        return this.delegate.transferRecipe(
                container, recipe, this.helper.createRecipeSlotsView(inputs),
                player, maxTransfer, doTransfer);
    }

    @Override
    public IRecipeTransferError transferRecipe(
            IRecipeTransferContext<MultiblockJeiRecipe, MultiblockMenu> context, boolean doTransfer) {
        List<IRecipeSlotView> inputs = itemInputs(context.getRecipeSlots());
        if (inputs.isEmpty()) {
            return this.helper.createUserErrorWithTooltip(
                    Component.translatable("gui.infinity_nexus_multiblock.jei.transfer.no_items"));
        }
        return this.helper.transferRecipeWithInputAlternatives(
                this.delegate, context, List.of(this.helper.createRecipeSlotsView(inputs)), doTransfer);
    }

    private static List<IRecipeSlotView> itemInputs(IRecipeSlotsView recipeSlots) {
        return recipeSlots.getSlotViews(RecipeIngredientRole.INPUT).stream()
                .filter(slot -> slot.getItemStacks().findAny().isPresent())
                .toList();
    }
}
