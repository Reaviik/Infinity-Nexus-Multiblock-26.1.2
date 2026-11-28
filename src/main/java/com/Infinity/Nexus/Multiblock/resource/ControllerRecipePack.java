package com.Infinity.Nexus.Multiblock.resource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import com.Infinity.Nexus.Multiblock.InfinityNexusMultiblock;
import com.Infinity.Nexus.Multiblock.api.MultiblockControllerRecipe;
import com.Infinity.Nexus.Multiblock.api.MultiblockDefinition;
import com.Infinity.Nexus.Multiblock.data.MultiblockDefinitionFiles;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.util.InclusiveRange;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforgespi.language.IModFileInfo;
import org.jspecify.annotations.Nullable;

public final class ControllerRecipePack {
    private static final String PACK_ID = InfinityNexusMultiblock.MOD_ID + ":controller_recipes";
    private static final String PACK_NAME = "Infinity Nexus Multiblock Controller Recipes";
    private static final String RECIPE_DIRECTORY = "recipe";
    private static final String CONTROLLER_ITEM_ID = InfinityNexusMultiblock.MOD_ID + ":multiblock_controller";
    private static final String DEFINITION_COMPONENT_ID =
            InfinityNexusMultiblock.MOD_ID + ":multiblock_definition";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ControllerRecipePack() {}

    public static void addTo(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) {
            return;
        }

