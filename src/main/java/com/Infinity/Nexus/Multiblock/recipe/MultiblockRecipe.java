package com.Infinity.Nexus.Multiblock.recipe;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.Infinity.Nexus.Multiblock.registry.ModRecipes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

public record MultiblockRecipe(
        RecipeType<MultiblockRecipe> recipeType,
        Identifier definition,
        int energy,
        int time,
        List<Ingredient> ingredients,
        List<SizedFluidIngredient> fluidIngredients,
        MultiblockRecipeOutput output) implements Recipe<MultiblockRecipeInput> {

    public MultiblockRecipe {
        Objects.requireNonNull(recipeType, "recipeType");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(output, "output");
        if (energy < 0) {
            throw new IllegalArgumentException("Recipe energy cannot be negative");
        }
        if (time < 1) {
            throw new IllegalArgumentException("Recipe time must be at least 1 tick");
        }
        ingredients = List.copyOf(ingredients);
        fluidIngredients = List.copyOf(fluidIngredients);
    }

    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        list.addAll(this.ingredients);
        return list;
    }

    @Override
    public boolean matches(MultiblockRecipeInput input, Level level) {
        if (level.isClientSide()
                || (this.ingredients.isEmpty() && this.fluidIngredients.isEmpty())
                || input.size() < this.ingredients.size()) {
            return false;
        }
        for (int slot : this.matchItemSlots(input.items())) {
            if (slot < 0) {
                return false;
            }
        }
        return this.matchesFluidIngredients(input.fluids());
    }

    public int[] matchItemSlots(List<ItemStack> items) {
        int[] matchedSlots = new int[this.ingredients.size()];
        for (int index = 0; index < matchedSlots.length; index++) {
            matchedSlots[index] = -1;
        }
        if (items.size() < this.ingredients.size()) {
            return matchedSlots;
        }
        this.matchItemSlots(items, 0, new boolean[items.size()], matchedSlots);
        return matchedSlots;
    }

    private boolean matchItemSlots(List<ItemStack> items, int ingredientIndex, boolean[] usedSlots, int[] matchedSlots) {
        if (ingredientIndex >= this.ingredients.size()) {
            return true;
        }
        Ingredient ingredient = this.ingredients.get(ingredientIndex);
        for (int slot = 0; slot < items.size(); slot++) {
            if (usedSlots[slot]) {
                continue;
            }
            ItemStack stack = items.get(slot);
            if (stack.isEmpty() || !ingredient.test(stack)) {
                continue;
            }
            usedSlots[slot] = true;
            matchedSlots[ingredientIndex] = slot;
            if (this.matchItemSlots(items, ingredientIndex + 1, usedSlots, matchedSlots)) {
                return true;
            }
            matchedSlots[ingredientIndex] = -1;
            usedSlots[slot] = false;
        }
        return false;
    }

    private boolean matchesFluidIngredients(List<FluidStack> fluids) {
        if (this.fluidIngredients.isEmpty()) {
            return true;
        }
        boolean[] used = new boolean[fluids.size()];
        for (SizedFluidIngredient ingredient : this.fluidIngredients) {
            boolean matched = false;
            for (int index = 0; index < fluids.size(); index++) {
                FluidStack stack = fluids.get(index);
                if (!used[index] && !stack.isEmpty() && ingredient.test(stack)) {
                    used[index] = true;
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(MultiblockRecipeInput input) {
        return this.output instanceof MultiblockRecipeOutput.Item item
                ? item.stack().create()
                : ItemStack.EMPTY;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public RecipeSerializer<? extends Recipe<MultiblockRecipeInput>> getSerializer() {
        RecipeSerializer<MultiblockRecipe> serializer = ModRecipes.serializerFor(this.recipeType);
        if (serializer == null) {
            throw new IllegalStateException("No serializer registered for recipe type " + this.recipeType);
        }
        return serializer;
    }

    @Override
    public RecipeType<? extends Recipe<MultiblockRecipeInput>> getType() {
        return this.recipeType;
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.create(this.ingredients);
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }

    public static MapCodec<MultiblockRecipe> codec() {
        MapCodec<RecipeFields> fields = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Identifier.CODEC.fieldOf("definition").forGetter(RecipeFields::definition),
                Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("energy", 0).forGetter(RecipeFields::energy),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("time", 100).forGetter(RecipeFields::time),
                Ingredient.CODEC.listOf().optionalFieldOf("ingredients", List.of()).forGetter(RecipeFields::ingredients),
                SizedFluidIngredient.CODEC.listOf()
                        .optionalFieldOf("fluid_ingredients", List.of())
                        .forGetter(RecipeFields::fluidIngredients),
                MultiblockRecipeOutput.CODEC.optionalFieldOf("output").forGetter(RecipeFields::output),
                ItemStackTemplate.CODEC.optionalFieldOf("result").forGetter(RecipeFields::legacyResult))
                .apply(instance, RecipeFields::new));
        return fields.flatXmap(
                RecipeFields::toRecipe,
                recipe -> DataResult.success(RecipeFields.from(recipe)));
    }

    public static StreamCodec<RegistryFriendlyByteBuf, MultiblockRecipe> streamCodec() {
        return StreamCodec.composite(
                Identifier.STREAM_CODEC, MultiblockRecipe::definition,
                ByteBufCodecs.INT, MultiblockRecipe::energy,
                ByteBufCodecs.INT, MultiblockRecipe::time,
                Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()), MultiblockRecipe::ingredients,
                SizedFluidIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()), MultiblockRecipe::fluidIngredients,
                MultiblockRecipeOutput.STREAM_CODEC, MultiblockRecipe::output,
                (definition, energy, time, ingredients, fluidIngredients, output) ->
                        new MultiblockRecipe(ModRecipes.MULTIBLOCK_TYPE, definition, energy, time,
                                ingredients, fluidIngredients, output));
    }

    private record RecipeFields(
            Identifier definition,
            int energy,
            int time,
            List<Ingredient> ingredients,
            List<SizedFluidIngredient> fluidIngredients,
            Optional<MultiblockRecipeOutput> output,
            Optional<ItemStackTemplate> legacyResult) {

        private DataResult<MultiblockRecipe> toRecipe() {
            Optional<MultiblockRecipeOutput> resolved = this.output.or(() ->
                    this.legacyResult.map(MultiblockRecipeOutput.Item::new));
            if (resolved.isEmpty()) {
                return DataResult.error(() -> "Recipe needs an output");
            }
            return DataResult.success(new MultiblockRecipe(
                    ModRecipes.MULTIBLOCK_TYPE,
                    this.definition,
                    this.energy,
                    this.time,
                    this.ingredients,
                    this.fluidIngredients,
                    resolved.get()));
        }

        private static RecipeFields from(MultiblockRecipe recipe) {
            return new RecipeFields(
                    recipe.definition(),
                    recipe.energy(),
                    recipe.time(),
                    recipe.ingredients(),
                    recipe.fluidIngredients(),
                    Optional.of(recipe.output()),
                    Optional.empty());
        }
    }
}
