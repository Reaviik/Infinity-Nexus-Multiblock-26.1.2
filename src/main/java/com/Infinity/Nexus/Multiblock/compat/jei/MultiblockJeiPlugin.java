package com.Infinity.Nexus.Multiblock.compat.jei;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
import com.Infinity.Nexus.Multiblock.registry.DynamicControllerSpec;
import com.Infinity.Nexus.Multiblock.registry.ModBlocks;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiClickableArea;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.runtime.IRecipesGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.registries.DeferredItem;
import org.jspecify.annotations.Nullable;

@JeiPlugin
public final class MultiblockJeiPlugin implements IModPlugin {
    private static @Nullable MultiblockJeiPlugin instance;

    private final Runnable definitionsListener = this::scheduleRefreshRuntime;
    private final Map<Identifier, IRecipeType<MultiblockJeiRecipe>> recipeTypes = new LinkedHashMap<>();
    private final Map<Identifier, IRecipeType<MultiblockStructureData>> structureTypes = new LinkedHashMap<>();
    private final Map<Identifier, ItemLike> catalysts = new LinkedHashMap<>();
    private final Map<Identifier, ItemLike> structureCatalysts = new LinkedHashMap<>();
    private @Nullable IJeiRuntime runtime;
    private Map<Identifier, List<MultiblockJeiRecipe>> registeredRecipes = Map.of();
    private Map<Identifier, MultiblockStructureData> registeredStructures = Map.of();
    private boolean listeningForDefinitions;

