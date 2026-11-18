package com.Infinity.Nexus.Multiblock.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

public enum MultiblockType {
    RECIPE("recipe"),
    STORAGE("storage"),
    RECIPE_EXAMPLE("recipe_exemple"),
    STORAGE_EXAMPLE("storage_exemple");

    public static final Codec<MultiblockType> CODEC = Codec.STRING.flatXmap(
            MultiblockType::fromSerializedName,
            type -> DataResult.success(type.serializedName));

    private final String serializedName;

    MultiblockType(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return this.serializedName;
    }

    public boolean isRecipe() {
        return this == RECIPE || this == RECIPE_EXAMPLE;
    }

    public boolean isStorage() {
        return this == STORAGE || this == STORAGE_EXAMPLE;
    }

    public boolean isExample() {
        return this == RECIPE_EXAMPLE || this == STORAGE_EXAMPLE;
    }

    public static boolean isExampleSerializedName(String serializedName) {
        return RECIPE_EXAMPLE.serializedName.equals(serializedName)
                || STORAGE_EXAMPLE.serializedName.equals(serializedName);
    }

    private static DataResult<MultiblockType> fromSerializedName(String serializedName) {
        for (MultiblockType type : values()) {
            if (type.serializedName.equals(serializedName)) {
                return DataResult.success(type);
            }
        }
        return DataResult.error(() -> "Unknown multiblock type: " + serializedName);
    }
}
