package com.Infinity.Nexus.Multiblock.compat.jei;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.Infinity.Nexus.Multiblock.InfinityNexusMultiblock;
import com.Infinity.Nexus.Multiblock.api.GuiElement;
import com.Infinity.Nexus.Multiblock.api.GuiElementType;
import com.Infinity.Nexus.Multiblock.api.MultiblockDefinition;
import com.Infinity.Nexus.Multiblock.api.MultiblockType;
import com.Infinity.Nexus.Multiblock.client.ClientMultiblockEvents;
import com.Infinity.Nexus.Multiblock.client.MultiblockScreen;
import com.Infinity.Nexus.Multiblock.client.preview.MultiblockStructureData;
import com.Infinity.Nexus.Multiblock.data.MultiblockDefinitions;
import com.Infinity.Nexus.Multiblock.menu.MultiblockMenu;
import com.Infinity.Nexus.Multiblock.recipe.MultiblockRecipe;
import com.Infinity.Nexus.Multiblock.registry.ModBlocks;
import com.Infinity.Nexus.Multiblock.registry.ModDataComponents;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiClickableArea;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.advanced.IRecipeManagerPlugin;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IAdvancedRegistration;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.runtime.IRecipesGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.level.ItemLike;
import org.jspecify.annotations.Nullable;

@JeiPlugin
public final class MultiblockJeiPlugin implements IModPlugin {
    public static final IRecipeType<MultiblockJeiRecipe> RECIPE_TYPE = IRecipeType.create(
            Identifier.fromNamespaceAndPath(InfinityNexusMultiblock.MOD_ID, "multiblock_crafting"),
            MultiblockJeiRecipe.class);
    public static final IRecipeType<MultiblockStructureData> STRUCTURE_TYPE = IRecipeType.create(
            Identifier.fromNamespaceAndPath(InfinityNexusMultiblock.MOD_ID, "multiblock_structure"),
            MultiblockStructureData.class);

    private static @Nullable MultiblockJeiPlugin instance;

    private final Runnable definitionsListener = this::scheduleRefreshRuntime;
    private @Nullable IJeiRuntime runtime;
    private @Nullable MultiblockJeiCategory recipeCategory;
    private List<MultiblockJeiRecipe> registeredRecipes = List.of();
    private List<MultiblockStructureData> registeredStructures = List.of();
    private boolean listeningForDefinitions;

