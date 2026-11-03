package com.Infinity.Nexus.Multiblock.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public record MultiblockControllerRecipe(List<String> pattern, Map<Character, String> key) {
    public MultiblockControllerRecipe {
        pattern = List.copyOf(Objects.requireNonNull(pattern, "pattern"));
        key = Map.copyOf(Objects.requireNonNull(key, "key"));
    }

    public static Optional<MultiblockControllerRecipe> parse(
            @Nullable JsonElement element,
            Consumer<String> warning) {
        Objects.requireNonNull(warning, "warning");
        if (element == null || element.isJsonNull()) {
            return Optional.empty();
        }
        if (!element.isJsonObject()) {
            warning.accept("recipe must be an object");
            return Optional.empty();
        }

        JsonObject recipe = element.getAsJsonObject();
        JsonElement patternElement = recipe.get("pattern");
        if (patternElement == null || !patternElement.isJsonArray()) {
            warning.accept("recipe.pattern must be an array");
            return Optional.empty();
        }

        List<String> pattern = new ArrayList<>();
        for (JsonElement row : patternElement.getAsJsonArray()) {
            if (!row.isJsonPrimitive() || !row.getAsJsonPrimitive().isString()) {
                warning.accept("recipe.pattern rows must be strings");
                return Optional.empty();
            }
            pattern.add(row.getAsString());
        }
        if (pattern.isEmpty() || pattern.size() > 3) {
            warning.accept("recipe.pattern must contain between 1 and 3 rows");
            return Optional.empty();
        }

        int width = pattern.get(0).length();
        if (width < 1 || width > 3) {
            warning.accept("recipe.pattern rows must contain between 1 and 3 characters");
            return Optional.empty();
        }

        Set<Character> usedSymbols = new LinkedHashSet<>();
        for (String row : pattern) {
            if (row.length() != width) {
                warning.accept("recipe.pattern rows must have the same width");
                return Optional.empty();
            }
            for (int index = 0; index < row.length(); index++) {
                char symbol = row.charAt(index);
                if (symbol != ' ') {
                    usedSymbols.add(symbol);
                }
            }
        }
        if (usedSymbols.isEmpty()) {
            warning.accept("recipe.pattern must contain at least one ingredient");
            return Optional.empty();
        }

        JsonElement keyElement = recipe.get("key");
        if (keyElement == null || !keyElement.isJsonObject()) {
            warning.accept("recipe.key must be an object");
            return Optional.empty();
        }

        Map<Character, String> key = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : keyElement.getAsJsonObject().entrySet()) {
            String symbolText = entry.getKey();
            if (symbolText.length() != 1 || symbolText.charAt(0) == ' ') {
                warning.accept("recipe.key contains an invalid symbol '" + symbolText + "'");
                return Optional.empty();
            }
            JsonElement value = entry.getValue();
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                warning.accept("recipe ingredient for '" + symbolText + "' must be a string");
                return Optional.empty();
            }
            String ingredient = value.getAsString().trim();
            if (!isValidIngredient(ingredient)) {
                warning.accept("recipe ingredient '" + ingredient + "' is not an item or tag id");
                return Optional.empty();
            }
            key.put(symbolText.charAt(0), ingredient);
        }

        if (!key.keySet().equals(usedSymbols)) {
            warning.accept("recipe.key symbols must match the symbols used by recipe.pattern");
            return Optional.empty();
        }

        return Optional.of(new MultiblockControllerRecipe(pattern, key));
    }

    private static boolean isValidIngredient(String value) {
        String text = value.startsWith("#") ? value.substring(1) : value;
        return !text.isEmpty() && Identifier.tryParse(text) != null;
    }
}
