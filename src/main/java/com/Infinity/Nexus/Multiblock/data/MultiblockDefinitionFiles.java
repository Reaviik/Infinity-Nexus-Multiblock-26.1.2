package com.Infinity.Nexus.Multiblock.data;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;

public final class MultiblockDefinitionFiles {
    private MultiblockDefinitionFiles() {}

    public static JsonObject resolve(
            Identifier id,
            Map<Identifier, JsonObject> files,
            Map<Identifier, JsonObject> resolved,
            Set<Identifier> resolving) {
        JsonObject cached = resolved.get(id);
        if (cached != null) {
            return cached;
        }
        JsonObject file = files.get(id);
        if (file == null) {
            throw new IllegalArgumentException("Unknown parent multiblock: " + id);
        }
        if (!resolving.add(id)) {
            throw new IllegalArgumentException("Circular extends involving " + id);
        }
        try {
            JsonObject result = new JsonObject();
            Optional<Identifier> parentId = parentId(file);
            if (parentId.isPresent()) {
                result = resolve(parentId.get(), files, resolved, resolving).deepCopy();
            }
            applyLayer(result, file);
            result.remove("extends");
            result.remove("family");
            result.remove("overrides");
            result.remove("template");
            resolved.put(id, result);
            return result;
        } finally {
            resolving.remove(id);
        }
    }

    public static boolean isTemplate(JsonObject file) {
        JsonElement template = file.get("template");
        if (template == null) {
            return false;
        }
        if (!template.isJsonPrimitive() || !template.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("template must be a boolean");
        }
        return template.getAsBoolean();
    }

    public static Optional<Identifier> parentId(JsonObject file) {
        JsonElement extendsElement = file.get("extends");
        JsonElement familyElement = file.get("family");
        if (extendsElement != null && familyElement != null) {
            throw new IllegalArgumentException("Use either extends or family, not both");
        }
        JsonElement parent = extendsElement != null ? extendsElement : familyElement;
        if (parent == null) {
            return Optional.empty();
        }
        return Optional.of(Identifier.CODEC.parse(JsonOps.INSTANCE, parent)
                .getOrThrow(message -> new IllegalArgumentException("Invalid parent reference: " + message)));
    }

    private static void applyLayer(JsonObject target, JsonObject layer) {
        for (Map.Entry<String, JsonElement> entry : layer.entrySet()) {
            if (isInheritanceKey(entry.getKey())) {
                continue;
            }
            merge(target, entry.getKey(), entry.getValue());
        }
        JsonElement overrides = layer.get("overrides");
        if (overrides == null) {
            return;
        }
        if (!overrides.isJsonObject()) {
            throw new IllegalArgumentException("overrides must be an object");
        }
        for (Map.Entry<String, JsonElement> entry : overrides.getAsJsonObject().entrySet()) {
            if (isInheritanceKey(entry.getKey())) {
                throw new IllegalArgumentException("overrides cannot define " + entry.getKey());
            }
            merge(target, entry.getKey(), entry.getValue());
        }
    }

    private static boolean isInheritanceKey(String key) {
        return key.equals("extends") || key.equals("family") || key.equals("overrides") || key.equals("template");
    }

    private static void merge(JsonObject target, String key, JsonElement value) {
        JsonElement existing = target.get(key);
        if (existing != null && existing.isJsonObject() && value.isJsonObject()) {
            JsonObject merged = existing.getAsJsonObject().deepCopy();
            for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
                merge(merged, entry.getKey(), entry.getValue());
            }
            target.add(key, merged);
            return;
        }
        target.add(key, value.deepCopy());
    }
}
