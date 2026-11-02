package com.Infinity.Nexus.Multiblock.api;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record MultiblockControllerRecipe(List<String> pattern, Map<Character, String> key) {
    public MultiblockControllerRecipe {
        pattern = List.copyOf(Objects.requireNonNull(pattern, "pattern"));
        key = Map.copyOf(Objects.requireNonNull(key, "key"));
    }
}
