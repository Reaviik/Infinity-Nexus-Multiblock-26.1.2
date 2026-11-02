package com.Infinity.Nexus.Multiblock.resource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.Infinity.Nexus.Multiblock.InfinityNexusMultiblock;
import com.Infinity.Nexus.Multiblock.api.MultiblockControllerConfig;
import com.Infinity.Nexus.Multiblock.api.MultiblockControllerRecipe;
import com.Infinity.Nexus.Multiblock.registry.DynamicControllerRegistry;
import com.Infinity.Nexus.Multiblock.registry.DynamicControllerSpec;
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
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforgespi.language.IModFileInfo;
import org.jspecify.annotations.Nullable;

public final class DynamicControllerResourcePack {
    private static final String PACK_ID = InfinityNexusMultiblock.MOD_ID + ":dynamic_controllers";
    private static final String PACK_NAME = "Infinity Nexus Multiblock Dynamic Controllers";
    private static final String DATA_PACK_ID = InfinityNexusMultiblock.MOD_ID + ":dynamic_controller_recipes";
    private static final String DATA_PACK_NAME = "Infinity Nexus Multiblock Dynamic Controller Recipes";
    private static final List<String> LANGUAGES = List.of("en_us", "pt_br");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private DynamicControllerResourcePack() {}

    public static void addTo(AddPackFindersEvent event) {
        List<DynamicControllerSpec> specs = DynamicControllerRegistry.specs();
        if (specs.isEmpty()) {
            return;
        }

        if (event.getPackType() == PackType.CLIENT_RESOURCES) {
            addPack(
                    event,
                    PackType.CLIENT_RESOURCES,
                    PACK_ID,
                    PACK_NAME,
                    buildResources(specs));
        } else if (event.getPackType() == PackType.SERVER_DATA) {
            addPack(
                    event,
                    PackType.SERVER_DATA,
                    DATA_PACK_ID,
                    DATA_PACK_NAME,
                    buildRecipeResources(specs));
        }
    }

