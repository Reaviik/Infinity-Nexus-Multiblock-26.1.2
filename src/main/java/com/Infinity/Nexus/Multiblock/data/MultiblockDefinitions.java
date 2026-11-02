package com.Infinity.Nexus.Multiblock.data;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.Infinity.Nexus.Multiblock.api.MultiblockDefinition;
import net.minecraft.resources.Identifier;

public final class MultiblockDefinitions {
    private static volatile Map<Identifier, MultiblockDefinition> definitions = Map.of();
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private MultiblockDefinitions() {}

    public static void register(MultiblockDefinition definition) {
        Map<Identifier, MultiblockDefinition> updated = new LinkedHashMap<>(definitions);
        if (updated.putIfAbsent(definition.id(), definition) != null) {
            throw new IllegalStateException("Duplicate multiblock definition: " + definition.id());
        }
        definitions = Collections.unmodifiableMap(updated);
        notifyListeners();
    }

    public static void replaceAll(Map<Identifier, MultiblockDefinition> loadedDefinitions) {
        definitions = Collections.unmodifiableMap(new LinkedHashMap<>(loadedDefinitions));
        notifyListeners();
    }

    public static void addListener(Runnable listener) {
        LISTENERS.add(listener);
    }

    public static void removeListener(Runnable listener) {
        LISTENERS.remove(listener);
    }

    private static void notifyListeners() {
        for (Runnable listener : LISTENERS) {
            listener.run();
        }
    }

    public static Collection<MultiblockDefinition> all() {
        return definitions.values();
    }

    public static MultiblockDefinition get(Identifier id) {
        return definitions.get(id);
    }

    public static MultiblockDefinition getOrThrow(Identifier id) {
        MultiblockDefinition definition = definitions.get(id);
        if (definition == null) {
            throw new IllegalStateException("Unknown multiblock definition: " + id);
        }
        return definition;
    }
}
