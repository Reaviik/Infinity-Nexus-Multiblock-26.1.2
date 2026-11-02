package com.Infinity.Nexus.Multiblock.registry;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.Infinity.Nexus.Multiblock.InfinityNexusMultiblock;
import com.Infinity.Nexus.Multiblock.api.MultiblockControllerConfig;
import com.Infinity.Nexus.Multiblock.api.MultiblockControllerRecipe;
import com.Infinity.Nexus.Multiblock.api.MultiblockDefinition;
import com.Infinity.Nexus.Multiblock.api.MultiblockType;
import com.Infinity.Nexus.Multiblock.data.MultiblockDefinitionFiles;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;
import org.jspecify.annotations.Nullable;

public final class DynamicControllerRegistry {
    private static final MultiblockControllerConfig.Textures FALLBACK_TEXTURES =
            new MultiblockControllerConfig.Textures(
                    texture("fallback/front"),
                    texture("fallback/back"),
                    texture("fallback/left"),
                    texture("fallback/right"),
                    texture("fallback/top"),
                    texture("fallback/bottom"));

    private static final Set<Identifier> RECIPE_TYPES = new LinkedHashSet<>();
    private static final Map<Identifier, DynamicControllerSpec> SPECS = load();

    private DynamicControllerRegistry() {}

    public static List<DynamicControllerSpec> specs() {
        return List.copyOf(SPECS.values());
    }

    public static List<Identifier> recipeTypes() {
        return List.copyOf(RECIPE_TYPES);
    }

    public static Map<Identifier, DynamicControllerSpec> byBlockId() {
        return SPECS;
    }

    public static @Nullable DynamicControllerSpec get(Identifier blockId) {
        return SPECS.get(blockId);
    }

    private static Map<Identifier, DynamicControllerSpec> load() {
        IModFileInfo modFileInfo = ModList.get().getModFileById(InfinityNexusMultiblock.MOD_ID);
        if (modFileInfo == null) {
            InfinityNexusMultiblock.LOGGER.warn(
                    "Could not find the {} mod file while scanning dynamic controllers",
                    InfinityNexusMultiblock.MOD_ID);
            return Map.of();
        }

        Map<Identifier, JsonObject> files = new LinkedHashMap<>();
        modFileInfo.getFile().getContents().visitContent("data", (relativePath, resource) -> {
            Identifier id = definitionId(relativePath);
            if (id == null) {
                return;
            }
            try (Reader reader = resource.bufferedReader()) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (!parsed.isJsonObject()) {
                    InfinityNexusMultiblock.LOGGER.warn("Multiblock definition {} is not a JSON object", id);
                    return;
                }
                files.put(id, parsed.getAsJsonObject());
            } catch (RuntimeException | java.io.IOException exception) {
                InfinityNexusMultiblock.LOGGER.error(
                        "Failed to read multiblock definition {}: {}", id, exception.getMessage());
            }
        });

        Map<Identifier, JsonObject> resolved = new LinkedHashMap<>();
        Map<Identifier, DynamicControllerSpec> specs = new LinkedHashMap<>();
        files.keySet().stream().sorted().forEach(id -> {
            try {
                JsonObject original = files.get(id);
                if (MultiblockDefinitionFiles.isTemplate(original)) {
                    return;
                }
                JsonObject json = MultiblockDefinitionFiles.resolve(id, files, resolved, new HashSet<>());
                DynamicControllerSpec spec = createSpec(id, json);
                if (spec == null) {
                    return;
                }
                DynamicControllerSpec previous = specs.putIfAbsent(spec.blockId(), spec);
                if (previous != null) {
                    InfinityNexusMultiblock.LOGGER.warn(
                            "Duplicate dynamic controller block {} for definitions {} and {}",
                            spec.blockId(), previous.definitionId(), spec.definitionId());
                } else if (spec.type() == MultiblockType.RECIPE) {
                    collectRecipeType(id, json);
                }
            } catch (RuntimeException exception) {
                InfinityNexusMultiblock.LOGGER.error(
                        "Failed to scan multiblock definition {}: {}", id, exception.getMessage());
            }
        });

