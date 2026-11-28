package com.Infinity.Nexus.Multiblock.api.addon;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

/**
 * Global registry of addon resource types. Base code must only use this registry,
 * never a concrete mod resource class.
 */
public final class MultiblockAddons {
    private static final Map<String, MultiblockResourceType> TYPES = new ConcurrentHashMap<>();
    private static final Map<String, JeiResourceBridge> JEI_BRIDGES = new ConcurrentHashMap<>();

    private MultiblockAddons() {
    }

    public static void registerJeiBridge(String kind, JeiResourceBridge bridge) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(bridge, "bridge");
        JEI_BRIDGES.put(kind, bridge);
    }

    public static @Nullable JeiResourceBridge jeiBridge(String kind) {
        return kind == null ? null : JEI_BRIDGES.get(kind);
    }

    public static void register(MultiblockResourceType type) {
        Objects.requireNonNull(type, "type");
        String id = type.id();
        if (id == null || !id.matches("[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid multiblock resource kind: " + id);
        }
        MultiblockResourceType previous = TYPES.putIfAbsent(id, type);
        if (previous != null && previous != type) {
            throw new IllegalStateException("Duplicate multiblock resource kind: " + id);
        }
    }

    public static @Nullable MultiblockResourceType get(String id) {
        return id == null ? null : TYPES.get(id);
    }

    public static boolean has(String id) {
        return id != null && TYPES.containsKey(id);
    }

    public static Collection<MultiblockResourceType> all() {
        List<MultiblockResourceType> types = new ArrayList<>(TYPES.values());
        types.sort(Comparator.comparing(MultiblockResourceType::id));
        return List.copyOf(types);
    }
}
