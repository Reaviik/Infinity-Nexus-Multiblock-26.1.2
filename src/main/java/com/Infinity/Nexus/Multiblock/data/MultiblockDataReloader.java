package com.Infinity.Nexus.Multiblock.data;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.Infinity.Nexus.Multiblock.InfinityNexusMultiblock;
import com.Infinity.Nexus.Multiblock.recipe.MultiblockRecipe;
import com.Infinity.Nexus.Multiblock.registry.ModRecipes;
import com.Infinity.Nexus.Multiblock.resource.ControllerRecipePack;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import net.neoforged.neoforge.common.conditions.ConditionalOps;
import net.neoforged.neoforge.common.conditions.ICondition;
import org.jspecify.annotations.Nullable;

public final class MultiblockDataReloader {
    private static final FileToIdConverter RECIPE_LISTER = FileToIdConverter.registry(Registries.RECIPE);
    private static final Field RECIPE_MAP_FIELD = findRecipeMapField();

    private MultiblockDataReloader() {}

    public static Result reload(MinecraftServer server) {
        Set<ResourceKey<Recipe<?>>> staleControllerRecipes =
                controllerRecipeKeys(MultiblockDefinitionLoader.resolvedFiles());
        int definitions = MultiblockDefinitionLoader.reloadFrom(server.getResourceManager());
        Map<Identifier, JsonObject> resolvedDefinitions = MultiblockDefinitionLoader.resolvedFiles();
        staleControllerRecipes.addAll(controllerRecipeKeys(resolvedDefinitions));
        int recipes;
        try {
            recipes = reloadRecipes(server, staleControllerRecipes, resolvedDefinitions);
        } finally {
            server.getPlayerList().reloadResources();
        }
        return new Result(definitions, recipes);
    }

    private static Set<ResourceKey<Recipe<?>>> controllerRecipeKeys(Map<Identifier, JsonObject> definitions) {
        Set<ResourceKey<Recipe<?>>> keys = new LinkedHashSet<>();
        definitions.forEach((id, json) -> {
            if (json.has("recipe")) {
                keys.add(ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath(
                        id.getNamespace(), "controller/" + id.getPath())));
            }
        });
        return keys;
    }

    private static int reloadRecipes(
            MinecraftServer server,
            Set<ResourceKey<Recipe<?>>> staleControllerRecipes,
            Map<Identifier, JsonObject> resolvedDefinitions) {
        RecipeManager recipeManager = server.getRecipeManager();
        Map<ResourceKey<Recipe<?>>, RecipeHolder<?>> recipes = new LinkedHashMap<>();
        for (RecipeHolder<?> holder : recipeManager.recipeMap().values()) {
            if (holder.value().getType() != ModRecipes.MULTIBLOCK_TYPE) {
                recipes.put(holder.id(), holder);
            }
        }
        recipes.keySet().removeAll(staleControllerRecipes);

        Codec<Optional<Recipe<?>>> codec = ConditionalOps.createConditionalCodec(Recipe.CODEC);
        DynamicOps<JsonElement> ops = new ConditionalOps<>(
                server.registryAccess().createSerializationContext(JsonOps.INSTANCE),
                ICondition.IContext.EMPTY);
        int loaded = 0;

        for (Map.Entry<Identifier, Resource> entry
                : RECIPE_LISTER.listMatchingResources(server.getResourceManager()).entrySet()) {
            Identifier id = RECIPE_LISTER.fileToId(entry.getKey());
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonElement element = JsonParser.parseReader(reader);
                if (!element.isJsonObject() || !isMultiblockRecipe(element.getAsJsonObject())) {
                    continue;
                }
                Recipe<?> recipe = parseRecipe(codec, ops, id, element.getAsJsonObject());
                if (recipe == null) {
                    continue;
                }
                if (recipe instanceof MultiblockRecipe multiblockRecipe
                        && !resolvedDefinitions.containsKey(multiblockRecipe.definition())) {
                    InfinityNexusMultiblock.LOGGER.debug(
                            "Skipped multiblock recipe {} because definition {} is not loaded",
                            id, multiblockRecipe.definition());
                    continue;
                }
                ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, id);
                recipes.put(key, new RecipeHolder<>(key, recipe));
                loaded++;
            } catch (IOException | RuntimeException exception) {
                InfinityNexusMultiblock.LOGGER.error(
                        "Failed to read multiblock recipe {}: {}", id, exception.getMessage());
            }
        }

        for (Map.Entry<Identifier, byte[]> entry
                : ControllerRecipePack.generateRecipes(resolvedDefinitions).entrySet()) {
            Identifier id = RECIPE_LISTER.fileToId(entry.getKey());
            try {
                JsonElement element = JsonParser.parseString(
                        new String(entry.getValue(), StandardCharsets.UTF_8));
                if (!element.isJsonObject()) {
                    continue;
                }
                Recipe<?> recipe = parseRecipe(codec, ops, id, element.getAsJsonObject());
                if (recipe == null) {
                    continue;
                }
                ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, id);
                recipes.put(key, new RecipeHolder<>(key, recipe));
                loaded++;
            } catch (RuntimeException exception) {
                InfinityNexusMultiblock.LOGGER.error(
                        "Failed to generate controller recipe {}: {}", id, exception.getMessage());
            }
        }

        RecipeMap updated = RecipeMap.create(recipes.values());
        setRecipeMap(recipeManager, updated);
        recipeManager.finalizeRecipeLoading(server.getWorldData().enabledFeatures());
        return loaded;
    }

    private static boolean isMultiblockRecipe(JsonObject json) {
        JsonElement type = json.get("type");
        return type != null
                && type.isJsonPrimitive()
                && type.getAsJsonPrimitive().isString()
                && ModRecipes.MULTIBLOCK_RECIPE_TYPE_ID.toString().equals(type.getAsString());
    }

    private static @Nullable Recipe<?> parseRecipe(
            Codec<Optional<Recipe<?>>> codec,
            DynamicOps<JsonElement> ops,
            Identifier id,
            JsonObject json) {
        Optional<Recipe<?>> parsed = codec.parse(ops, json)
                .getOrThrow(message -> new JsonParseException(
                        "Invalid multiblock recipe " + id + ": " + message));
        if (parsed.isEmpty()) {
            InfinityNexusMultiblock.LOGGER.debug(
                    "Skipped multiblock recipe {} because its conditions were not met", id);
            return null;
        }
        return parsed.get();
    }

    private static Field findRecipeMapField() {
        for (Field field : RecipeManager.class.getDeclaredFields()) {
            if (field.getType() == RecipeMap.class) {
                field.setAccessible(true);
                return field;
            }
        }
        throw new IllegalStateException("Could not find the RecipeMap field in RecipeManager");
    }

    private static void setRecipeMap(RecipeManager recipeManager, RecipeMap recipes) {
        try {
            RECIPE_MAP_FIELD.set(recipeManager, recipes);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Could not update the recipe manager", exception);
        }
    }

    public record Result(int definitions, int recipes) {}
}