    public MultiblockJeiPlugin() {
        instance = this;
    }

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(InfinityNexusMultiblock.MOD_ID, "jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        this.recipeTypes.clear();
        this.structureTypes.clear();
        this.catalysts.clear();
        this.structureCatalysts.clear();
        for (DynamicControllerSpec spec : ModBlocks.DYNAMIC_CONTROLLER_SPECS) {
            DeferredItem<BlockItem> item = ModBlocks.DYNAMIC_CONTROLLER_ITEMS.get(spec.blockId());
            if (item == null) {
                continue;
            }
            Identifier definitionId = spec.definitionId();
            if (spec.type() == MultiblockType.RECIPE) {
                IRecipeType<MultiblockJeiRecipe> recipeType = recipeType(definitionId);
                this.recipeTypes.put(definitionId, recipeType);
                this.catalysts.put(definitionId, item.get());
                registration.addRecipeCategories(new MultiblockJeiCategory(
                        guiHelper, recipeType, definitionId, item.get()));
            }
            IRecipeType<MultiblockStructureData> structureType = structureType(definitionId);
            this.structureTypes.put(definitionId, structureType);
            this.structureCatalysts.put(definitionId, item.get());
            registration.addRecipeCategories(new MultiblockStructureCategory(
                    guiHelper, structureType, definitionId, item.get()));
        }
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        Map<Identifier, List<MultiblockJeiRecipe>> recipes = collectRecipes();
        for (Map.Entry<Identifier, IRecipeType<MultiblockJeiRecipe>> entry : this.recipeTypes.entrySet()) {
            List<MultiblockJeiRecipe> controllerRecipes = recipes.getOrDefault(entry.getKey(), List.of());
            if (!controllerRecipes.isEmpty()) {
                registration.addRecipes(entry.getValue(), controllerRecipes);
            }
        }
        this.registeredRecipes = recipes;
        Map<Identifier, MultiblockStructureData> structures = collectStructures();
        for (Map.Entry<Identifier, IRecipeType<MultiblockStructureData>> entry : this.structureTypes.entrySet()) {
            MultiblockStructureData structure = structures.get(entry.getKey());
            if (structure != null) {
                registration.addRecipes(entry.getValue(), List.of(structure));
            }
        }
        this.registeredStructures = structures;
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (Map.Entry<Identifier, IRecipeType<MultiblockJeiRecipe>> entry : this.recipeTypes.entrySet()) {
            ItemLike catalyst = this.catalysts.get(entry.getKey());
            if (catalyst != null) {
                registration.addCraftingStation(entry.getValue(), catalyst);
            }
        }
        for (Map.Entry<Identifier, IRecipeType<MultiblockStructureData>> entry : this.structureTypes.entrySet()) {
            ItemLike catalyst = this.structureCatalysts.get(entry.getKey());
            if (catalyst != null) {
                registration.addCraftingStation(entry.getValue(), catalyst);
            }
        }
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        IRecipeTransferHandlerHelper helper = registration.getTransferHelper();
        for (Map.Entry<Identifier, IRecipeType<MultiblockJeiRecipe>> entry : this.recipeTypes.entrySet()) {
            MultiblockRecipeTransferInfo info = new MultiblockRecipeTransferInfo(
                    helper, entry.getKey(), entry.getValue());
            registration.addRecipeTransferHandler(
                    new MultiblockRecipeTransferHandler(helper, info), entry.getValue());
        }
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
                IRecipeType<MultiblockJeiRecipe> recipeType =
                        MultiblockJeiPlugin.this.recipeTypes.get(definition.id());
                if (recipeType == null) {
                    return List.of();
                }
                List<IGuiClickableArea> areas = new ArrayList<>();
                for (GuiElement element : menu.layout().elements()) {
                    if (element.type() == GuiElementType.ARROW
                            || element.type() == GuiElementType.PROGRESS_BAR) {
                        areas.add(clickableArea(element, recipeType));
                    }
                }
                return areas;
            }
        });
    }

    private static IGuiClickableArea clickableArea(GuiElement element, IRecipeType<?> recipeType) {
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
                recipesGui.showTypes(List.<IRecipeType<?>>of(recipeType));
            }
        };
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
        this.registeredRecipes = Map.of();
        this.registeredStructures = Map.of();
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
        Map<Identifier, List<MultiblockJeiRecipe>> recipes = collectRecipes();
        for (Map.Entry<Identifier, IRecipeType<MultiblockJeiRecipe>> entry : this.recipeTypes.entrySet()) {
            Identifier definitionId = entry.getKey();
            IRecipeType<MultiblockJeiRecipe> recipeType = entry.getValue();
            List<MultiblockJeiRecipe> oldRecipes =
                    this.registeredRecipes.getOrDefault(definitionId, List.of());
            List<MultiblockJeiRecipe> newRecipes =
                    recipes.getOrDefault(definitionId, List.of());
            List<MultiblockJeiRecipe> toHide = oldRecipes.stream()
                    .filter(recipe -> !newRecipes.contains(recipe))
                    .toList();
            if (!toHide.isEmpty()) {
                manager.hideRecipes(recipeType, toHide);
            }
            List<MultiblockJeiRecipe> toAdd = newRecipes.stream()
                    .filter(recipe -> !oldRecipes.contains(recipe))
                    .toList();
            if (!toAdd.isEmpty()) {
                manager.unhideRecipes(recipeType, toAdd);
                manager.addRecipes(recipeType, toAdd);
            }
        }
        this.registeredRecipes = recipes;
        refreshStructures(manager);
    }

    private void refreshStructures(IRecipeManager manager) {
        Map<Identifier, MultiblockStructureData> structures = collectStructures();
        for (Map.Entry<Identifier, IRecipeType<MultiblockStructureData>> entry : this.structureTypes.entrySet()) {
            Identifier definitionId = entry.getKey();
            IRecipeType<MultiblockStructureData> structureType = entry.getValue();
            MultiblockStructureData oldStructure = this.registeredStructures.get(definitionId);
            MultiblockStructureData newStructure = structures.get(definitionId);
            if (oldStructure != null && !oldStructure.equals(newStructure)) {
                manager.hideRecipes(structureType, List.of(oldStructure));
            }
            if (newStructure != null && !newStructure.equals(oldStructure)) {
                manager.unhideRecipes(structureType, List.of(newStructure));
                manager.addRecipes(structureType, List.of(newStructure));
            }
        }
        this.registeredStructures = structures;
    }

    private static Map<Identifier, MultiblockStructureData> collectStructures() {
        Map<Identifier, DynamicControllerSpec> specs = new LinkedHashMap<>();
        for (DynamicControllerSpec spec : ModBlocks.DYNAMIC_CONTROLLER_SPECS) {
            specs.put(spec.definitionId(), spec);
        }
        Map<Identifier, MultiblockStructureData> structures = new LinkedHashMap<>();
        for (MultiblockDefinition definition : MultiblockDefinitions.all()) {
            DynamicControllerSpec spec = specs.get(definition.id());
            if (spec == null) {
                continue;
            }
            DeferredItem<BlockItem> item = ModBlocks.DYNAMIC_CONTROLLER_ITEMS.get(spec.blockId());
            if (item == null) {
                continue;
            }
            MultiblockStructureData structure =
                    MultiblockStructureData.create(definition, item.get().getBlock());
            if (!structure.controller().isEmpty()) {
                structures.put(definition.id(), structure);
            }
        }
        return Collections.unmodifiableMap(structures);
    }

    private Map<Identifier, List<MultiblockJeiRecipe>> collectRecipes() {
        if (this.recipeTypes.isEmpty()) {
            return Map.of();
        }
        RecipeMap recipeMap = ClientMultiblockEvents.recipes();
        if (recipeMap.values().isEmpty()) {
            return Map.of();
        }
        Map<Identifier, List<MultiblockJeiRecipe>> recipes = new LinkedHashMap<>();
        for (RecipeHolder<?> holder : recipeMap.values()) {
            if (!(holder.value() instanceof MultiblockRecipe recipe)) {
                continue;
            }
            Identifier recipeType = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
            if (recipeType == null) {
                continue;
            }
            for (MultiblockDefinition definition : MultiblockDefinitions.all()) {
                if (definition.type() != MultiblockType.RECIPE) {
                    continue;
                }
                if (!this.recipeTypes.containsKey(definition.id())) {
                    continue;
                }
                if (definition.recipeType().filter(recipeType::equals).isEmpty()) {
                    continue;
                }
                recipes.computeIfAbsent(definition.id(), key -> new ArrayList<>())
                        .add(new MultiblockJeiRecipe(definition, recipe, holder.id().identifier()));
            }
        }
        Map<Identifier, List<MultiblockJeiRecipe>> immutable = new LinkedHashMap<>();
        recipes.forEach((id, list) -> immutable.put(id, List.copyOf(list)));
        return Collections.unmodifiableMap(immutable);
    }

    private static IRecipeType<MultiblockJeiRecipe> recipeType(Identifier definitionId) {
        return IRecipeType.create(
                Identifier.fromNamespaceAndPath(
                        InfinityNexusMultiblock.MOD_ID, "controller/" + definitionId.getPath()),
                MultiblockJeiRecipe.class);
    }

    private static IRecipeType<MultiblockStructureData> structureType(Identifier definitionId) {
        return IRecipeType.create(
                Identifier.fromNamespaceAndPath(
                        InfinityNexusMultiblock.MOD_ID,
                        "structure/" + definitionId.getNamespace() + "/" + definitionId.getPath()),
                MultiblockStructureData.class);
    }
}
