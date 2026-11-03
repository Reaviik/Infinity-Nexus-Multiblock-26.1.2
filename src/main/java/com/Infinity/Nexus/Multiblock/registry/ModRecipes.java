package com.Infinity.Nexus.Multiblock.registry;

import java.util.List;

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
    public static final Identifier MULTIBLOCK_RECIPE_TYPE_ID =
            Identifier.fromNamespaceAndPath(InfinityNexusMultiblock.MOD_ID, "multiblock");

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, InfinityNexusMultiblock.MOD_ID);
    public static final DeferredRegister<RecipeType<?>> TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, InfinityNexusMultiblock.MOD_ID);

    public static final RecipeType<MultiblockRecipe> MULTIBLOCK_TYPE = new RecipeType<>() {
        @Override
        public String toString() {
            return MULTIBLOCK_RECIPE_TYPE_ID.toString();
        }
    };

    public static final RecipeSerializer<MultiblockRecipe> MULTIBLOCK_SERIALIZER = new RecipeSerializer<>(
            MultiblockRecipe.codec(), MultiblockRecipe.streamCodec());

    static {
        TYPES.register(MULTIBLOCK_RECIPE_TYPE_ID.getPath(), () -> MULTIBLOCK_TYPE);
        SERIALIZERS.register(MULTIBLOCK_RECIPE_TYPE_ID.getPath(), () -> MULTIBLOCK_SERIALIZER);
    }

    public static List<RecipeType<?>> types() {
        return List.of(MULTIBLOCK_TYPE);
    }

    public static @Nullable RecipeSerializer<MultiblockRecipe> serializerFor(RecipeType<?> recipeType) {
        return recipeType == MULTIBLOCK_TYPE ? MULTIBLOCK_SERIALIZER : null;
    }

    public static void register(IEventBus eventBus) {
        SERIALIZERS.register(eventBus);
        TYPES.register(eventBus);
    }

    private ModRecipes() {}
}