    private static void addPack(
            AddPackFindersEvent event,
            PackType packType,
            String packId,
            String packName,
            Map<String, byte[]> resources) {
        if (resources.isEmpty()) {
            return;
        }

        PackLocationInfo location = new PackLocationInfo(
                packId,
                Component.literal(packName),
                PackSource.BUILT_IN,
                Optional.empty());
        Pack.ResourcesSupplier supplier = new Pack.ResourcesSupplier() {
            @Override
            public PackResources openPrimary(PackLocationInfo location) {
                return new GeneratedPackResources(location, packType, packName, resources);
            }

            @Override
            public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
                return new GeneratedPackResources(location, packType, packName, resources);
            }
        };
        Pack pack = Pack.readMetaAndCreate(
                location,
                supplier,
                packType,
                new PackSelectionConfig(true, Pack.Position.TOP, false));
        if (pack == null) {
            InfinityNexusMultiblock.LOGGER.error(
                    "Failed to create the dynamic controller {} pack", packType.getDirectory());
            return;
        }
        Pack hiddenPack = pack.hidden();
        event.addRepositorySource(source -> source.accept(hiddenPack));
    }

    private static Map<String, byte[]> buildResources(List<DynamicControllerSpec> specs) {
        Map<String, byte[]> resources = new LinkedHashMap<>();
        Map<String, JsonObject> languages = new LinkedHashMap<>();
        for (String locale : LANGUAGES) {
            languages.put(locale, loadBaseLanguage(locale));
        }

        for (DynamicControllerSpec spec : specs) {
            Identifier blockId = spec.blockId();
            String namespace = blockId.getNamespace();
            String path = blockId.getPath();
            String model = namespace + ":block/" + path;
            String modelOn = model + "_on";

            String formedModel = null;
            String formedModelOn = null;
            MultiblockControllerConfig.FormedTextures formedTextures = spec.formedTextures();
            if (formedTextures != null && !formedTextures.isEmpty()) {
                formedModel = model + "_formed";
                formedModelOn = formedModel + "_on";
                putJson(resources, "assets/" + namespace + "/models/block/" + path + "_formed.json",
                        blockModel(spec.textures(), formedTextures, spec.particle(), "_off"));
                putJson(resources, "assets/" + namespace + "/models/block/" + path + "_formed_on.json",
                        blockModel(spec.textures(), formedTextures, spec.particle(), "_on"));
            }

            putJson(resources, "assets/" + namespace + "/blockstates/" + path + ".json",
                    blockState(model, modelOn, formedModel, formedModelOn));
            putJson(resources, "assets/" + namespace + "/models/block/" + path + ".json",
                    blockModel(spec.textures(), null, spec.particle(), "_off"));
            putJson(resources, "assets/" + namespace + "/models/block/" + path + "_on.json",
                    blockModel(spec.textures(), null, spec.particle(), "_on"));
            putJson(resources, "assets/" + namespace + "/models/item/" + path + ".json", itemModel(model));
            putJson(resources, "assets/" + namespace + "/items/" + path + ".json", itemDefinition(namespace, path));

            String displayName = displayName(blockId);
            for (JsonObject language : languages.values()) {
                putIfAbsent(language, "block." + namespace + "." + path, displayName);
                putIfAbsent(language, "gui." + namespace + "." + path, displayName);
            }

            Identifier definitionId = spec.definitionId();
            if (!definitionId.equals(blockId)) {
                String definitionNamespace = definitionId.getNamespace();
                String definitionPath = definitionId.getPath();
                for (JsonObject language : languages.values()) {
                    putIfAbsent(language, "block." + definitionNamespace + "." + definitionPath, displayName);
                    putIfAbsent(language, "gui." + definitionNamespace + "." + definitionPath, displayName);
                }
            }
        }

        languages.forEach((locale, language) ->
                putJson(resources, "assets/" + InfinityNexusMultiblock.MOD_ID + "/lang/" + locale + ".json", language));
        return resources;
    }

    private static Map<String, byte[]> buildRecipeResources(List<DynamicControllerSpec> specs) {
        Map<String, byte[]> resources = new LinkedHashMap<>();
        for (DynamicControllerSpec spec : specs) {
            MultiblockControllerRecipe recipe = spec.recipe();
            if (recipe == null) {
                continue;
            }

            JsonObject root = new JsonObject();
            root.addProperty("type", "minecraft:crafting_shaped");
            root.addProperty("category", "misc");

            JsonArray pattern = new JsonArray();
            recipe.pattern().forEach(pattern::add);
            root.add("pattern", pattern);

            JsonObject key = new JsonObject();
            recipe.key().forEach((symbol, ingredient) ->
                    key.addProperty(String.valueOf(symbol), ingredient));
            root.add("key", key);

            JsonObject result = new JsonObject();
            result.addProperty("id", spec.blockId().toString());
            result.addProperty("count", 1);
            root.add("result", result);

            Identifier blockId = spec.blockId();
            putJson(
                    resources,
                    "data/" + blockId.getNamespace() + "/recipe/controller/" + blockId.getPath() + ".json",
                    root);
        }
        return resources;
    }

    private static JsonObject loadBaseLanguage(String locale) {
        IModFileInfo modFileInfo = ModList.get().getModFileById(InfinityNexusMultiblock.MOD_ID);
        if (modFileInfo == null) {
            return new JsonObject();
        }
        try {
            byte[] data = modFileInfo.getFile().getContents().readFile(
                    "assets/" + InfinityNexusMultiblock.MOD_ID + "/lang/" + locale + ".json");
            if (data == null) {
                return new JsonObject();
            }
            JsonElement parsed = JsonParser.parseString(new String(data, StandardCharsets.UTF_8));
            return parsed.isJsonObject() ? parsed.getAsJsonObject().deepCopy() : new JsonObject();
        } catch (IOException | RuntimeException exception) {
            InfinityNexusMultiblock.LOGGER.warn(
                    "Failed to read the base {} language file: {}", locale, exception.getMessage());
            return new JsonObject();
        }
    }

    private static void putIfAbsent(JsonObject language, String key, String value) {
        if (!language.has(key)) {
            language.addProperty(key, value);
        }
    }

    private static JsonObject blockState(
            String model,
            String modelOn,
            @Nullable String formedModel,
            @Nullable String formedModelOn) {
        JsonObject variants = new JsonObject();
        for (String facing : List.of("north", "east", "south", "west")) {
            int y = switch (facing) {
                case "east" -> 90;
                case "south" -> 180;
                case "west" -> 270;
                default -> 0;
            };
            for (boolean formed : new boolean[] { false, true }) {
                boolean usesFormedModel = formed && formedModel != null;
                for (boolean processing : new boolean[] { false, true }) {
                    String variantModel;
                    if (usesFormedModel) {
                        variantModel = processing && formedModelOn != null ? formedModelOn : formedModel;
                    } else {
                        variantModel = processing ? modelOn : model;
                    }
                    JsonObject variant = new JsonObject();
                    variant.addProperty("model", variantModel);
                    if (y != 0) {
                        variant.addProperty("y", y);
                    }
                    variants.add("facing=" + facing + ",formed=" + formed + ",processing=" + processing, variant);
                }
            }
        }
        JsonObject root = new JsonObject();
        root.add("variants", variants);
        return root;
    }

    private static JsonObject blockModel(
            MultiblockControllerConfig.Textures textures,
            MultiblockControllerConfig.@Nullable FormedTextures formedTextures,
            @Nullable Identifier particle,
            String fallbackSuffix) {
        Identifier formedFront = formedTextures == null ? null : formedTextures.front().orElse(null);
        Identifier formedBack = formedTextures == null ? null : formedTextures.back().orElse(null);
        Identifier formedLeft = formedTextures == null ? null : formedTextures.left().orElse(null);
        Identifier formedRight = formedTextures == null ? null : formedTextures.right().orElse(null);
        Identifier formedTop = formedTextures == null ? null : formedTextures.top().orElse(null);
        Identifier formedBottom = formedTextures == null ? null : formedTextures.bottom().orElse(null);
        Identifier front = resolveFace(textures.front(), formedFront, "front", fallbackSuffix);
        Identifier back = resolveFace(textures.back(), formedBack, "back", fallbackSuffix);
        Identifier left = resolveFace(textures.left(), formedLeft, "left", fallbackSuffix);
        Identifier right = resolveFace(textures.right(), formedRight, "right", fallbackSuffix);
        Identifier top = resolveFace(textures.top(), formedTop, "top", fallbackSuffix);
        Identifier bottom = resolveFace(textures.bottom(), formedBottom, "bottom", fallbackSuffix);
        JsonObject values = new JsonObject();
        Identifier particleTexture = particle != null && textureExists(particle) ? particle : front;
        values.addProperty("particle", particleTexture.toString());
        values.addProperty("north", front.toString());
        values.addProperty("south", back.toString());
        values.addProperty("west", left.toString());
        values.addProperty("east", right.toString());
        values.addProperty("up", top.toString());
        values.addProperty("down", bottom.toString());
        JsonObject root = new JsonObject();
        root.addProperty("parent", "minecraft:block/cube");
        root.add("textures", values);
        return root;
    }

    private static Identifier resolveFace(
            Identifier base,
            @Nullable Identifier formed,
            String face,
            String fallbackSuffix) {
        if (formed != null && textureExists(formed)) {
            return formed;
        }
        return resolveTexture(base, face, fallbackSuffix);
    }

    private static Identifier resolveTexture(Identifier texture, String face, String fallbackSuffix) {
        return textureExists(texture)
                ? texture
                : Identifier.fromNamespaceAndPath(
                        InfinityNexusMultiblock.MOD_ID, "block/fallback/" + face + fallbackSuffix);
    }

    private static boolean textureExists(Identifier texture) {
        String namespace = texture.getNamespace();
        if ("minecraft".equals(namespace)) {
            return true;
        }
        IModFileInfo modFileInfo = ModList.get().getModFileById(namespace);
        if (modFileInfo == null) {
            return false;
        }
        String path = texture.getPath();
        if (!path.endsWith(".png")) {
            path += ".png";
        }
        try {
            return modFileInfo.getFile().getContents()
                    .readFile("assets/" + namespace + "/textures/" + path) != null;
        } catch (IOException exception) {
            return false;
        }
    }

    private static JsonObject itemModel(String model) {
        JsonObject root = new JsonObject();
        root.addProperty("parent", model);
        return root;
    }

    private static JsonObject itemDefinition(String namespace, String path) {
        JsonObject model = new JsonObject();
        model.addProperty("type", "minecraft:model");
        model.addProperty("model", namespace + ":item/" + path);
        JsonObject root = new JsonObject();
        root.add("model", model);
        return root;
    }

    private static String displayName(Identifier id) {
        StringBuilder result = new StringBuilder();
        for (String part : id.getPath().split("[/_]")) {
            if (part.isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                result.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return result.length() == 0 ? id.getPath() : result.toString();
    }

    private static void putJson(Map<String, byte[]> resources, String path, JsonObject json) {
        resources.put(path, GSON.toJson(json).getBytes(StandardCharsets.UTF_8));
    }

    private static String resourcePath(PackType type, Identifier location) {
        return type.getDirectory() + "/" + location.getNamespace() + "/" + location.getPath();
    }

    private static final class GeneratedPackResources extends AbstractPackResources {
        private final Map<String, byte[]> resources;
        private final PackType packType;
        private final PackMetadataSection metadata;

        private GeneratedPackResources(
                PackLocationInfo location,
                PackType packType,
                String packName,
                Map<String, byte[]> resources) {
            super(location);
            this.resources = resources;
            this.packType = packType;
            this.metadata = new PackMetadataSection(
                    Component.literal(packName),
                    new InclusiveRange<>(SharedConstants.getCurrentVersion().packVersion(packType)));
        }

        @Override
        public @Nullable IoSupplier<InputStream> getRootResource(String... path) {
            return null;
        }

        @Override
        public @Nullable IoSupplier<InputStream> getResource(PackType type, Identifier location) {
            byte[] data = this.resources.get(resourcePath(type, location));
            return data == null ? null : () -> new ByteArrayInputStream(data);
        }

        @Override
        public void listResources(PackType type, String namespace, String directory, ResourceOutput output) {
            String root = type.getDirectory() + "/" + namespace + "/";
            String filter = directory.isEmpty() ? root : root + directory + "/";
            for (Map.Entry<String, byte[]> entry : this.resources.entrySet()) {
                String key = entry.getKey();
                if (!key.startsWith(filter)) {
                    continue;
                }
                String path = key.substring(root.length());
                if (path.isEmpty()) {
                    continue;
                }
                Identifier id = Identifier.fromNamespaceAndPath(namespace, path);
                output.accept(id, () -> new ByteArrayInputStream(entry.getValue()));
            }
        }

        @Override
        public Set<String> getNamespaces(PackType type) {
            if (type != this.packType) {
                return Set.of();
            }
            String prefix = type.getDirectory() + "/";
            Set<String> namespaces = new LinkedHashSet<>();
            for (String key : this.resources.keySet()) {
                if (!key.startsWith(prefix)) {
                    continue;
                }
                int start = prefix.length();
                int end = key.indexOf('/', start);
                if (end > start) {
                    namespaces.add(key.substring(start, end));
                }
            }
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
        }
    }
}