    public MultiblockJeiPlugin() {
        instance = this;
    }

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(InfinityNexusMultiblock.MOD_ID, "jei");
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        registration.registerSubtypeInterpreter(
                ModBlocks.CONTROLLER_ITEM.get(),
                (stack, context) -> stack.get(ModDataComponents.MULTIBLOCK_DEFINITION.get()));
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        ItemLike catalyst = ModBlocks.CONTROLLER_ITEM.get();
        MultiblockJeiCategory category = new MultiblockJeiCategory(guiHelper, RECIPE_TYPE, catalyst);
        this.recipeCategory = category;
        registration.addRecipeCategories(category);
        registration.addRecipeCategories(new MultiblockStructureCategory(guiHelper, STRUCTURE_TYPE, catalyst));
    }

    @Override
    public void registerAdvanced(IAdvancedRegistration registration) {
        registration.addRecipeManagerPlugin(new ControllerRecipeLookupPlugin());
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<MultiblockJeiRecipe> recipes = collectRecipes();
        if (!recipes.isEmpty()) {
            registration.addRecipes(RECIPE_TYPE, recipes);
        }
        this.registeredRecipes = recipes;

        List<MultiblockStructureData> structures = collectStructures();
        if (!structures.isEmpty()) {
            registration.addRecipes(STRUCTURE_TYPE, structures);
        }
        this.registeredStructures = structures;
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(STRUCTURE_TYPE, ModBlocks.CONTROLLER_ITEM.get());
        for (MultiblockDefinition definition : MultiblockDefinitions.all()) {
            registration.addCraftingStation(
                    RECIPE_TYPE,
                    ModBlocks.controllerWithDefinition(definition.id()));
        }
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        IRecipeTransferHandlerHelper helper = registration.getTransferHelper();
        MultiblockRecipeTransferInfo info = new MultiblockRecipeTransferInfo(helper, RECIPE_TYPE);
        registration.addRecipeTransferHandler(new MultiblockRecipeTransferHandler(helper, info), RECIPE_TYPE);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(MultiblockScreen.class, new IGuiContainerHandler<MultiblockScreen>() {
            @Override
            public Collection<IGuiClickableArea> getGuiClickableAreas(
                    MultiblockScreen screen, double mouseX, double mouseY) {
                MultiblockMenu menu = screen.getMenu();
                MultiblockDefinition definition = menu.blockEntity().definition();
                if (definition == null || definition.type() != MultiblockType.RECIPE) {
                    return List.of();
                }
                List<IGuiClickableArea> areas = new ArrayList<>();
                for (GuiElement element : menu.layout().elements()) {
                    if (element.type() == GuiElementType.ARROW
                            || element.type() == GuiElementType.PROGRESS_BAR) {
                        areas.add(clickableArea(element, definition.id()));
                    }
                }
                return areas;
            }
        });
    }

    private IGuiClickableArea clickableArea(GuiElement element, Identifier definitionId) {
        Rect2i area = new Rect2i(element.x(), element.y(), element.width(), element.height());
        return new IGuiClickableArea() {
            @Override
            public Rect2i getArea() {
                return area;
            }

            @Override
            public boolean isTooltipEnabled() {
                return false;
            }

            @Override
            public void onClick(IFocusFactory focusFactory, IRecipesGui recipesGui) {
                MultiblockJeiCategory category = MultiblockJeiPlugin.this.recipeCategory;
                List<MultiblockJeiRecipe> recipes = MultiblockJeiPlugin.this.recipesFor(definitionId);
                if (category != null && !recipes.isEmpty()) {
                    recipesGui.showRecipes(category, recipes, List.of());
                }
            }
        };
    }

    private List<MultiblockJeiRecipe> recipesFor(Identifier definitionId) {
        List<MultiblockJeiRecipe> registered = this.registeredRecipes.stream()
                .filter(recipe -> recipe.definition().id().equals(definitionId))
                .toList();
        if (!registered.isEmpty()) {
            return registered;
        }
        return collectRecipes().stream()
                .filter(recipe -> recipe.definition().id().equals(definitionId))
                .toList();
    }

    private final class ControllerRecipeLookupPlugin implements IRecipeManagerPlugin {
        @Override
        public <V> List<IRecipeType<?>> getRecipeTypes(IFocus<V> focus) {
            Identifier definitionId = controllerDefinition(focus);
            if (definitionId == null) {
                return List.of();
            }
            List<IRecipeType<?>> recipeTypes = new ArrayList<>(2);
            if (!recipesFor(definitionId).isEmpty()) {
                recipeTypes.add(RECIPE_TYPE);
            }
            if (!structuresFor(definitionId).isEmpty()) {
                recipeTypes.add(STRUCTURE_TYPE);
            }
            return List.copyOf(recipeTypes);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T, V> List<T> getRecipes(IRecipeType<T> recipeType, IFocus<V> focus) {
            Identifier definitionId = controllerDefinition(focus);
            if (definitionId == null) {
                return List.of();
            }
            if (RECIPE_TYPE.equals(recipeType)) {
                return (List<T>) recipesFor(definitionId);
            }
            if (STRUCTURE_TYPE.equals(recipeType)) {
                return (List<T>) structuresFor(definitionId);
            }
            return List.of();
        }

        @Override
        public <T> List<T> getRecipes(IRecipeType<T> recipeType) {
            return List.of();
        }
    }

    private List<MultiblockStructureData> structuresFor(Identifier definitionId) {
        List<MultiblockStructureData> registered = this.registeredStructures.stream()
                .filter(structure -> structure.definition().id().equals(definitionId))
                .toList();
        if (!registered.isEmpty()) {
            return registered;
        }
        return collectStructures().stream()
                .filter(structure -> structure.definition().id().equals(definitionId))
                .toList();
    }

    private static @Nullable Identifier controllerDefinition(IFocus<?> focus) {
        return focus.getTypedValue().getItemStack()
                .map(stack -> stack.get(ModDataComponents.MULTIBLOCK_DEFINITION.get()))
                .orElse(null);
    }

    private void scheduleRefreshRuntime() {
        Minecraft.getInstance().execute(this::refreshRuntime);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        this.runtime = jeiRuntime;
        if (!this.listeningForDefinitions) {
            MultiblockDefinitions.addListener(this.definitionsListener);
            this.listeningForDefinitions = true;
        }
        refreshRuntime();
    }

    @Override
    public void onRuntimeUnavailable() {
        if (this.listeningForDefinitions) {
            MultiblockDefinitions.removeListener(this.definitionsListener);
            this.listeningForDefinitions = false;
        }
        this.runtime = null;
        this.registeredRecipes = List.of();
        this.registeredStructures = List.of();
    }

    public static void onRecipesUpdated() {
        if (instance != null) {
            instance.refreshRuntime();
        }
    }

    private void refreshRuntime() {
        IJeiRuntime currentRuntime = this.runtime;
        if (currentRuntime == null) {
            return;
        }
        IRecipeManager manager = currentRuntime.getRecipeManager();
        List<MultiblockJeiRecipe> recipes = collectRecipes();
        List<MultiblockJeiRecipe> toHide = this.registeredRecipes.stream()
                .filter(recipe -> !recipes.contains(recipe))
                .toList();
        if (!toHide.isEmpty()) {
            manager.hideRecipes(RECIPE_TYPE, toHide);
        }
        List<MultiblockJeiRecipe> toAdd = recipes.stream()
                .filter(recipe -> !this.registeredRecipes.contains(recipe))
                .toList();
        if (!toAdd.isEmpty()) {
            manager.unhideRecipes(RECIPE_TYPE, toAdd);
            manager.addRecipes(RECIPE_TYPE, toAdd);
        }
        this.registeredRecipes = recipes;

        List<MultiblockStructureData> structures = collectStructures();
        List<MultiblockStructureData> structuresToHide = this.registeredStructures.stream()
                .filter(structure -> !structures.contains(structure))
                .toList();
        if (!structuresToHide.isEmpty()) {
            manager.hideRecipes(STRUCTURE_TYPE, structuresToHide);
        }
        List<MultiblockStructureData> structuresToAdd = structures.stream()
                .filter(structure -> !this.registeredStructures.contains(structure))
                .toList();
        if (!structuresToAdd.isEmpty()) {
            manager.unhideRecipes(STRUCTURE_TYPE, structuresToAdd);
            manager.addRecipes(STRUCTURE_TYPE, structuresToAdd);
        }
        this.registeredStructures = structures;
    }

    private static List<MultiblockStructureData> collectStructures() {
        List<MultiblockStructureData> structures = new ArrayList<>();
        for (MultiblockDefinition definition : MultiblockDefinitions.all()) {
            MultiblockStructureData structure = MultiblockStructureData.create(definition);
            if (!structure.controller().isEmpty()) {
                structures.add(structure);
            }
        }
        return List.copyOf(structures);
    }

    private static List<MultiblockJeiRecipe> collectRecipes() {
        RecipeMap recipeMap = ClientMultiblockEvents.recipes();
        if (recipeMap.values().isEmpty()) {
            return List.of();
        }
        Map<Identifier, List<MultiblockJeiRecipe>> recipes = new LinkedHashMap<>();
        for (RecipeHolder<?> holder : recipeMap.values()) {
            if (!(holder.value() instanceof MultiblockRecipe recipe)) {
                continue;
            }
            MultiblockDefinition definition = MultiblockDefinitions.get(recipe.definition());
            if (definition == null || definition.type() != MultiblockType.RECIPE) {
                continue;
            }
            recipes.computeIfAbsent(definition.id(), key -> new ArrayList<>())
                    .add(new MultiblockJeiRecipe(definition, recipe, holder.id().identifier()));
        }
        List<MultiblockJeiRecipe> result = new ArrayList<>();
        recipes.values().forEach(result::addAll);
        return List.copyOf(result);
    }
}
