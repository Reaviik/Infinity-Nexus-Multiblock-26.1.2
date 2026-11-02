package com.Infinity.Nexus.Multiblock.registry;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.Infinity.Nexus.Multiblock.InfinityNexusMultiblock;
import com.Infinity.Nexus.Multiblock.recipe.MultiblockRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.Nullable;

public final class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, InfinityNexusMultiblock.MOD_ID);
    public static final DeferredRegister<RecipeType<?>> TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, InfinityNexusMultiblock.MOD_ID);

    private static final Map<Identifier, RecipeType<MultiblockRecipe>> RECIPE_TYPES = new LinkedHashMap<>();
    private static final Map<RecipeType<?>, RecipeSerializer<MultiblockRecipe>> SERIALIZERS_BY_TYPE =
            new IdentityHashMap<>();

    static {
        for (Identifier recipeTypeId : DynamicControllerRegistry.recipeTypes()) {
            if (!InfinityNexusMultiblock.MOD_ID.equals(recipeTypeId.getNamespace())) {
                InfinityNexusMultiblock.LOGGER.debug(
                        "Skipping dynamic recipe type {} because it is not in this namespace", recipeTypeId);
                continue;
            }
            RecipeType<MultiblockRecipe> recipeType = new RecipeType<>() {
                @Override
                public String toString() {
                    return recipeTypeId.toString();
                }
            };
            RecipeSerializer<MultiblockRecipe> serializer = new RecipeSerializer<>(
                    MultiblockRecipe.codec(recipeType), MultiblockRecipe.streamCodec(recipeType));
            TYPES.register(recipeTypeId.getPath(), () -> recipeType);
            SERIALIZERS.register(recipeTypeId.getPath(), () -> serializer);
            RECIPE_TYPES.put(recipeTypeId, recipeType);
            SERIALIZERS_BY_TYPE.put(recipeType, serializer);
            InfinityNexusMultiblock.LOGGER.info("Registered dynamic recipe type {}", recipeTypeId);
        }
    }

    public static List<RecipeType<?>> types() {
        return List.copyOf(RECIPE_TYPES.values());
    }

    public static @Nullable RecipeSerializer<MultiblockRecipe> serializerFor(RecipeType<?> recipeType) {
        return SERIALIZERS_BY_TYPE.get(recipeType);
    }

    public static void register(IEventBus eventBus) {
        SERIALIZERS.register(eventBus);
        TYPES.register(eventBus);
    }

    private ModRecipes() {}
}