        InfinityNexusMultiblock.LOGGER.info("Discovered {} dynamic multiblock controllers", specs.size());
        return Collections.unmodifiableMap(specs);
    }

    private static @Nullable DynamicControllerSpec createSpec(Identifier id, JsonObject json) {
        if (!InfinityNexusMultiblock.MOD_ID.equals(id.getNamespace())) {
            return null;
        }
        if (!json.has("pattern") || !json.get("pattern").isJsonObject()) {
            InfinityNexusMultiblock.LOGGER.warn("Dynamic controller definition {} has no pattern", id);
            return null;
        }

        Identifier declaredId = Identifier.CODEC.parse(JsonOps.INSTANCE, json.get("id"))
                .result()
                .orElse(null);
        if (!id.equals(declaredId)) {
            InfinityNexusMultiblock.LOGGER.warn(
                    "Dynamic controller definition {} declares id {}", id, declaredId);
            return null;
        }

        int schemaVersion = json.get("schema_version").getAsInt();
        if (schemaVersion != MultiblockDefinition.CURRENT_SCHEMA_VERSION) {
            InfinityNexusMultiblock.LOGGER.warn(
                    "Dynamic controller definition {} uses unsupported schema_version {}",
                    id, schemaVersion);
            return null;
        }

        MultiblockType type = MultiblockType.CODEC.parse(JsonOps.INSTANCE, json.get("type"))
                .getOrThrow(message -> new IllegalArgumentException("Invalid multiblock type: " + message));

        MultiblockControllerRecipe recipe = parseRecipe(id, json.get("recipe"));

        JsonElement controllerElement = json.get("controller");
        if (controllerElement == null) {
            return new DynamicControllerSpec(
                    id, id, type, FALLBACK_TEXTURES, null, FALLBACK_TEXTURES.front(), 0, recipe);
        }
        if (!controllerElement.isJsonObject()) {
            InfinityNexusMultiblock.LOGGER.warn("Dynamic controller definition {} has an invalid controller", id);
            return null;
        }

        MultiblockControllerConfig controller = MultiblockControllerConfig.CODEC
                .parse(JsonOps.INSTANCE, controllerElement)
                .getOrThrow(message -> new IllegalArgumentException("Invalid controller: " + message));
        Identifier blockId = controller.block();
        if (!InfinityNexusMultiblock.MOD_ID.equals(blockId.getNamespace())) {
            InfinityNexusMultiblock.LOGGER.debug(
                    "Skipping controller block {} for definition {} because it is not in this namespace",
                    blockId, id);
            return null;
        }
        return new DynamicControllerSpec(
                id,
                blockId,
                type,
                controller.textures(),
                controller.formedTextures(),
                controller.particle(),
                controller.lightLevel(),
                recipe);
    }

    private static @Nullable MultiblockControllerRecipe parseRecipe(
            Identifier definitionId,
            @Nullable JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonObject()) {
            InfinityNexusMultiblock.LOGGER.warn(
                    "Dynamic controller definition {} has an invalid recipe", definitionId);
            return null;
        }

        JsonObject recipe = element.getAsJsonObject();
        JsonElement patternElement = recipe.get("pattern");
        if (patternElement == null || !patternElement.isJsonArray()) {
            InfinityNexusMultiblock.LOGGER.warn(
                    "Dynamic controller definition {} has a recipe without a pattern array", definitionId);
            return null;
        }

        List<String> pattern = new ArrayList<>();
        for (JsonElement row : patternElement.getAsJsonArray()) {
            if (!row.isJsonPrimitive() || !row.getAsJsonPrimitive().isString()) {
                InfinityNexusMultiblock.LOGGER.warn(
                        "Dynamic controller definition {} has a non-string recipe pattern row", definitionId);
                return null;
            }
            pattern.add(row.getAsString());
        }
        if (pattern.isEmpty() || pattern.size() > 3) {
            InfinityNexusMultiblock.LOGGER.warn(
                    "Dynamic controller definition {} has a recipe pattern with invalid height", definitionId);
            return null;
        }

        int width = pattern.get(0).length();
        if (width < 1 || width > 3) {
            InfinityNexusMultiblock.LOGGER.warn(
                    "Dynamic controller definition {} has a recipe pattern with invalid width", definitionId);
            return null;
        }

        Set<Character> usedSymbols = new LinkedHashSet<>();
        for (String row : pattern) {
            if (row.length() != width) {
                InfinityNexusMultiblock.LOGGER.warn(
                        "Dynamic controller definition {} has uneven recipe pattern rows", definitionId);
                return null;
            }
            for (int index = 0; index < row.length(); index++) {
                char symbol = row.charAt(index);
                if (symbol != ' ') {
                    usedSymbols.add(symbol);
                }
            }
        }
        if (usedSymbols.isEmpty()) {
            InfinityNexusMultiblock.LOGGER.warn(
                    "Dynamic controller definition {} has an empty recipe pattern", definitionId);
            return null;
        }

        JsonElement keyElement = recipe.get("key");
        if (keyElement == null || !keyElement.isJsonObject()) {
            InfinityNexusMultiblock.LOGGER.warn(
                    "Dynamic controller definition {} has a recipe without a key object", definitionId);
            return null;
        }

        Map<Character, String> key = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : keyElement.getAsJsonObject().entrySet()) {
            String symbolText = entry.getKey();
            if (symbolText.length() != 1 || symbolText.charAt(0) == ' ') {
                InfinityNexusMultiblock.LOGGER.warn(
                        "Dynamic controller definition {} has an invalid recipe symbol '{}'",
                        definitionId, symbolText);
                return null;
            }
            JsonElement value = entry.getValue();
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                InfinityNexusMultiblock.LOGGER.warn(
                        "Dynamic controller definition {} has an invalid recipe ingredient for '{}'",
                        definitionId, symbolText);
                return null;
            }
            String ingredient = value.getAsString().trim();
            if (!isValidRecipeIngredient(ingredient)) {
                InfinityNexusMultiblock.LOGGER.warn(
                        "Dynamic controller definition {} has an invalid recipe ingredient '{}'",
                        definitionId, ingredient);
                return null;
            }
            key.put(symbolText.charAt(0), ingredient);
        }

        if (!key.keySet().equals(usedSymbols)) {
            InfinityNexusMultiblock.LOGGER.warn(
                    "Dynamic controller definition {} recipe key does not match the pattern symbols", definitionId);
            return null;
        }

        return new MultiblockControllerRecipe(pattern, key);
    }

    private static boolean isValidRecipeIngredient(String value) {
        String text = value.startsWith("#") ? value.substring(1) : value;
        return !text.isEmpty() && Identifier.tryParse(text) != null;
    }

    private static void collectRecipeType(Identifier definitionId, JsonObject json) {
        JsonElement element = json.get("recipe_type");
        if (element == null) {
            InfinityNexusMultiblock.LOGGER.warn(
                    "Recipe multiblock definition {} has no recipe_type", definitionId);
            return;
        }
        Identifier recipeTypeId = Identifier.CODEC.parse(JsonOps.INSTANCE, element).result().orElse(null);
        if (recipeTypeId == null) {
            InfinityNexusMultiblock.LOGGER.warn(
                    "Multiblock definition {} has an invalid recipe_type", definitionId);
            return;
        }
        RECIPE_TYPES.add(recipeTypeId);
    }

    private static @Nullable Identifier definitionId(String relativePath) {
        if (!relativePath.startsWith("data/") || !relativePath.endsWith(".json")) {
            return null;
        }
        String path = relativePath.substring("data/".length());
        int separator = path.indexOf('/');
        if (separator <= 0) {
            return null;
        }
        String namespace = path.substring(0, separator);
        String remainder = path.substring(separator + 1);
        if (!remainder.startsWith("multiblock/")) {
            return null;
        }
        String definitionPath = remainder.substring("multiblock/".length());
        definitionPath = definitionPath.substring(0, definitionPath.length() - ".json".length());
        if (definitionPath.isEmpty()
                || !Identifier.isValidNamespace(namespace)
                || !Identifier.isValidPath(definitionPath)) {
            InfinityNexusMultiblock.LOGGER.warn("Skipping invalid multiblock definition path {}", relativePath);
            return null;
        }
        return Identifier.fromNamespaceAndPath(namespace, definitionPath);
    }

    private static Identifier texture(String path) {
        return Identifier.fromNamespaceAndPath(
                InfinityNexusMultiblock.MOD_ID, "block/" + path);
    }
}
