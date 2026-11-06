package com.Infinity.Nexus.Multiblock.recipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.Infinity.Nexus.Multiblock.recipe.MultiblockRecipeIngredient.Energy;
import com.Infinity.Nexus.Multiblock.recipe.MultiblockRecipeIngredient.Entity;
import com.Infinity.Nexus.Multiblock.recipe.MultiblockRecipeIngredient.Fluid;
import com.Infinity.Nexus.Multiblock.recipe.MultiblockRecipeIngredient.Item;
import com.Infinity.Nexus.Multiblock.registry.ModRecipes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
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
import org.jspecify.annotations.Nullable;

public record MultiblockRecipe(
        RecipeType<MultiblockRecipe> recipeType,
        Identifier definition,
        int time,
        List<MultiblockRecipeIngredient> inputs,
        List<MultiblockRecipeOutput> outputs) implements Recipe<MultiblockRecipeInput> {

    public MultiblockRecipe {
        Objects.requireNonNull(recipeType, "recipeType");
        Objects.requireNonNull(definition, "definition");
        if (time < 1) {
            throw new IllegalArgumentException("Recipe time must be at least 1 tick");
        }
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
    }

    public int energy() {
        long total = 0L;
        for (MultiblockRecipeIngredient input : this.inputs) {
            if (input instanceof Energy energy) {
                total += energy.amount();
                if (total >= Integer.MAX_VALUE) {
                    return Integer.MAX_VALUE;
                }
            }
        }
        return (int) total;
    }

    public List<Item> itemInputs() {
        List<Item> items = new ArrayList<>();
        for (MultiblockRecipeIngredient input : this.inputs) {
            if (input instanceof Item item) {
                items.add(item);
            }
        }
        return items;
    }

    public List<Fluid> fluidInputs() {
        List<Fluid> fluids = new ArrayList<>();
        for (MultiblockRecipeIngredient input : this.inputs) {
            if (input instanceof Fluid fluid) {
                fluids.add(fluid);
            }
        }
        return fluids;
    }

    public List<Entity> entityInputs() {
        List<Entity> entities = new ArrayList<>();
        for (MultiblockRecipeIngredient input : this.inputs) {
            if (input instanceof Entity entity) {
                entities.add(entity);
            }
        }
        return entities;
    }

    public List<Ingredient> ingredients() {
        List<Ingredient> ingredients = new ArrayList<>();
        for (MultiblockRecipeIngredient input : this.inputs) {
            if (input instanceof Item item) {
                ingredients.add(item.ingredient());
            }
        }
        return ingredients;
    }

    public List<SizedFluidIngredient> fluidIngredients() {
        List<SizedFluidIngredient> fluids = new ArrayList<>();
        for (MultiblockRecipeIngredient input : this.inputs) {
            if (input instanceof Fluid fluid) {
                fluids.add(fluid.ingredient());
            }
        }
        return fluids;
    }

    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        list.addAll(this.ingredients());
        return list;
    }

    @Override
    public boolean matches(MultiblockRecipeInput input, Level level) {
        if (level.isClientSide() || this.inputs.isEmpty()) {
            return false;
        }
        if (this.findItemInputs(input.items()) == null) {
            return false;
        }
        int[] fluidTanks = this.matchFluidTanks(input.fluids());
        for (int tank : fluidTanks) {
            if (tank < 0) {
                return false;
            }
        }
        return true;
    }

    public @Nullable List<ItemAllocation> findItemInputs(List<ItemStack> items) {
        List<Item> itemInputs = this.itemInputs();
        int[] remaining = new int[items.size()];
        for (int index = 0; index < items.size(); index++) {
            remaining[index] = items.get(index).getCount();
        }
        List<ItemAllocation> allocations = new ArrayList<>();
        if (this.findItemInputs(items, itemInputs, 0, remaining, allocations)) {
            return List.copyOf(allocations);
        }
        return null;
    }

    private boolean findItemInputs(
            List<ItemStack> items,
            List<Item> itemInputs,
            int inputIndex,
            int[] remaining,
            List<ItemAllocation> allocations) {
        if (inputIndex >= itemInputs.size()) {
            return true;
        }
        return this.findItemInputCount(
                items,
                itemInputs,
                inputIndex,
                itemInputs.get(inputIndex).count(),
                0,
                remaining,
                allocations);
    }

    private boolean findItemInputCount(
            List<ItemStack> items,
            List<Item> itemInputs,
            int inputIndex,
            int needed,
            int startSlot,
            int[] remaining,
            List<ItemAllocation> allocations) {
        if (needed <= 0) {
            return this.findItemInputs(items, itemInputs, inputIndex + 1, remaining, allocations);
        }
        Item input = itemInputs.get(inputIndex);
        for (int slot = startSlot; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            int available = remaining[slot];
            if (available <= 0 || stack.isEmpty() || !input.ingredient().test(stack)) {
                continue;
            }
            int max = Math.min(available, needed);
            for (int amount = max; amount >= 1; amount--) {
                remaining[slot] -= amount;
                allocations.add(new ItemAllocation(slot, amount));
                if (this.findItemInputCount(
                        items,
                        itemInputs,
                        inputIndex,
                        needed - amount,
                        slot + 1,
                        remaining,
                        allocations)) {
                    return true;
                }
                allocations.remove(allocations.size() - 1);
                remaining[slot] += amount;
            }
        }
        return false;
    }

    public record ItemAllocation(int slot, int count) {
        public ItemAllocation {
            if (slot < 0) {
                throw new IllegalArgumentException("slot must not be negative");
            }
            if (count < 1) {
                throw new IllegalArgumentException("count must be at least 1");
            }
        }
    }

    public int[] matchFluidTanks(List<FluidStack> fluids) {
        List<Fluid> fluidInputs = this.fluidInputs();
        int[] matchedTanks = new int[fluidInputs.size()];
        Arrays.fill(matchedTanks, -1);
        int[] remaining = new int[fluids.size()];
        for (int index = 0; index < fluids.size(); index++) {
            remaining[index] = fluids.get(index).getAmount();
        }
        if (matchFluidTanks(fluids, fluidInputs, 0, remaining, matchedTanks)) {
            return matchedTanks;
        }
        Arrays.fill(matchedTanks, -1);
        return matchedTanks;
    }

    private boolean matchFluidTanks(
            List<FluidStack> fluids,
            List<Fluid> fluidInputs,
            int inputIndex,
            int[] remaining,
            int[] matchedTanks) {
        if (inputIndex >= fluidInputs.size()) {
            return true;
        }
        SizedFluidIngredient ingredient = fluidInputs.get(inputIndex).ingredient();
        for (int tank = 0; tank < fluids.size(); tank++) {
            FluidStack stack = fluids.get(tank);
            if (remaining[tank] < ingredient.amount() || stack.isEmpty() || !ingredient.test(stack)) {
                continue;
            }
            remaining[tank] -= ingredient.amount();
            matchedTanks[inputIndex] = tank;
            if (this.matchFluidTanks(fluids, fluidInputs, inputIndex + 1, remaining, matchedTanks)) {
                return true;
            }
            remaining[tank] += ingredient.amount();
            matchedTanks[inputIndex] = -1;
        }
        return false;
    }

    @Override
    public ItemStack assemble(MultiblockRecipeInput input) {
        for (MultiblockRecipeOutput output : this.outputs) {
            if (output instanceof MultiblockRecipeOutput.Item item) {
                return item.stack().create();
            }
        }
        return ItemStack.EMPTY;
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
        return PlacementInfo.create(this.ingredients());
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }

    public static MapCodec<MultiblockRecipe> codec() {
        MapCodec<RecipeFields> fields = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Identifier.CODEC.fieldOf("definition").forGetter(RecipeFields::definition),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("time", 100).forGetter(RecipeFields::time),
                MultiblockRecipeIngredient.CODEC.listOf()
                        .optionalFieldOf("inputs", List.of())
                        .forGetter(RecipeFields::inputs),
                MultiblockRecipeOutput.CODEC.listOf()
                        .optionalFieldOf("outputs", List.of())
                        .forGetter(RecipeFields::outputs),
                Ingredient.CODEC.listOf()
                        .optionalFieldOf("ingredients", List.of())
                        .forGetter(RecipeFields::legacyIngredients),
                SizedFluidIngredient.CODEC.listOf()
                        .optionalFieldOf("fluid_ingredients", List.of())
                        .forGetter(RecipeFields::legacyFluidIngredients),
                MultiblockRecipeOutput.CODEC.optionalFieldOf("output").forGetter(RecipeFields::legacyOutput),
                Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("energy", 0).forGetter(RecipeFields::legacyEnergy),
                ItemStackTemplate.CODEC.optionalFieldOf("result").forGetter(RecipeFields::legacyResult))
                .apply(instance, RecipeFields::new));
        return fields.flatXmap(
                RecipeFields::toRecipe,
                recipe -> DataResult.success(RecipeFields.from(recipe)));
    }

    public static StreamCodec<RegistryFriendlyByteBuf, MultiblockRecipe> streamCodec() {
        return StreamCodec.composite(
                Identifier.STREAM_CODEC, MultiblockRecipe::definition,
                ByteBufCodecs.INT, MultiblockRecipe::time,
                MultiblockRecipeIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()), MultiblockRecipe::inputs,
                MultiblockRecipeOutput.STREAM_CODEC.apply(ByteBufCodecs.list()), MultiblockRecipe::outputs,
                (definition, time, inputs, outputs) ->
                        new MultiblockRecipe(ModRecipes.MULTIBLOCK_TYPE, definition, time, inputs, outputs));
    }

    private record RecipeFields(
            Identifier definition,
            int time,
            List<MultiblockRecipeIngredient> inputs,
            List<MultiblockRecipeOutput> outputs,
            List<Ingredient> legacyIngredients,
            List<SizedFluidIngredient> legacyFluidIngredients,
            Optional<MultiblockRecipeOutput> legacyOutput,
            int legacyEnergy,
            Optional<ItemStackTemplate> legacyResult) {

        private DataResult<MultiblockRecipe> toRecipe() {
            List<MultiblockRecipeIngredient> resolvedInputs = new ArrayList<>(this.inputs);
            if (resolvedInputs.isEmpty()) {
                for (Ingredient ingredient : this.legacyIngredients) {
                    resolvedInputs.add(new Item(ingredient));
                }
                for (SizedFluidIngredient ingredient : this.legacyFluidIngredients) {
                    resolvedInputs.add(new Fluid(ingredient));
                }
                if (this.legacyEnergy > 0) {
                    resolvedInputs.add(new Energy(this.legacyEnergy));
                }
            }
            List<MultiblockRecipeOutput> resolvedOutputs = new ArrayList<>(this.outputs);
            if (resolvedOutputs.isEmpty()) {
                this.legacyOutput.ifPresent(resolvedOutputs::add);
                this.legacyResult
                        .map(MultiblockRecipeOutput.Item::new)
                        .ifPresent(resolvedOutputs::add);
            }
            if (resolvedInputs.isEmpty()) {
                return DataResult.error(() -> "Recipe needs at least one input");
            }
            if (resolvedOutputs.isEmpty()) {
                return DataResult.error(() -> "Recipe needs at least one output");
            }
            return DataResult.success(new MultiblockRecipe(
                    ModRecipes.MULTIBLOCK_TYPE,
                    this.definition,
                    this.time,
                    resolvedInputs,
                    resolvedOutputs));
        }

        private static RecipeFields from(MultiblockRecipe recipe) {
            return new RecipeFields(
                    recipe.definition(),
                    recipe.time(),
                    recipe.inputs(),
                    recipe.outputs(),
                    List.of(),
                    List.of(),
                    Optional.empty(),
                    0,
                    Optional.empty());
        }
    }
}