        PackLocationInfo location = new PackLocationInfo(
                PACK_ID,
                Component.literal(PACK_NAME),
                PackSource.BUILT_IN,
                Optional.empty());
        Pack.ResourcesSupplier supplier = new Pack.ResourcesSupplier() {
            @Override
            public PackResources openPrimary(PackLocationInfo location) {
                return new RecipeResources(location);
            }

            @Override
            public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
                return new RecipeResources(location);
            }
        };
        Pack pack = Pack.readMetaAndCreate(
                location,
                supplier,
                PackType.SERVER_DATA,
                new PackSelectionConfig(true, Pack.Position.TOP, false));
        if (pack == null) {
            InfinityNexusMultiblock.LOGGER.error("Failed to create the controller recipe data pack");
            return;
        }
        event.addRepositorySource(source -> source.accept(pack.hidden()));
    }

    private static Map<Identifier, byte[]> buildRecipes() {
        Map<Identifier, JsonObject> files = new LinkedHashMap<>();
        readModDefinitions(files);
        readKubeJsDefinitions(files);

        Map<Identifier, JsonObject> resolved = new LinkedHashMap<>();
        Map<Identifier, byte[]> recipes = new LinkedHashMap<>();
        files.keySet().stream().sorted().forEach(id -> {
            try {
                JsonObject original = files.get(id);
                if (MultiblockDefinitionFiles.isTemplate(original)) {
                    return;
                }
                JsonObject json = MultiblockDefinitionFiles.resolve(id, files, resolved, new HashSet<>());
                addRecipe(recipes, id, json);
            } catch (RuntimeException exception) {
                InfinityNexusMultiblock.LOGGER.error(
                        "Failed to generate a controller recipe for {}: {}", id, exception.getMessage());
            }
        });
        logGenerated(recipes);
        return Map.copyOf(recipes);
    }

    public static Map<Identifier, byte[]> generateRecipes(Map<Identifier, JsonObject> resolvedDefinitions) {
        Map<Identifier, byte[]> recipes = new LinkedHashMap<>();
        resolvedDefinitions.keySet().stream().sorted()
                .forEach(id -> addRecipe(recipes, id, resolvedDefinitions.get(id)));
        logGenerated(recipes);
        return Map.copyOf(recipes);
    }

    private static void logGenerated(Map<Identifier, byte[]> recipes) {
        if (!recipes.isEmpty()) {
            InfinityNexusMultiblock.LOGGER.debug("Generated {} controller recipes", recipes.size());
        }
    }

    private static void addRecipe(Map<Identifier, byte[]> recipes, Identifier id, JsonObject json) {
        try {
            if (!json.has("pattern")) {
                return;
            }
            if (!isCurrentSchema(json)) {
                if (json.has("recipe")) {
                    InfinityNexusMultiblock.LOGGER.warn(
                            "Skipping controller recipe for {}: unsupported schema_version", id);
                }
                return;
            }
            Optional<MultiblockControllerRecipe> recipe = MultiblockControllerRecipe.parse(
                    json.get("recipe"),
                    message -> InfinityNexusMultiblock.LOGGER.warn(
                            "Invalid controller recipe in {}: {}", id, message));
            if (recipe.isEmpty()) {
                return;
            }

            Identifier recipeResourceId = Identifier.fromNamespaceAndPath(
                    id.getNamespace(), RECIPE_DIRECTORY + "/controller/" + id.getPath() + ".json");
            recipes.put(recipeResourceId, GSON.toJson(createRecipeJson(id, recipe.get()))
                    .getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException exception) {
            InfinityNexusMultiblock.LOGGER.error(
                    "Failed to generate a controller recipe for {}: {}", id, exception.getMessage());
        }
    }

    private static JsonObject createRecipeJson(Identifier definitionId, MultiblockControllerRecipe recipe) {
        JsonObject root = new JsonObject();
        root.addProperty("type", "minecraft:crafting_shaped");
        root.addProperty("category", "misc");

        JsonArray pattern = new JsonArray();
        recipe.pattern().forEach(pattern::add);
        root.add("pattern", pattern);

        JsonObject key = new JsonObject();
        recipe.key().forEach((symbol, ingredient) -> key.addProperty(String.valueOf(symbol), ingredient));
        root.add("key", key);

        JsonObject components = new JsonObject();
        components.addProperty(DEFINITION_COMPONENT_ID, definitionId.toString());

        JsonObject result = new JsonObject();
        result.addProperty("id", CONTROLLER_ITEM_ID);
        result.addProperty("count", 1);
        result.add("components", components);
        root.add("result", result);
        return root;
    }

    private static boolean isCurrentSchema(JsonObject json) {
        JsonElement schema = json.get("schema_version");
        return schema != null
                && schema.isJsonPrimitive()
                && schema.getAsJsonPrimitive().isNumber()
                && schema.getAsInt() == MultiblockDefinition.CURRENT_SCHEMA_VERSION;
    }

    private static void readModDefinitions(Map<Identifier, JsonObject> files) {
        IModFileInfo modFileInfo = ModList.get().getModFileById(InfinityNexusMultiblock.MOD_ID);
        if (modFileInfo == null) {
            return;
        }
        modFileInfo.getFile().getContents().visitContent("data", (relativePath, resource) -> {
            Identifier id = definitionId(relativePath);
            if (id == null) {
                return;
            }
            try (Reader reader = resource.bufferedReader()) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (parsed.isJsonObject()) {
                    files.put(id, parsed.getAsJsonObject());
                } else {
                    InfinityNexusMultiblock.LOGGER.warn("Multiblock definition {} is not a JSON object", id);
                }
            } catch (IOException | RuntimeException exception) {
                InfinityNexusMultiblock.LOGGER.error(
                        "Failed to read multiblock definition {}: {}", id, exception.getMessage());
            }
        });
    }

    private static void readKubeJsDefinitions(Map<Identifier, JsonObject> files) {
        Path root = FMLPaths.GAMEDIR.get().resolve("kubejs").resolve("data");
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> readKubeJsDefinition(root, path, files));
        } catch (IOException | RuntimeException exception) {
            InfinityNexusMultiblock.LOGGER.warn(
                    "Failed to scan KubeJS multiblock definitions: {}", exception.getMessage());
        }
    }

    private static void readKubeJsDefinition(Path root, Path path, Map<Identifier, JsonObject> files) {
        try {
            String relativePath = root.relativize(path).toString().replace('\\', '/');
            Identifier id = definitionId("data/" + relativePath);
            if (id == null) {
                return;
            }
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (parsed.isJsonObject()) {
                    files.put(id, parsed.getAsJsonObject());
                } else {
                    InfinityNexusMultiblock.LOGGER.warn("Multiblock definition {} is not a JSON object", id);
                }
            }
        } catch (IOException | RuntimeException exception) {
            InfinityNexusMultiblock.LOGGER.error(
                    "Failed to read KubeJS multiblock definition {}: {}", path, exception.getMessage());
        }
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
            return null;
        }
        return Identifier.fromNamespaceAndPath(namespace, definitionPath);
    }

    private static final class RecipeResources extends AbstractPackResources {
        private final PackMetadataSection metadata;
        private volatile Map<Identifier, byte[]> recipes;

        private RecipeResources(PackLocationInfo location) {
            super(location);
            this.metadata = new PackMetadataSection(
                    Component.literal(PACK_NAME),
                    new InclusiveRange<>(SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA)));
        }

        @Override
        public @Nullable IoSupplier<InputStream> getRootResource(String... path) {
            return null;
        }

        @Override
        public @Nullable IoSupplier<InputStream> getResource(PackType type, Identifier location) {
            if (type != PackType.SERVER_DATA) {
                return null;
            }
            byte[] data = recipes().get(location);
            return data == null ? null : () -> new ByteArrayInputStream(data);
        }

        @Override
        public void listResources(PackType type, String namespace, String directory, ResourceOutput output) {
            if (type != PackType.SERVER_DATA
                    || (!RECIPE_DIRECTORY.equals(directory) && !directory.startsWith(RECIPE_DIRECTORY + "/"))) {
                return;
            }
            String prefix = directory + "/";
            for (Map.Entry<Identifier, byte[]> entry : recipes().entrySet()) {
                Identifier id = entry.getKey();
                if (!id.getNamespace().equals(namespace) || !id.getPath().startsWith(prefix)) {
                    continue;
                }
                output.accept(id, () -> new ByteArrayInputStream(entry.getValue()));
            }
        }

        @Override
        public Set<String> getNamespaces(PackType type) {
            if (type != PackType.SERVER_DATA) {
                return Set.of();
            }
            Set<String> namespaces = new LinkedHashSet<>();
            recipes().keySet().forEach(id -> namespaces.add(id.getNamespace()));
            return namespaces;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> @Nullable T getMetadataSection(MetadataSectionType<T> metadataSerializer) {
            if (metadataSerializer == PackMetadataSection.CLIENT_TYPE
                    || metadataSerializer == PackMetadataSection.SERVER_TYPE
                    || metadataSerializer == PackMetadataSection.FALLBACK_TYPE) {
                return (T) this.metadata;
            }
            return null;
        }

        @Override
        public void close() {
            this.recipes = null;
        }

        private Map<Identifier, byte[]> recipes() {
            Map<Identifier, byte[]> current = this.recipes;
            if (current != null) {
                return current;
            }
            synchronized (this) {
                if (this.recipes == null) {
                    this.recipes = buildRecipes();
                }
                return this.recipes;
            }
        }
    }
}
