package com.Infinity.Nexus.Multiblock.api.addon.client;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

public final class MultiblockClientAddons {
    private static final Map<String, ResourceListRenderer> RESOURCE_LIST_RENDERERS = new ConcurrentHashMap<>();

    private MultiblockClientAddons() {
    }

    public static void registerResourceListRenderer(String kind, ResourceListRenderer renderer) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(renderer, "renderer");
        RESOURCE_LIST_RENDERERS.put(kind, renderer);
    }

    public static @Nullable ResourceListRenderer resourceListRenderer(String kind) {
        return kind == null ? null : RESOURCE_LIST_RENDERERS.get(kind);
    }
}
